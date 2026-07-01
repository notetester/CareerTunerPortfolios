"""Build copy/paste judge packs for top-tier LLM RAG hard-case review.

This script does not call any model or external API. It only combines existing
synthetic fixture, A/B run, offline evaluator, and Qwen judge artifacts into
machine-readable JSONL packets plus Markdown prompts that can be pasted into
GPT/Claude/Gemini-class judges by a human reviewer.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from validate_rag_hardcase_fixture import load_jsonl, validate_rows  # noqa: E402

REPO_ROOT = SCRIPT_DIR.parents[2].resolve()
ML_ROOT = SCRIPT_DIR.parent.resolve()
GENERATED_ROOT = (ML_ROOT / "reports" / "generated").resolve()
CAREERTUNER_AI_ROOT = Path("D:/dev/CareerTunerAI").resolve()

VARIANTS = ("A_lora_only", "B_structured_evidence_buckets")
MODEL_PROMPTS = {
    "gpt4o": {
        "judge_id": "gpt-4o",
        "file": "gpt4o_judge_prompt.md",
        "subset_file": "gpt4o_disagreement13_judge_prompt.md",
    },
    "claude": {
        "judge_id": "claude-3.5-sonnet",
        "file": "claude_judge_prompt.md",
        "subset_file": "claude_disagreement13_judge_prompt.md",
    },
    "gemini": {
        "judge_id": "gemini-1.5-pro",
        "file": "gemini_judge_prompt.md",
        "subset_file": "gemini_disagreement13_judge_prompt.md",
    },
}

PRIMARY_LABELS = [
    "POSITIVE_UNSUPPORTED_OWNERSHIP",
    "IMPLIED_UNSUPPORTED_OWNERSHIP",
    "RISK_WARNING_ONLY",
    "JOB_REQUIREMENT_ONLY",
    "COMPANY_CONTEXT_ONLY",
    "CATALOG_FACT_ONLY",
    "MISSING_SKILL_STATEMENT",
    "NEGATED_OWNERSHIP_STATEMENT",
    "SAFE_SUPPORTED_OWNERSHIP",
    "SAFE_GENERIC_ADVICE",
    "AMBIGUOUS_ATTRIBUTION",
    "CONTRADICTORY_OUTPUT",
    "FORMAT_OR_PARSE_PROBLEM",
    "UNCLEAR",
]
SEVERITIES = ["PASS", "MINOR_RISK", "REVIEW_REQUIRED", "REJECT", "NOT_JUDGEABLE"]
COMPARISONS = ["B_BETTER", "B_WORSE", "UNCHANGED_SAFE", "UNCHANGED_UNSAFE", "MIXED", "NOT_COMPARABLE"]
RISK_CHANGES = ["DECREASED", "INCREASED", "UNCHANGED", "MIXED", "NOT_COMPARABLE"]
RECOMMENDATIONS = ["KEEP_RAG_DISABLED", "LIMITED_REEVALUATION", "ALLOW_SCOPED_RAG_EXPERIMENT"]

EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_RE = re.compile(r"01[016789][- ]?\d{3,4}[- ]?\d{4}")
RRN_RE = re.compile(r"\b\d{6}[- ]?\d{7}\b")
API_KEY_RE = re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b")
PII_PATTERNS = {
    "email": EMAIL_RE,
    "phone": PHONE_RE,
    "resident-id": RRN_RE,
    "api-key-like": API_KEY_RE,
}


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.rstrip() + "\n", encoding="utf-8")


def assert_output_path_allowed(out_dir: Path) -> None:
    resolved = out_dir.resolve()
    if resolved.is_relative_to(REPO_ROOT) and not resolved.is_relative_to(GENERATED_ROOT):
        raise SystemExit(
            "Top LLM judge packs must not be written into the CareerTuner main repo "
            f"outside reports/generated/: {resolved}"
        )


def output_policy(out_dir: Path) -> str:
    resolved = out_dir.resolve()
    if resolved.is_relative_to(CAREERTUNER_AI_ROOT):
        return "careertuner-ai-artifact"
    if resolved.is_relative_to(GENERATED_ROOT):
        return "career-tuner-generated-ignore"
    return "external-local-output"


def truncate(text: str, limit: int) -> tuple[str, bool]:
    if len(text) <= limit:
        return text, False
    return text[:limit] + "\n...[truncated]", True


def pii_labels(value: Any) -> list[str]:
    blob = json.dumps(value, ensure_ascii=False) if not isinstance(value, str) else value
    return [label for label, pattern in PII_PATTERNS.items() if pattern.search(blob)]


def try_parse_json(raw_text: str) -> Any | None:
    if not raw_text.strip():
        return None
    try:
        return json.loads(raw_text)
    except json.JSONDecodeError:
        return None


def extract_strings(value: Any) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        out: list[str] = []
        for item in value.values():
            out.extend(extract_strings(item))
        return out
    if isinstance(value, list):
        out = []
        for item in value:
            out.extend(extract_strings(item))
        return out
    return []


def text_for_semantic_judgment(parsed_raw: Any) -> str:
    if not isinstance(parsed_raw, dict):
        return ""
    candidates: list[str] = []
    raw_output = parsed_raw.get("rawOutput")
    if isinstance(raw_output, str):
        candidates.append(raw_output)
    parsed_output = parsed_raw.get("parsedOutput")
    if parsed_output is not None:
        candidates.extend(extract_strings(parsed_output))
    gate = parsed_raw.get("gateResult")
    if isinstance(gate, dict) and isinstance(gate.get("reasons"), list):
        candidates.extend(extract_strings(gate["reasons"]))
    return "\n".join(item.strip() for item in candidates if item and item.strip())


def raw_output_path(run_dir: Path, result: dict[str, Any]) -> Path:
    raw_path = result.get("rawOutputPath")
    if isinstance(raw_path, str) and raw_path.strip():
        return run_dir / Path(raw_path)
    return run_dir / "outputs" / f"{result['caseId']}_{result['variant']}.raw.txt"


def load_results_by_key(run_dir: Path) -> dict[tuple[str, str], dict[str, Any]]:
    return {
        (str(row.get("caseId")), str(row.get("variant"))): row
        for row in (load_json(path) for path in sorted((run_dir / "results").glob("*.result.json")))
    }


def load_evaluations_by_key(evaluation_dir: Path) -> dict[tuple[str, str], dict[str, Any]]:
    return {
        (str(row.get("caseId")), str(row.get("variant"))): row
        for row in (load_json(path) for path in sorted((evaluation_dir / "case_evaluations").glob("*.eval.json")))
    }


def load_qwen_results_by_key(qwen_dir: Path) -> dict[tuple[str, str], dict[str, Any]]:
    return {
        (str(row.get("caseId")), str(row.get("variant"))): row
        for row in (load_json(path) for path in sorted((qwen_dir / "results").glob("*.judge.result.json")))
    }


def slim_offline(evaluation: dict[str, Any]) -> dict[str, Any]:
    return {
        "combinedUnsafeClaimCount": evaluation.get("combinedUnsafeClaimCount"),
        "r3LikeGateStatus": evaluation.get("r3LikeGateStatus"),
        "modelReportedUnsupportedPossessionClaimCount": evaluation.get(
            "modelReportedUnsupportedPossessionClaimCount"
        ),
        "deterministicUnsafeClaimCount": evaluation.get("deterministicUnsafeClaimCount"),
        "detectedUnsafeClaims": evaluation.get("detectedUnsafeClaims") or [],
        "jsonParseSuccess": evaluation.get("jsonParseSuccess"),
    }


def slim_qwen(qwen_result: dict[str, Any] | None) -> dict[str, Any]:
    if not qwen_result:
        return {}
    return {
        "label": qwen_result.get("label"),
        "unsupportedClaims": qwen_result.get("unsupportedClaims") or [],
        "confidence": qwen_result.get("confidence"),
        "reason": qwen_result.get("reason"),
        "error": qwen_result.get("error"),
    }


def source_result(result: dict[str, Any]) -> dict[str, Any]:
    parsed = result.get("parsedOutput") if isinstance(result.get("parsedOutput"), dict) else {}
    parsed_metrics = parsed.get("metrics") if isinstance(parsed.get("metrics"), dict) else {}
    metrics = result.get("metrics") if isinstance(result.get("metrics"), dict) else {}
    return {
        "provider": result.get("provider"),
        "model": result.get("model"),
        "rawOutputPath": result.get("rawOutputPath"),
        "latencyMs": result.get("latencyMs"),
        "error": result.get("error"),
        "contractSuccess": parsed_metrics.get("contract_success"),
        "jsonParseSuccess": metrics.get("json_parse_success"),
        "outputLength": metrics.get("output_length"),
    }


def variant_payload(row: dict[str, Any],
                    variant: str,
                    result: dict[str, Any],
                    evaluation: dict[str, Any],
                    qwen_result: dict[str, Any] | None,
                    run_dir: Path,
                    excerpt_chars: int,
                    include_full_output: bool) -> dict[str, Any]:
    raw_path = raw_output_path(run_dir, result)
    raw_text = raw_path.read_text(encoding="utf-8") if raw_path.exists() else ""
    parsed_raw = try_parse_json(raw_text)
    excerpt, truncated = (raw_text, False) if include_full_output else truncate(raw_text, excerpt_chars)
    return {
        "variant": variant,
        "rawOutputPath": str(raw_path.relative_to(run_dir)) if raw_path.exists() else str(raw_path),
        "rawOutputLength": len(raw_text),
        "rawOutputExcerpt": excerpt,
        "rawOutputTruncated": truncated,
        "textForSemanticJudgment": text_for_semantic_judgment(parsed_raw),
        "sourceResult": source_result(result),
        "offlineEvaluator": slim_offline(evaluation),
        "qwenJudge": slim_qwen(qwen_result),
    }


def build_case_bundle(row: dict[str, Any],
                      results: dict[tuple[str, str], dict[str, Any]],
                      evaluations: dict[tuple[str, str], dict[str, Any]],
                      qwen_results: dict[tuple[str, str], dict[str, Any]],
                      run_dir: Path,
                      excerpt_chars: int,
                      include_full_output: bool) -> dict[str, Any]:
    variants: dict[str, dict[str, Any]] = {}
    for variant in VARIANTS:
        key = (row["caseId"], variant)
        variants[variant] = variant_payload(
            row=row,
            variant=variant,
            result=results[key],
            evaluation=evaluations[key],
            qwen_result=qwen_results.get(key),
            run_dir=run_dir,
            excerpt_chars=excerpt_chars,
            include_full_output=include_full_output,
        )
    expected = row.get("expected") if isinstance(row.get("expected"), dict) else {}
    profile = row.get("profile") if isinstance(row.get("profile"), dict) else {}
    job = row.get("job") if isinstance(row.get("job"), dict) else {}
    return {
        "caseId": row["caseId"],
        "category": row["category"],
        "intent": row.get("intent"),
        "expected": {
            "mustNotClaimOwned": expected.get("mustNotClaimOwned") or [],
            "allowedOwned": expected.get("allowedOwned") or [],
            "knownMissingSkills": expected.get("knownMissingSkills") or [],
            "expectedGateStatusForUnsafeClaim": expected.get("expectedGateStatusForUnsafeClaim"),
        },
        "profile": {
            "skills": profile.get("skills") or [],
            "certificates": profile.get("certificates") or [],
            "experienceSummary": profile.get("experienceSummary"),
        },
        "job": {
            "requiredSkills": job.get("requiredSkills") or [],
            "preferredSkills": job.get("preferredSkills") or [],
            "description": job.get("description"),
        },
        "evidenceBuckets": row.get("evidenceBuckets") or {},
        "variants": variants,
    }


def packet_for_variant(case_bundle: dict[str, Any],
                       variant: str,
                       disagreement_keys: set[tuple[str, str]]) -> dict[str, Any]:
    sibling = "B_structured_evidence_buckets" if variant == "A_lora_only" else "A_lora_only"
    case_id = case_bundle["caseId"]
    current = case_bundle["variants"][variant]
    sibling_payload = case_bundle["variants"][sibling]
    return {
        "schemaVersion": "rag-hardcase-top-llm-judge-packet/v1",
        "caseId": case_id,
        "category": case_bundle["category"],
        "intent": case_bundle.get("intent"),
        "variant": variant,
        "pairKey": case_id,
        "subsetFlags": {
            "isOfflineQwenDisagreement": (case_id, variant) in disagreement_keys,
        },
        "expectedMustNotClaimOwned": case_bundle["expected"]["mustNotClaimOwned"],
        "allowedOwned": case_bundle["expected"]["allowedOwned"],
        "knownMissingSkills": case_bundle["expected"]["knownMissingSkills"],
        "profileSkills": case_bundle["profile"]["skills"],
        "profileCertificates": case_bundle["profile"]["certificates"],
        "profileExperienceSummary": case_bundle["profile"]["experienceSummary"],
        "jobRequiredSkills": case_bundle["job"]["requiredSkills"],
        "jobPreferredSkills": case_bundle["job"]["preferredSkills"],
        "jobDescription": case_bundle["job"]["description"],
        "evidenceBuckets": case_bundle["evidenceBuckets"],
        "outputUnderReview": {
            "variant": variant,
            "rawOutputPath": current["rawOutputPath"],
            "rawOutputLength": current["rawOutputLength"],
            "rawOutputExcerpt": current["rawOutputExcerpt"],
            "rawOutputTruncated": current["rawOutputTruncated"],
            "textForSemanticJudgment": current["textForSemanticJudgment"],
        },
        "priorSignalsForReferenceOnly": {
            "offlineEvaluator": current["offlineEvaluator"],
            "qwenJudge": current["qwenJudge"],
            "warning": "Do not copy these prior labels. Judge from outputUnderReview first.",
        },
        "pairContext": {
            "siblingVariant": sibling,
            "aVariant": case_bundle["variants"]["A_lora_only"],
            "bVariant": case_bundle["variants"]["B_structured_evidence_buckets"],
        },
        "judgeTask": {
            "primaryLabels": PRIMARY_LABELS,
            "severities": SEVERITIES,
            "comparisonLabels": COMPARISONS,
            "riskChangeLabels": RISK_CHANGES,
            "recommendations": RECOMMENDATIONS,
        },
    }


def disagreement_keys_from_summary(summary: dict[str, Any]) -> set[tuple[str, str]]:
    keys: set[tuple[str, str]] = set()
    for item in summary.get("judgeVsOfflineDisagreementCases") or []:
        if isinstance(item, dict):
            keys.add((str(item.get("caseId")), str(item.get("variant"))))
    return keys


def expected_output_schema() -> dict[str, Any]:
    return {
        "judgeMeta": {
            "judgeId": "gpt-4o|claude-3.5-sonnet|gemini-1.5-pro",
            "packetSchemaVersion": "rag-hardcase-top-llm-judge-packet/v1",
            "packetSubset": "all|disagreement13",
            "judgedAt": "ISO-8601 timestamp if available",
        },
        "itemJudgments": [
            {
                "caseId": "RAG-HC-001",
                "category": "java_vs_javascript",
                "variant": "A_lora_only",
                "primaryLabel": "POSITIVE_UNSUPPORTED_OWNERSHIP",
                "secondaryLabels": [],
                "severity": "REJECT",
                "confidence": 0.95,
                "unsupportedPossession": True,
                "unsupportedClaims": [
                    {
                        "claim": "Java",
                        "claimType": "skill|certificate|experience|other",
                        "sourceOfTruth": "expectedMustNotClaimOwned",
                        "evidenceExcerpt": "short exact excerpt",
                        "whyUnsupported": "short reason",
                        "ownershipStrength": "EXPLICIT|IMPLIED|AMBIGUOUS",
                        "polarity": "POSITIVE|NEGATED|MISSING|WARNING_ONLY",
                    }
                ],
                "safeMentions": [
                    {
                        "claim": "Java",
                        "mentionType": "JOB_REQUIREMENT_ONLY",
                        "evidenceExcerpt": "short exact excerpt",
                        "whySafe": "short reason",
                    }
                ],
                "sourceAttribution": {
                    "userEvidenceUsedCorrectly": True,
                    "jobRequirementMisattributedToUser": False,
                    "companyContextMisattributedToUser": False,
                    "catalogFactMisattributedToUser": False,
                    "missingSkillMisreportedAsMatched": False,
                },
                "semanticChecks": {
                    "distinguishesJavaAndJavaScript": True,
                    "distinguishesReactAndReactNative": True,
                    "distinguishesSpringAndSpringBoot": True,
                    "distinguishesSqlFamily": True,
                    "distinguishesNextjsAndJavascript": True,
                    "distinguishesCertificateRequirementAndOwnership": True,
                },
                "outputQuality": {
                    "formatValid": True,
                    "internallyConsistent": True,
                    "overClaimsUserProfile": False,
                    "usesCautiousLanguage": True,
                    "mentionsNeedForVerification": True,
                    "actionableButSafe": True,
                },
                "riskFactors": {
                    "positiveOwnershipVerbFound": True,
                    "ownershipVerb": "경험",
                    "negationPresent": False,
                    "missingSkillLanguagePresent": False,
                    "requirementOnlyLanguagePresent": False,
                    "warningOnlyLanguagePresent": False,
                },
                "reason": "1~3 sentence reason",
            }
        ],
        "pairJudgments": [
            {
                "caseId": "RAG-HC-001",
                "category": "java_vs_javascript",
                "aVariant": "A_lora_only",
                "bVariant": "B_structured_evidence_buckets",
                "aSeverity": "REJECT",
                "bSeverity": "PASS",
                "comparison": "B_BETTER",
                "aUnsupportedClaimCount": 1,
                "bUnsupportedClaimCount": 0,
                "bChangedRisk": "DECREASED",
                "bChangedClarity": "IMPROVED|DEGRADED|UNCHANGED|MIXED|NOT_COMPARABLE",
                "bChangedGrounding": "IMPROVED|DEGRADED|UNCHANGED|MIXED|NOT_COMPARABLE",
                "comparisonReason": "short reason",
                "recommendedRegressionSet": False,
            }
        ],
        "summary": {
            "resultCount": 24,
            "variantCount": 2,
            "labelCountByVariant": {"A_lora_only": {}, "B_structured_evidence_buckets": {}},
            "severityCountByVariant": {"A_lora_only": {}, "B_structured_evidence_buckets": {}},
            "unsupportedPossessionCountByVariant": {"A_lora_only": 0, "B_structured_evidence_buckets": 0},
            "ambiguousAttributionCountByVariant": {"A_lora_only": 0, "B_structured_evidence_buckets": 0},
            "riskWarningOnlyCountByVariant": {"A_lora_only": 0, "B_structured_evidence_buckets": 0},
            "pairComparisonCounts": {
                "B_BETTER": 0,
                "B_WORSE": 0,
                "UNCHANGED_SAFE": 0,
                "UNCHANGED_UNSAFE": 0,
                "MIXED": 0,
                "NOT_COMPARABLE": 0,
            },
            "worstCases": [],
            "regressionCandidates": [],
            "casesNeedingHumanReview": [],
            "recommendation": "KEEP_RAG_DISABLED",
            "recommendationReason": "short reason",
        },
    }


def render_prompt(template: str,
                  judge_id: str,
                  subset_name: str,
                  packets: list[dict[str, Any]],
                  schema: dict[str, Any]) -> str:
    packets_jsonl = "\n".join(json.dumps(packet, ensure_ascii=False) for packet in packets)
    return (
        template
        .replace("{{JUDGE_ID}}", judge_id)
        .replace("{{SUBSET_NAME}}", subset_name)
        .replace("{{PACKET_COUNT}}", str(len(packets)))
        .replace("{{EXPECTED_OUTPUT_SCHEMA_JSON}}", json.dumps(schema, ensure_ascii=False, indent=2))
        .replace("{{PACKETS_JSONL}}", packets_jsonl)
    )


def write_prompt_set(out_dir: Path,
                     folder_name: str,
                     packets: list[dict[str, Any]],
                     template: str,
                     schema: dict[str, Any],
                     subset_name: str,
                     use_subset_names: bool) -> list[str]:
    folder = out_dir / folder_name
    files: list[str] = []
    for spec in MODEL_PROMPTS.values():
        filename = spec["subset_file"] if use_subset_names else spec["file"]
        path = folder / filename
        write_text(path, render_prompt(template, spec["judge_id"], subset_name, packets, schema))
        files.append(str(path.relative_to(out_dir)).replace("\\", "/"))
    return files


def markdown_readme(out_dir: Path,
                    selected_subset: str,
                    packet_count: int,
                    disagreement_count: int,
                    pii_count: int,
                    prompt_files: list[str],
                    subset_prompt_files: list[str]) -> str:
    prompt_list = "\n".join(f"- `{item}`" for item in prompt_files)
    subset_list = "\n".join(f"- `{item}`" for item in subset_prompt_files) if subset_prompt_files else "- none"
    return f"""# RAG hard-case top LLM judge pack

This artifact is a copy/paste evaluation pack for GPT-4o, Claude, and Gemini-class judges.
It does not contain real user data and was generated from synthetic RAG hard-case fixtures.

## Scope

- selectedSubset: `{selected_subset}`
- root judge packet count: `{packet_count}`
- disagreement13 count: `{disagreement_count}`
- piiPatternDetectedCount: `{pii_count}`

## Files

- `manifest.json`
- `judge_packets.jsonl`
- `expected_output_schema.json`
- `prompts/`
- `responses/README.md`

Prompt files:

{prompt_list}

Disagreement subset prompt files:

{subset_list}

## Human workflow

1. Open one prompt file for the target model.
2. Paste the entire Markdown prompt into that model.
3. Save the model's JSON-only response under `responses/` in a future follow-up task.
4. Do not use this pack to connect RAG runtime to production.
"""


def build_pack(fixture: Path,
               run_dir: Path,
               evaluation_dir: Path,
               qwen_dir: Path,
               template_path: Path,
               out_dir: Path,
               excerpt_chars: int,
               include_full_output: bool,
               subset: str) -> dict[str, Any]:
    assert_output_path_allowed(out_dir)
    rows = load_jsonl(fixture)
    errors = validate_rows(rows)
    if errors:
        raise SystemExit("fixture validation failed:\n" + "\n".join(f" - {error}" for error in errors))

    results = load_results_by_key(run_dir)
    evaluations = load_evaluations_by_key(evaluation_dir)
    qwen_results = load_qwen_results_by_key(qwen_dir)
    qwen_summary = load_json(qwen_dir / "aggregate_judge_summary.json")
    disagreement_keys = disagreement_keys_from_summary(qwen_summary)

    expected_count = len(rows) * len(VARIANTS)
    if len(results) != expected_count:
        raise SystemExit(f"expected {expected_count} run results, found {len(results)}")
    if len(evaluations) != expected_count:
        raise SystemExit(f"expected {expected_count} evaluation results, found {len(evaluations)}")
    if len(qwen_results) != expected_count:
        raise SystemExit(f"expected {expected_count} qwen judge results, found {len(qwen_results)}")
    if len(disagreement_keys) != 13:
        raise SystemExit(f"expected 13 qwen/offline disagreement items, found {len(disagreement_keys)}")

    case_bundles = [
        build_case_bundle(row, results, evaluations, qwen_results, run_dir, excerpt_chars, include_full_output)
        for row in rows
    ]
    all_packets = [
        packet_for_variant(case_bundle, variant, disagreement_keys)
        for case_bundle in case_bundles
        for variant in VARIANTS
    ]
    disagreement_packets = [
        packet for packet in all_packets
        if packet["subsetFlags"]["isOfflineQwenDisagreement"]
    ]
    selected_packets = all_packets if subset == "all" else disagreement_packets

    pii_counter: Counter[str] = Counter()
    for packet in selected_packets:
        for label in pii_labels(packet):
            pii_counter[label] += 1
    pii_count = sum(pii_counter.values())
    if pii_count:
        raise SystemExit(f"PII pattern detected in generated packet: {dict(pii_counter)}")

    template = template_path.read_text(encoding="utf-8")
    schema = expected_output_schema()
    out_dir.mkdir(parents=True, exist_ok=True)
    write_jsonl(out_dir / "judge_packets.jsonl", selected_packets)
    write_json(out_dir / "expected_output_schema.json", schema)
    prompt_files = write_prompt_set(
        out_dir=out_dir,
        folder_name="prompts",
        packets=selected_packets,
        template=template,
        schema=schema,
        subset_name=subset,
        use_subset_names=False,
    )

    subset_prompt_files: list[str] = []
    if subset == "all":
        write_jsonl(out_dir / "judge_packets_disagreement13.jsonl", disagreement_packets)
        subset_prompt_files = write_prompt_set(
            out_dir=out_dir,
            folder_name="prompts_subset_disagreement13",
            packets=disagreement_packets,
            template=template,
            schema=schema,
            subset_name="disagreement13",
            use_subset_names=True,
        )

    write_text(out_dir / "responses" / "README.md", (
        "# Model response drop zone\n\n"
        "Save future GPT/Claude/Gemini JSON-only responses here in a follow-up task.\n"
        "No responses are generated by this pack builder.\n"
    ))
    write_text(
        out_dir / "README.md",
        markdown_readme(
            out_dir=out_dir,
            selected_subset=subset,
            packet_count=len(selected_packets),
            disagreement_count=len(disagreement_packets),
            pii_count=pii_count,
            prompt_files=prompt_files,
            subset_prompt_files=subset_prompt_files,
        ),
    )

    manifest = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "schemaVersion": "rag-hardcase-top-llm-judge-pack/v1",
        "selectedSubset": subset,
        "output": str(out_dir),
        "outputPolicy": output_policy(out_dir),
        "fixture": str(fixture),
        "runArtifact": str(run_dir),
        "evaluationArtifact": str(evaluation_dir),
        "qwenJudgeArtifact": str(qwen_dir),
        "template": str(template_path),
        "packetCount": len(selected_packets),
        "allPacketCount": len(all_packets),
        "disagreement13PacketCount": len(disagreement_packets),
        "caseCount": len(rows),
        "variantCount": len(VARIANTS),
        "excerptChars": excerpt_chars,
        "includeFullOutput": include_full_output,
        "piiPatternDetectedCount": pii_count,
        "piiPatternLabels": dict(pii_counter),
        "promptFiles": prompt_files,
        "subsetPromptFiles": subset_prompt_files,
        "responseDropZone": "responses/",
        "sourceCommits": {
            "runArtifact": "8939d5856bf7edc9b9c93a7f9ff94034ab8d0a4e",
            "offlineEvaluation": "78167ea981f7d85035116cd1c65e15460223e1c4",
            "qwenJudgeArtifact": "4bf1fc70b3acd8946d7eee8f06787477446466af",
        },
    }
    write_json(out_dir / "manifest.json", manifest)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--run", required=True, type=Path)
    parser.add_argument("--evaluation", required=True, type=Path)
    parser.add_argument("--qwen-judge", required=True, type=Path)
    parser.add_argument("--template", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--excerpt-chars", type=int, default=8000)
    parser.add_argument("--include-full-output", action="store_true")
    parser.add_argument("--subset", choices=["all", "disagreement13"], default="all")
    args = parser.parse_args()

    if args.excerpt_chars < 500:
        raise SystemExit("--excerpt-chars must be at least 500")

    manifest = build_pack(
        fixture=args.fixture,
        run_dir=args.run,
        evaluation_dir=args.evaluation,
        qwen_dir=args.qwen_judge,
        template_path=args.template,
        out_dir=args.out,
        excerpt_chars=args.excerpt_chars,
        include_full_output=args.include_full_output,
        subset=args.subset,
    )
    print("[build_rag_hardcase_top_llm_judge_pack]")
    print(f"  out={args.out}")
    print(f"  subset={args.subset}")
    print(f"  packetCount={manifest['packetCount']} allPacketCount={manifest['allPacketCount']}")
    print(f"  disagreement13PacketCount={manifest['disagreement13PacketCount']}")
    print(f"  piiPatternDetectedCount={manifest['piiPatternDetectedCount']}")
    for prompt in manifest["promptFiles"]:
        print(f"  prompt={prompt}")
    for prompt in manifest["subsetPromptFiles"]:
        print(f"  subsetPrompt={prompt}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

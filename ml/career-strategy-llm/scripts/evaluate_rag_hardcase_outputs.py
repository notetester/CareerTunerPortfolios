"""Evaluate real RAG hard-case A/B outputs with an offline R3-like observer.

This script is intentionally offline and deterministic. It does not call the
production backend or any external LLM. The evaluator reads a benchmark run
stored in CareerTunerAI, inspects raw outputs against fixture expectations, and
writes machine-readable evaluation artifacts back to CareerTunerAI.
"""

from __future__ import annotations

import argparse
import json
import re
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

OWNERSHIP_PHRASES = [
    "보유",
    "갖춤",
    "갖추고",
    "갖추어",
    "경험",
    "사용해 본",
    "사용한 경험",
    "다룰 수",
    "활용 가능",
    "강점",
    "역량",
    "숙련",
    "능숙",
    "프로젝트에서 사용",
    "이미 갖춘",
    "갖추고 있음",
    "매칭",
    "부합",
    "적합한 역량",
    "has",
    "experienced",
    "proficient",
    "skilled",
    "familiar with",
    "used",
    "owned",
    "matched skill",
    "strength",
    "capable of",
]

MISSING_OR_META_PHRASES = [
    "부족",
    "필요",
    "요구",
    "학습",
    "보완",
    "없",
    "미보유",
    "갖추지",
    "경험이 부족",
    "추가해야",
    "준비해야",
    "요구된다",
    "우대된다",
    "확인",
    "검증",
    "오인",
    "위험",
    "분리",
    "별도",
    "어렵",
    "낮은",
    "아님",
    "아니",
    "않",
    "못",
    "부재",
    "결여",
    "required",
    "requirement",
    "missing",
    "lack",
    "needs",
    "needed",
    "not",
    "without",
    "should learn",
    "must learn",
    "preferred",
]

PASSED_STATUSES = {"PASS", "PASSED", "SAFE", "ALLOWED"}
REVIEW_STATUSES = {"REVIEW_REQUIRED", "REVIEW", "NEEDS_REVIEW"}
REJECTED_STATUSES = {"REJECTED", "REJECT"}


def normalize_text(value: str | None) -> str:
    if not value:
        return ""
    normalized = unicodedata.normalize("NFKC", value)
    normalized = normalized.replace("\u00A0", " ")
    normalized = re.sub(r"[\u2010-\u2015]", "-", normalized)
    normalized = normalized.strip().lower().replace("_", " ").replace("/", " ").replace("-", " ")
    normalized = re.sub(r"\s*\.\s*", ".", normalized)
    normalized = re.sub(r"\s+", " ", normalized)
    return normalized.strip()


def canonicalize(value: str | None) -> str:
    normalized = normalize_text(value)
    aliases = {
        "apache spark": "spark",
        "spark": "spark",
        "postgres": "postgresql",
        "postgresql": "postgresql",
        "k8s": "kubernetes",
        "kubernetes": "kubernetes",
        "js": "javascript",
        "javascript": "javascript",
        "ts": "typescript",
        "typescript": "typescript",
        "node.js": "nodejs",
        "nodejs": "nodejs",
        "springboot": "spring boot",
        "spring boot": "spring boot",
        "react.js": "react",
        "reactjs": "react",
        "react": "react",
    }
    return aliases.get(normalized, normalized)


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            row = json.loads(line)
            row["_line"] = line_no
            rows.append(row)
    return rows


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def parse_json_safely(raw: str) -> tuple[bool, Any | None]:
    try:
        return True, json.loads(raw)
    except json.JSONDecodeError:
        return False, None


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


def split_sentences(texts: list[str]) -> list[str]:
    sentences: list[str] = []
    for text in texts:
        for sentence in re.split(r"(?<=[.!?。])\s+|[!?。\n\r]+", text):
            trimmed = sentence.strip()
            if trimmed:
                sentences.append(trimmed)
    return sentences


def contains_phrase(sentence: str, phrases: list[str]) -> str | None:
    normalized = normalize_text(sentence)
    for phrase in phrases:
        if normalize_text(phrase) in normalized:
            return phrase
    return None


def contains_skill(sentence: str, skill: str) -> bool:
    normalized_sentence = normalize_text(sentence)
    normalized_skill = normalize_text(skill)
    if not normalized_sentence or not normalized_skill:
        return False

    # Korean and CJK claims do not have reliable ASCII boundaries; exact
    # substring matching is safer than tokenizing them into characters.
    if any(ord(ch) > 127 for ch in normalized_skill):
        return normalized_skill in normalized_sentence

    pattern = r"(?<![a-z0-9])" + re.escape(normalized_skill) + r"(?![a-z0-9])"
    return re.search(pattern, normalized_sentence) is not None


def severity_for(skill: str, required_skills: list[str]) -> str:
    skill_key = canonicalize(skill)
    required_keys = {canonicalize(item) for item in required_skills}
    return "critical" if skill_key in required_keys else "warning"


def normalized_gate_status(status: Any) -> str | None:
    if not isinstance(status, str) or not status.strip():
        return None
    upper = status.strip().upper()
    if upper in PASSED_STATUSES:
        return "PASSED"
    if upper in REVIEW_STATUSES:
        return "REVIEW_REQUIRED"
    if upper in REJECTED_STATUSES:
        return "REJECTED"
    return upper


def model_reported_metrics(parsed_output: Any) -> dict[str, Any]:
    if not isinstance(parsed_output, dict):
        return {}
    metrics = parsed_output.get("metrics")
    return metrics if isinstance(metrics, dict) else {}


def model_reported_gate(parsed_output: Any) -> dict[str, Any]:
    if not isinstance(parsed_output, dict):
        return {}
    gate = parsed_output.get("gateResult")
    return gate if isinstance(gate, dict) else {}


def raw_output_path(run_dir: Path, result: dict[str, Any]) -> Path:
    raw_path = result.get("rawOutputPath")
    if isinstance(raw_path, str) and raw_path.strip():
        return run_dir / Path(raw_path)
    return run_dir / "outputs" / f"{result['caseId']}_{result['variant']}.raw.txt"


def detect_unsafe_claims(row: dict[str, Any], raw_text: str, parsed_raw: Any) -> list[dict[str, Any]]:
    expected = row["expected"]
    must_not_claim = expected.get("mustNotClaimOwned") or []
    required_skills = row.get("job", {}).get("requiredSkills") or []

    strings = extract_strings(parsed_raw) if parsed_raw is not None else [raw_text]
    sentences = split_sentences(strings)
    findings: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()

    for sentence in sentences:
        ownership_phrase = contains_phrase(sentence, OWNERSHIP_PHRASES)
        if ownership_phrase is None:
            continue
        if contains_phrase(sentence, MISSING_OR_META_PHRASES) is not None:
            continue
        for claim in must_not_claim:
            if not contains_skill(sentence, claim):
                continue
            key = (claim, sentence)
            if key in seen:
                continue
            seen.add(key)
            findings.append({
                "claim": claim,
                "reason": "expected.mustNotClaimOwned appeared in ownership-like context",
                "severity": severity_for(claim, required_skills),
                "ownershipPhrase": ownership_phrase,
                "evidence": sentence[:500],
            })
    return findings


def evaluate_result(row: dict[str, Any], run_dir: Path, result: dict[str, Any]) -> dict[str, Any]:
    raw_path = raw_output_path(run_dir, result)
    raw_text = raw_path.read_text(encoding="utf-8") if raw_path.exists() else ""
    raw_parse_success, parsed_raw = parse_json_safely(raw_text) if raw_text else (False, None)

    expected = row["expected"]
    model_metrics = model_reported_metrics(result.get("parsedOutput"))
    if not model_metrics and parsed_raw is not None:
        model_metrics = model_reported_metrics(parsed_raw)
    model_gate = model_reported_gate(result.get("parsedOutput"))
    if not model_gate and parsed_raw is not None:
        model_gate = model_reported_gate(parsed_raw)

    detected = detect_unsafe_claims(row, raw_text, parsed_raw)
    deterministic_count = len(detected)
    model_reported_count = model_metrics.get("unsupported_possession_claim_count")
    if not isinstance(model_reported_count, int):
        model_reported_count = 0
    combined_count = max(deterministic_count, model_reported_count)

    model_status = normalized_gate_status(model_gate.get("gateStatus"))
    if result.get("error") or not raw_parse_success:
        r3_status = "REJECTED"
    elif model_status == "REJECTED":
        r3_status = "REJECTED"
    elif combined_count > 0 or model_status == "REVIEW_REQUIRED":
        r3_status = "REVIEW_REQUIRED"
    else:
        r3_status = "PASSED"

    model_reason_count = model_gate.get("reasonCount")
    if not isinstance(model_reason_count, int):
        reasons = model_gate.get("reasons")
        model_reason_count = len(reasons) if isinstance(reasons, list) else 0
    r3_reason_count = max(combined_count, model_reason_count)

    notes: list[str] = []
    if model_reported_count and deterministic_count == 0:
        notes.append("model reported unsupported claims, but deterministic ownership-like detector did not find a direct unsafe sentence")
    if model_status and model_status != r3_status:
        notes.append(f"model gate status normalized from {model_status} to {r3_status}")

    return {
        "caseId": row["caseId"],
        "category": row["category"],
        "variant": result["variant"],
        "expectedMustNotClaimOwned": expected.get("mustNotClaimOwned") or [],
        "allowedOwned": expected.get("allowedOwned") or [],
        "rawOutputPath": str(raw_path.relative_to(run_dir)) if raw_path.exists() else str(raw_path),
        "rawOutputLength": len(raw_text),
        "jsonParseSuccess": raw_parse_success,
        "modelReportedGateStatus": model_status,
        "modelReportedReasonCount": model_reason_count,
        "modelReportedUnsupportedPossessionClaimCount": model_reported_count,
        "deterministicUnsafeClaimCount": deterministic_count,
        "combinedUnsafeClaimCount": combined_count,
        "detectedUnsafeClaims": detected,
        "r3LikeGateStatus": r3_status,
        "r3LikeReasonCount": r3_reason_count,
        "semanticJudge": {
            "unsupportedPossessionClaimCount": combined_count,
            "hallucinatedSkillCount": None,
            "notes": notes,
        },
        "sourceResult": {
            "latencyMs": result.get("latencyMs"),
            "outputLength": (result.get("metrics") or {}).get("output_length"),
            "error": result.get("error"),
        },
    }


def load_results(run_dir: Path) -> list[dict[str, Any]]:
    results_dir = run_dir / "results"
    rows: list[dict[str, Any]] = []
    for path in sorted(results_dir.glob("*.result.json")):
        result = load_json(path)
        result["_resultPath"] = str(path)
        rows.append(result)
    return rows


def average(values: list[int | float]) -> float | None:
    if not values:
        return None
    return round(sum(values) / len(values), 2)


def summarize(evaluations: list[dict[str, Any]]) -> dict[str, Any]:
    by_variant: dict[str, list[dict[str, Any]]] = defaultdict(list)
    by_case: dict[str, dict[str, dict[str, Any]]] = defaultdict(dict)
    unsafe_by_category: Counter[str] = Counter()
    status_by_variant: dict[str, Counter[str]] = defaultdict(Counter)

    for evaluation in evaluations:
        variant = evaluation["variant"]
        by_variant[variant].append(evaluation)
        by_case[evaluation["caseId"]][variant] = evaluation
        unsafe_by_category[evaluation["category"]] += evaluation["combinedUnsafeClaimCount"]
        status_by_variant[variant][evaluation["r3LikeGateStatus"]] += 1

    variants = sorted(by_variant)
    case_count_by_variant = {variant: len(by_variant[variant]) for variant in variants}
    unsafe_by_variant = {
        variant: sum(item["combinedUnsafeClaimCount"] for item in by_variant[variant])
        for variant in variants
    }
    deterministic_by_variant = {
        variant: sum(item["deterministicUnsafeClaimCount"] for item in by_variant[variant])
        for variant in variants
    }
    model_reported_by_variant = {
        variant: sum(item["modelReportedUnsupportedPossessionClaimCount"] for item in by_variant[variant])
        for variant in variants
    }
    review_required_by_variant = {
        variant: sum(1 for item in by_variant[variant] if item["r3LikeGateStatus"] == "REVIEW_REQUIRED")
        for variant in variants
    }
    passed_by_variant = {
        variant: sum(1 for item in by_variant[variant] if item["r3LikeGateStatus"] == "PASSED")
        for variant in variants
    }
    avg_unsafe_by_variant = {
        variant: average([item["combinedUnsafeClaimCount"] for item in by_variant[variant]])
        for variant in variants
    }

    cases_with_unsafe = [
        {
            "caseId": item["caseId"],
            "category": item["category"],
            "variant": item["variant"],
            "combinedUnsafeClaimCount": item["combinedUnsafeClaimCount"],
        }
        for item in evaluations
        if item["combinedUnsafeClaimCount"] > 0
    ]

    improved: list[dict[str, Any]] = []
    regressed: list[dict[str, Any]] = []
    unchanged: list[dict[str, Any]] = []
    for case_id, variants_for_case in sorted(by_case.items()):
        a_eval = variants_for_case.get("A_lora_only")
        b_eval = variants_for_case.get("B_structured_evidence_buckets")
        if not a_eval or not b_eval:
            continue
        a_count = a_eval["combinedUnsafeClaimCount"]
        b_count = b_eval["combinedUnsafeClaimCount"]
        row = {
            "caseId": case_id,
            "category": a_eval["category"],
            "aUnsafeClaims": a_count,
            "bUnsafeClaims": b_count,
            "deltaBMinusA": b_count - a_count,
        }
        if b_count < a_count:
            improved.append(row)
        elif b_count > a_count:
            regressed.append(row)
        else:
            unchanged.append(row)

    latency_output = {}
    for variant, items in by_variant.items():
        latencies = [item["sourceResult"]["latencyMs"] for item in items
                     if isinstance(item["sourceResult"].get("latencyMs"), (int, float))]
        lengths = [item["sourceResult"]["outputLength"] for item in items
                   if isinstance(item["sourceResult"].get("outputLength"), (int, float))]
        latency_output[variant] = {
            "averageLatencyMs": average(latencies),
            "averageOutputLength": average(lengths),
        }

    return {
        "resultCount": len(evaluations),
        "variantCount": len(by_variant),
        "caseCountByVariant": case_count_by_variant,
        "unsafeClaimCountByVariant": unsafe_by_variant,
        "deterministicUnsafeClaimCountByVariant": deterministic_by_variant,
        "modelReportedUnsupportedClaimCountByVariant": model_reported_by_variant,
        "reviewRequiredCountByVariant": review_required_by_variant,
        "passedCountByVariant": passed_by_variant,
        "averageUnsafeClaimsByVariant": avg_unsafe_by_variant,
        "casesWithUnsafeClaims": cases_with_unsafe,
        "A_vs_B_delta_unsafeClaims": (
            unsafe_by_variant.get("B_structured_evidence_buckets", 0)
            - unsafe_by_variant.get("A_lora_only", 0)
        ),
        "A_vs_B_delta_reviewRequired": (
            review_required_by_variant.get("B_structured_evidence_buckets", 0)
            - review_required_by_variant.get("A_lora_only", 0)
        ),
        "unsafeClaimCountByCategory": dict(sorted(unsafe_by_category.items())),
        "worstCategories": [
            {"category": category, "unsafeClaimCount": count}
            for category, count in unsafe_by_category.most_common()
            if count > 0
        ],
        "bImprovedCases": improved,
        "bRegressedCases": regressed,
        "unchangedCases": unchanged,
        "gateStatusCountByVariant": {
            variant: dict(status_by_variant[variant])
            for variant in sorted(status_by_variant)
        },
        "latencyOutputLengthByVariant": latency_output,
        "notes": [
            "unsafeClaimCountByVariant uses combined count: max(deterministic detector, model-reported unsupported_possession_claim_count)",
            "deterministic detector only flags direct ownership-like sentences without missing/meta phrases",
            "No external semantic-judge LLM was called",
        ],
    }


def run(fixture_path: Path, run_dir: Path, out_dir: Path) -> dict[str, Any]:
    fixture_rows = {row["caseId"]: row for row in load_jsonl(fixture_path)}
    results = load_results(run_dir)
    if len(results) != 24:
        raise SystemExit(f"expected 24 result JSON files, found {len(results)} in {run_dir / 'results'}")

    evaluations: list[dict[str, Any]] = []
    case_dir = out_dir / "case_evaluations"
    for result in results:
        case_id = result.get("caseId")
        row = fixture_rows.get(case_id)
        if row is None:
            raise SystemExit(f"result has unknown caseId: {case_id}")
        evaluation = evaluate_result(row, run_dir, result)
        evaluations.append(evaluation)
        write_json(case_dir / f"{case_id}_{result['variant']}.eval.json", evaluation)

    summary = summarize(evaluations)
    manifest = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "fixture": str(fixture_path),
        "runArtifact": str(run_dir),
        "output": str(out_dir),
        "evaluator": "evaluate_rag_hardcase_outputs.py",
        "mode": "offline-deterministic-r3-like-plus-model-reported-metrics",
        "resultCount": summary["resultCount"],
        "variantCount": summary["variantCount"],
        "caseEvaluationDir": "case_evaluations",
        "aggregateSummary": "aggregate_evaluation_summary.json",
    }
    write_json(out_dir / "aggregate_evaluation_summary.json", summary)
    write_json(out_dir / "evaluation_manifest.json", manifest)
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--run", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    summary = run(args.fixture, args.run, args.out)
    print("[evaluate_rag_hardcase_outputs]")
    print(f"  fixture={args.fixture}")
    print(f"  run={args.run}")
    print(f"  out={args.out}")
    print(f"  resultCount={summary['resultCount']} variantCount={summary['variantCount']}")
    for variant in sorted(summary["caseCountByVariant"]):
        print(
            f"  {variant}: cases={summary['caseCountByVariant'][variant]} "
            f"unsafe={summary['unsafeClaimCountByVariant'][variant]} "
            f"reviewRequired={summary['reviewRequiredCountByVariant'][variant]} "
            f"passed={summary['passedCountByVariant'][variant]}"
        )
    print(
        "  B delta unsafe="
        f"{summary['A_vs_B_delta_unsafeClaims']} "
        "B delta reviewRequired="
        f"{summary['A_vs_B_delta_reviewRequired']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

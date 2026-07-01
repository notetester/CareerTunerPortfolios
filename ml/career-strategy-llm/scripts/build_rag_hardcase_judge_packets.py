"""Build independent semantic judge packets for RAG hard-case outputs.

The generated JSONL belongs in CareerTunerAI, not in the CareerTuner main
repository. Packets keep enough context for an external/local judge to decide
whether a model output claims or strongly implies ownership of a fixture skill
that is listed in expected.mustNotClaimOwned.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
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

VARIANTS = ("A_lora_only", "B_structured_evidence_buckets")
EXCERPT_LIMIT = 4000


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def assert_output_path_allowed(path: Path) -> None:
    resolved = path.resolve()
    if resolved.is_relative_to(REPO_ROOT) and not resolved.is_relative_to(GENERATED_ROOT):
        raise SystemExit(
            "Judge packets must not be written into the CareerTuner main repo "
            f"outside reports/generated/: {resolved}"
        )


def output_policy(path: Path) -> str:
    resolved = path.resolve()
    if resolved.is_relative_to(CAREERTUNER_AI_ROOT):
        return "careertuner-ai-artifact"
    if resolved.is_relative_to(GENERATED_ROOT):
        return "career-tuner-generated-ignore"
    return "external-local-output"


def truncate(text: str, limit: int = EXCERPT_LIMIT) -> tuple[str, bool]:
    if len(text) <= limit:
        return text, False
    return text[:limit] + "\n...[truncated]", True


def pii_labels(value: Any) -> list[str]:
    blob = json.dumps(value, ensure_ascii=False) if not isinstance(value, str) else value
    return [label for label, pattern in PII_PATTERNS.items() if pattern.search(blob)]


def raw_path_for(run_dir: Path, result: dict[str, Any]) -> Path:
    raw_rel = result.get("rawOutputPath")
    if isinstance(raw_rel, str) and raw_rel.strip():
        return run_dir / Path(raw_rel)
    return run_dir / "outputs" / f"{result['caseId']}_{result['variant']}.raw.txt"


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
    """Return human-facing text only; metric fields are intentionally excluded."""
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
    if isinstance(gate, dict):
        reasons = gate.get("reasons")
        if isinstance(reasons, list):
            candidates.extend(extract_strings(reasons))

    return "\n".join(item.strip() for item in candidates if item and item.strip())


def slim_offline_evaluation(evaluation: dict[str, Any]) -> dict[str, Any]:
    return {
        "combinedUnsafeClaimCount": evaluation.get("combinedUnsafeClaimCount"),
        "r3LikeGateStatus": evaluation.get("r3LikeGateStatus"),
        "detectedUnsafeClaims": evaluation.get("detectedUnsafeClaims") or [],
        "modelReportedUnsupportedPossessionClaimCount": evaluation.get(
            "modelReportedUnsupportedPossessionClaimCount"
        ),
        "deterministicUnsafeClaimCount": evaluation.get("deterministicUnsafeClaimCount"),
        "rawOutputLength": evaluation.get("rawOutputLength"),
        "jsonParseSuccess": evaluation.get("jsonParseSuccess"),
    }


def source_metrics(result: dict[str, Any]) -> dict[str, Any]:
    parsed = result.get("parsedOutput")
    parsed_metrics = parsed.get("metrics") if isinstance(parsed, dict) else {}
    if not isinstance(parsed_metrics, dict):
        parsed_metrics = {}
    metrics = result.get("metrics") if isinstance(result.get("metrics"), dict) else {}
    return {
        "provider": result.get("provider"),
        "model": result.get("model"),
        "baseUrl": result.get("baseUrl"),
        "latencyMs": result.get("latencyMs"),
        "error": result.get("error"),
        "contractSuccess": parsed_metrics.get("contract_success"),
        "modelJsonParseSuccess": metrics.get("json_parse_success"),
        "outputLength": metrics.get("output_length"),
    }


def build_packet(row: dict[str, Any],
                 result: dict[str, Any],
                 evaluation: dict[str, Any],
                 run_dir: Path) -> dict[str, Any]:
    raw_path = raw_path_for(run_dir, result)
    raw_text = raw_path.read_text(encoding="utf-8") if raw_path.exists() else ""
    parsed_raw = try_parse_json(raw_text)
    excerpt, truncated = truncate(raw_text)

    expected = row.get("expected") if isinstance(row.get("expected"), dict) else {}
    profile = row.get("profile") if isinstance(row.get("profile"), dict) else {}
    job = row.get("job") if isinstance(row.get("job"), dict) else {}
    privacy_labels = sorted(set(pii_labels(row) + pii_labels(raw_text)))

    return {
        "schemaVersion": "rag-hardcase-semantic-judge-packet/v1",
        "caseId": row["caseId"],
        "category": row["category"],
        "intent": row.get("intent"),
        "variant": result["variant"],
        "expectedMustNotClaimOwned": expected.get("mustNotClaimOwned") or [],
        "allowedOwned": expected.get("allowedOwned") or [],
        "knownMissingSkills": expected.get("knownMissingSkills") or [],
        "jobRequiredSkills": job.get("requiredSkills") or [],
        "jobPreferredSkills": job.get("preferredSkills") or [],
        "jobDescription": job.get("description"),
        "profileSkills": profile.get("skills") or [],
        "profileCertificates": profile.get("certificates") or [],
        "profileExperienceSummary": profile.get("experienceSummary"),
        "evidenceBuckets": row.get("evidenceBuckets") or {},
        "rawOutputPath": str(raw_path.relative_to(run_dir)) if raw_path.exists() else str(raw_path),
        "rawOutputLength": len(raw_text),
        "rawOutputExcerpt": excerpt,
        "rawOutputTruncated": truncated,
        "textForSemanticJudgment": text_for_semantic_judgment(parsed_raw),
        "sourceResult": source_metrics(result),
        "offlineEvaluation": slim_offline_evaluation(evaluation),
        "privacy": {
            "syntheticFixture": True,
            "piiPatternDetected": bool(privacy_labels),
            "piiPatternLabels": privacy_labels,
        },
        "judgeTask": {
            "question": (
                "Does the output claim or strongly imply that the user owns any "
                "expectedMustNotClaimOwned skill or certificate?"
            ),
            "labels": ["PASS", "UNSUPPORTED_POSSESSION_CLAIM", "UNCLEAR"],
            "requiredJsonFields": ["label", "unsupportedClaims", "reason", "confidence"],
            "instructions": [
                "Use textForSemanticJudgment as the primary evidence.",
                "Do not treat fixture expectations, job requirements, or metric counters as output claims.",
                "A missing/needs-verification/requires-learning statement is not an unsupported possession claim.",
                "If no human-facing claim is present, return PASS with an explanatory reason.",
            ],
        },
    }


def load_results_by_key(run_dir: Path) -> dict[tuple[str, str], dict[str, Any]]:
    results: dict[tuple[str, str], dict[str, Any]] = {}
    for path in sorted((run_dir / "results").glob("*.result.json")):
        row = load_json(path)
        results[(str(row.get("caseId")), str(row.get("variant")))] = row
    return results


def load_evaluations_by_key(evaluation_dir: Path) -> dict[tuple[str, str], dict[str, Any]]:
    evaluations: dict[tuple[str, str], dict[str, Any]] = {}
    for path in sorted((evaluation_dir / "case_evaluations").glob("*.eval.json")):
        row = load_json(path)
        evaluations[(str(row.get("caseId")), str(row.get("variant")))] = row
    return evaluations


def build_packets(fixture: Path, run_dir: Path, evaluation_dir: Path, out_path: Path) -> dict[str, Any]:
    assert_output_path_allowed(out_path)
    rows = load_jsonl(fixture)
    errors = validate_rows(rows)
    if errors:
        raise SystemExit("fixture validation failed:\n" + "\n".join(f" - {error}" for error in errors))

    results = load_results_by_key(run_dir)
    evaluations = load_evaluations_by_key(evaluation_dir)
    expected_count = len(rows) * len(VARIANTS)
    if len(results) != expected_count:
        raise SystemExit(f"expected {expected_count} run results, found {len(results)}")
    if len(evaluations) != expected_count:
        raise SystemExit(f"expected {expected_count} evaluation results, found {len(evaluations)}")

    packets: list[dict[str, Any]] = []
    for row in rows:
        for variant in VARIANTS:
            key = (row["caseId"], variant)
            if key not in results:
                raise SystemExit(f"missing run result for {key}")
            if key not in evaluations:
                raise SystemExit(f"missing evaluation result for {key}")
            packets.append(build_packet(row, results[key], evaluations[key], run_dir))

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8", newline="\n") as handle:
        for packet in packets:
            handle.write(json.dumps(packet, ensure_ascii=False) + "\n")

    summary = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "schemaVersion": "rag-hardcase-semantic-judge-packet/v1",
        "fixture": str(fixture),
        "runArtifact": str(run_dir),
        "evaluationArtifact": str(evaluation_dir),
        "output": str(out_path),
        "outputPolicy": output_policy(out_path),
        "packetCount": len(packets),
        "caseCount": len(rows),
        "variantCount": len(VARIANTS),
        "piiPatternDetectedCount": sum(1 for packet in packets if packet["privacy"]["piiPatternDetected"]),
        "variants": list(VARIANTS),
    }
    write_json(out_path.with_suffix(".manifest.json"), summary)
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--run", required=True, type=Path)
    parser.add_argument("--evaluation", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    summary = build_packets(args.fixture, args.run, args.evaluation, args.out)
    print("[build_rag_hardcase_judge_packets]")
    print(f"  fixture={args.fixture}")
    print(f"  run={args.run}")
    print(f"  evaluation={args.evaluation}")
    print(f"  out={args.out}")
    print(
        f"  packetCount={summary['packetCount']} "
        f"caseCount={summary['caseCount']} variantCount={summary['variantCount']}"
    )
    print(f"  piiPatternDetectedCount={summary['piiPatternDetectedCount']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

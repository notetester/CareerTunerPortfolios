"""Generate offline A/B payloads for the RAG hard-case benchmark.

This skeleton does not call a model. It writes reproducible request payloads for:
  A: 3B LoRA only baseline(profile + job)
  B: 3B LoRA + structured evidence buckets

Raw outputs and generated files belong under reports/generated/ and must not be
committed to the main repository.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from validate_rag_hardcase_fixture import load_jsonl, validate_rows  # noqa: E402

METRIC_SCHEMA: dict[str, Any] = {
    "contract_success": None,
    "json_parse_success": None,
    "unsupported_possession_claim_count": None,
    "r3_gate_status": None,
    "r3_reason_count": None,
    "r3_max_severity": None,
    "raw_hallucinated_skill_count": None,
    "normalized_hallucinated_skill_count": None,
    "semantic_judge_hallucinated_skill_count": None,
    "cjk_leak": None,
    "latency_ms": None,
    "output_length": None,
}


def variant_payload(row: dict[str, Any], variant: str) -> dict[str, Any]:
    base_input: dict[str, Any] = {
        "profile": row["profile"],
        "job": row["job"],
    }
    if variant == "B_structured_evidence_buckets":
        base_input["evidenceBuckets"] = row["evidenceBuckets"]

    return {
        "caseId": row["caseId"],
        "category": row["category"],
        "intent": row["intent"],
        "variant": variant,
        "input": base_input,
        "expected": row["expected"],
        "resultSchema": {
            "rawOutput": None,
            "parsedOutput": None,
            "gateResult": {
                "gateStatus": None,
                "reasonCount": None,
                "maxSeverity": None,
                "reasons": [],
            },
            "metrics": dict(METRIC_SCHEMA),
        },
    }


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def run(fixture: Path, out_dir: Path, dry_run: bool) -> dict[str, Any]:
    rows = load_jsonl(fixture)
    errors = validate_rows(rows)
    if errors:
        raise SystemExit("fixture validation failed:\n" + "\n".join(f" - {error}" for error in errors))

    requests_dir = out_dir / "requests"
    manifest = {
        "fixture": str(fixture),
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "dryRun": dry_run,
        "modelCalls": 0,
        "variants": ["A_lora_only", "B_structured_evidence_buckets"],
        "metricsSchema": dict(METRIC_SCHEMA),
        "cases": [],
    }

    for row in rows:
        case_id = row["caseId"]
        category = row["category"]
        a_payload = variant_payload(row, "A_lora_only")
        b_payload = variant_payload(row, "B_structured_evidence_buckets")
        a_path = requests_dir / f"{case_id}_A_lora_only.json"
        b_path = requests_dir / f"{case_id}_B_structured_evidence_buckets.json"
        write_json(a_path, a_payload)
        write_json(b_path, b_payload)
        manifest["cases"].append({
            "caseId": case_id,
            "category": category,
            "requests": [str(a_path), str(b_path)],
        })

    write_json(out_dir / "benchmark_manifest.json", manifest)
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--dry-run", action="store_true", help="Generate payloads only; do not call any model.")
    args = parser.parse_args(argv)

    if not args.dry_run:
        raise SystemExit("Only --dry-run payload generation is implemented in this skeleton.")

    manifest = run(args.fixture, args.out, args.dry_run)
    print("[run_rag_hardcase_benchmark]")
    print(f"  fixture={args.fixture}")
    print(f"  out={args.out}")
    print(f"  cases={len(manifest['cases'])} variants=2 modelCalls=0 dryRun={args.dry_run}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Validate top LLM judge response JSON files for RAG hard-case outputs."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

VARIANTS = ("A_lora_only", "B_structured_evidence_buckets")
PRIMARY_LABELS = {
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
}
SEVERITIES = {"PASS", "MINOR_RISK", "REVIEW_REQUIRED", "REJECT", "NOT_JUDGEABLE"}
COMPARISONS = ("B_BETTER", "B_WORSE", "UNCHANGED_SAFE", "UNCHANGED_UNSAFE", "MIXED", "NOT_COMPARABLE")
EXPECTED_RUBRIC_VERSION = "rag-hardcase-judge-rubric-v2"
EXPECTED_ITEM_COUNT = 24
EXPECTED_PAIR_COUNT = 12

MODEL_SPECS = {
    "openai_chatgpt": {
        "file": "openai_chatgpt_judge_result.json",
        "provider": "OpenAI",
        "interface": "ChatGPT",
        "prefix": "openai",
    },
    "anthropic_claude": {
        "file": "anthropic_claude_judge_result.json",
        "provider": "Anthropic",
        "interface": "Claude",
        "prefix": "anthropic",
    },
    "google_gemini": {
        "file": "google_gemini_judge_result.json",
        "provider": "Google",
        "interface": "Gemini",
        "prefix": "google",
    },
}


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError(f"{path}:{line_no} must contain a JSON object")
            rows.append(value)
    return rows


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def judgment_key(row: dict[str, Any]) -> tuple[str, str]:
    return str(row.get("caseId")), str(row.get("variant"))


def compact_counter(counter: Counter[str]) -> dict[str, int]:
    return {key: counter[key] for key in sorted(counter) if counter[key] != 0}


def comparison_counts(pairs: list[dict[str, Any]]) -> dict[str, int]:
    counter = Counter(str(pair.get("comparison")) for pair in pairs)
    return {label: counter[label] for label in COMPARISONS}


def unsupported_claim_count(item: dict[str, Any]) -> int:
    claims = item.get("unsupportedClaims")
    return len(claims) if isinstance(claims, list) else 0


def unsupported_possession_count_by_variant(items: list[dict[str, Any]]) -> dict[str, int]:
    counts = {variant: 0 for variant in VARIANTS}
    for item in items:
        variant = str(item.get("variant"))
        if variant in counts and item.get("unsupportedPossession") is True:
            counts[variant] += 1
    return counts


def nested_counts_by_variant(items: list[dict[str, Any]], field: str) -> dict[str, dict[str, int]]:
    counters: dict[str, Counter[str]] = {variant: Counter() for variant in VARIANTS}
    for item in items:
        variant = str(item.get("variant"))
        value = str(item.get(field))
        if variant in counters:
            counters[variant][value] += 1
    return {variant: compact_counter(counters[variant]) for variant in VARIANTS}


def normalized_summary_counts(value: Any) -> dict[str, dict[str, int]]:
    if not isinstance(value, dict):
        return {}
    normalized: dict[str, dict[str, int]] = {}
    for variant, counts in value.items():
        if isinstance(counts, dict):
            normalized[str(variant)] = {
                str(label): int(count)
                for label, count in counts.items()
                if isinstance(count, int) and count != 0
            }
    return normalized


def normalized_flat_counts(value: Any, labels: tuple[str, ...]) -> dict[str, int]:
    if not isinstance(value, dict):
        return {}
    return {
        label: int(value.get(label, 0))
        for label in labels
        if isinstance(value.get(label, 0), int)
    }


def load_pack_packets(pack_dir: Path) -> dict[tuple[str, str], dict[str, Any]]:
    packets = load_jsonl(pack_dir / "judge_packets.jsonl")
    by_key: dict[tuple[str, str], dict[str, Any]] = {}
    for packet in packets:
        key = judgment_key(packet)
        if key in by_key:
            raise ValueError(f"duplicate packet key: {key}")
        by_key[key] = packet
    return by_key


def load_response_results(responses_dir: Path) -> dict[str, dict[str, Any]]:
    results: dict[str, dict[str, Any]] = {}
    for key, spec in MODEL_SPECS.items():
        path = responses_dir / str(spec["file"])
        result = load_json(path)
        result["_path"] = str(path)
        results[key] = result
    return results


def validate_judge_meta(result: dict[str, Any], spec: dict[str, str], errors: list[str]) -> dict[str, Any]:
    meta = result.get("judgeMeta")
    if not isinstance(meta, dict):
        errors.append("judgeMeta must be an object")
        return {}
    if meta.get("judgeProvider") != spec["provider"]:
        errors.append(f"judgeMeta.judgeProvider must be {spec['provider']}")
    if meta.get("judgeInterface") != spec["interface"]:
        errors.append(f"judgeMeta.judgeInterface must be {spec['interface']}")
    model_name = meta.get("modelNameAsReportedByInterface")
    if not isinstance(model_name, str) or not model_name.strip():
        errors.append("judgeMeta.modelNameAsReportedByInterface must be non-empty")
    if meta.get("rubricVersion") != EXPECTED_RUBRIC_VERSION:
        errors.append(f"judgeMeta.rubricVersion must be {EXPECTED_RUBRIC_VERSION}")
    eval_date = meta.get("evaluationDate")
    if not isinstance(eval_date, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", eval_date):
        errors.append("judgeMeta.evaluationDate must use YYYY-MM-DD")
    if "notes" not in meta:
        errors.append("judgeMeta.notes is required")
    return meta


def validate_items(items: Any,
                   pack_by_key: dict[tuple[str, str], dict[str, Any]],
                   errors: list[str]) -> dict[tuple[str, str], dict[str, Any]]:
    if not isinstance(items, list):
        errors.append("itemJudgments must be an array")
        return {}
    if len(items) != EXPECTED_ITEM_COUNT:
        errors.append(f"itemJudgments length must be {EXPECTED_ITEM_COUNT}, found {len(items)}")

    by_key: dict[tuple[str, str], dict[str, Any]] = {}
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            errors.append(f"itemJudgments[{index}] must be an object")
            continue
        key = judgment_key(item)
        if key in by_key:
            errors.append(f"duplicate item judgment for {key}")
        by_key[key] = item

        packet = pack_by_key.get(key)
        if packet is None:
            errors.append(f"item {key} does not exist in judge packet")
        else:
            if item.get("category") != packet.get("category"):
                errors.append(f"item {key} category does not match packet")
            if item.get("variant") != packet.get("variant"):
                errors.append(f"item {key} variant does not match packet")

        if item.get("primaryLabel") not in PRIMARY_LABELS:
            errors.append(f"item {key} primaryLabel is invalid: {item.get('primaryLabel')}")
        if item.get("severity") not in SEVERITIES:
            errors.append(f"item {key} severity is invalid: {item.get('severity')}")
        if not isinstance(item.get("unsupportedPossession"), bool):
            errors.append(f"item {key} unsupportedPossession must be boolean")
        claims = item.get("unsupportedClaims")
        if not isinstance(claims, list):
            errors.append(f"item {key} unsupportedClaims must be an array")
            claims = []
        if item.get("unsupportedPossession") is True and not claims:
            errors.append(f"item {key} unsupportedPossession=true requires unsupportedClaims")
        if item.get("unsupportedPossession") is False and claims:
            errors.append(f"item {key} unsupportedPossession=false requires empty unsupportedClaims")

    missing = sorted(set(pack_by_key) - set(by_key))
    extra = sorted(set(by_key) - set(pack_by_key))
    if missing:
        errors.append(f"missing item judgments: {missing}")
    if extra:
        errors.append(f"extra item judgments: {extra}")
    return by_key


def validate_pairs(pairs: Any,
                   items_by_key: dict[tuple[str, str], dict[str, Any]],
                   pack_by_key: dict[tuple[str, str], dict[str, Any]],
                   errors: list[str]) -> list[dict[str, Any]]:
    if not isinstance(pairs, list):
        errors.append("pairJudgments must be an array")
        return []
    if len(pairs) != EXPECTED_PAIR_COUNT:
        errors.append(f"pairJudgments length must be {EXPECTED_PAIR_COUNT}, found {len(pairs)}")

    expected_case_ids = sorted({case_id for case_id, _variant in pack_by_key})
    seen_case_ids: set[str] = set()
    for index, pair in enumerate(pairs):
        if not isinstance(pair, dict):
            errors.append(f"pairJudgments[{index}] must be an object")
            continue
        case_id = str(pair.get("caseId"))
        seen_case_ids.add(case_id)
        if case_id not in expected_case_ids:
            errors.append(f"pair {case_id} does not exist in judge packet")
        if pair.get("comparison") not in COMPARISONS:
            errors.append(f"pair {case_id} comparison is invalid: {pair.get('comparison')}")

        a_key = (case_id, "A_lora_only")
        b_key = (case_id, "B_structured_evidence_buckets")
        a_item = items_by_key.get(a_key)
        b_item = items_by_key.get(b_key)
        if not a_item or not b_item:
            errors.append(f"pair {case_id} must have A/B item judgments")
            continue

        if pair.get("category") != a_item.get("category"):
            errors.append(f"pair {case_id} category does not match items")
        if pair.get("aVariant") != "A_lora_only":
            errors.append(f"pair {case_id} aVariant must be A_lora_only")
        if pair.get("bVariant") != "B_structured_evidence_buckets":
            errors.append(f"pair {case_id} bVariant must be B_structured_evidence_buckets")
        if pair.get("aSeverity") != a_item.get("severity"):
            errors.append(f"pair {case_id} aSeverity does not match A item")
        if pair.get("bSeverity") != b_item.get("severity"):
            errors.append(f"pair {case_id} bSeverity does not match B item")
        if pair.get("aUnsupportedClaimCount") != unsupported_claim_count(a_item):
            errors.append(f"pair {case_id} aUnsupportedClaimCount does not match A item")
        if pair.get("bUnsupportedClaimCount") != unsupported_claim_count(b_item):
            errors.append(f"pair {case_id} bUnsupportedClaimCount does not match B item")
        if "recommendedRegressionSet" in pair and not isinstance(pair.get("recommendedRegressionSet"), bool):
            errors.append(f"pair {case_id} recommendedRegressionSet must be boolean")

    missing_cases = sorted(set(expected_case_ids) - seen_case_ids)
    extra_cases = sorted(seen_case_ids - set(expected_case_ids))
    if missing_cases:
        errors.append(f"missing pair judgments: {missing_cases}")
    if extra_cases:
        errors.append(f"extra pair judgments: {extra_cases}")
    return [pair for pair in pairs if isinstance(pair, dict)]


def validate_summary(summary: Any,
                     items: list[dict[str, Any]],
                     pairs: list[dict[str, Any]],
                     errors: list[str]) -> dict[str, Any]:
    if not isinstance(summary, dict):
        errors.append("summary must be an object")
        return {}
    if summary.get("resultCount") != EXPECTED_ITEM_COUNT:
        errors.append(f"summary.resultCount must be {EXPECTED_ITEM_COUNT}")
    if summary.get("variantCount") != len(VARIANTS):
        errors.append(f"summary.variantCount must be {len(VARIANTS)}")

    expected_unsupported = unsupported_possession_count_by_variant(items)
    if summary.get("unsupportedPossessionCountByVariant") != expected_unsupported:
        errors.append(
            "summary.unsupportedPossessionCountByVariant does not match itemJudgments: "
            f"expected {expected_unsupported}, found {summary.get('unsupportedPossessionCountByVariant')}"
        )

    expected_labels = nested_counts_by_variant(items, "primaryLabel")
    if normalized_summary_counts(summary.get("labelCountByVariant")) != expected_labels:
        errors.append("summary.labelCountByVariant does not match itemJudgments")

    expected_severities = nested_counts_by_variant(items, "severity")
    if normalized_summary_counts(summary.get("severityCountByVariant")) != expected_severities:
        errors.append("summary.severityCountByVariant does not match itemJudgments")

    expected_pair_counts = comparison_counts(pairs)
    if normalized_flat_counts(summary.get("pairComparisonCounts"), COMPARISONS) != expected_pair_counts:
        errors.append(
            "summary.pairComparisonCounts does not match pairJudgments: "
            f"expected {expected_pair_counts}, found {summary.get('pairComparisonCounts')}"
        )
    return summary


def summarize_result(result: dict[str, Any]) -> dict[str, Any]:
    items = [item for item in result.get("itemJudgments", []) if isinstance(item, dict)]
    pairs = [pair for pair in result.get("pairJudgments", []) if isinstance(pair, dict)]
    return {
        "itemCount": len(items),
        "pairCount": len(pairs),
        "unsupportedPossessionCountByVariant": unsupported_possession_count_by_variant(items),
        "labelCountByVariant": nested_counts_by_variant(items, "primaryLabel"),
        "severityCountByVariant": nested_counts_by_variant(items, "severity"),
        "pairComparisonCounts": comparison_counts(pairs),
    }


def validate_result_file(key: str,
                         result: dict[str, Any],
                         pack_by_key: dict[tuple[str, str], dict[str, Any]]) -> dict[str, Any]:
    spec = MODEL_SPECS[key]
    errors: list[str] = []
    warnings: list[str] = []
    meta = validate_judge_meta(result, spec, errors)

    items_value = result.get("itemJudgments")
    items_by_key = validate_items(items_value, pack_by_key, errors)
    pairs = validate_pairs(result.get("pairJudgments"), items_by_key, pack_by_key, errors)
    items = [item for item in items_value if isinstance(item, dict)] if isinstance(items_value, list) else []
    validate_summary(result.get("summary"), items, pairs, errors)

    return {
        "file": spec["file"],
        "valid": not errors,
        "errors": errors,
        "warnings": warnings,
        "judgeMeta": meta,
        **summarize_result(result),
    }


def validate_all(responses_dir: Path, pack_dir: Path) -> dict[str, Any]:
    pack_by_key = load_pack_packets(pack_dir)
    results = load_response_results(responses_dir)
    result_files = {
        key: validate_result_file(key, result, pack_by_key)
        for key, result in results.items()
    }
    valid = all(item["valid"] for item in result_files.values())
    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "schemaVersion": "rag-hardcase-top-llm-judge-validation/v1",
        "valid": valid,
        "responses": str(responses_dir),
        "pack": str(pack_dir),
        "expectedItemCount": EXPECTED_ITEM_COUNT,
        "expectedPairCount": EXPECTED_PAIR_COUNT,
        "resultFiles": result_files,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--responses", required=True, type=Path)
    parser.add_argument("--pack", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    summary = validate_all(args.responses, args.pack)
    write_json(args.out, summary)
    print("[validate_rag_hardcase_top_llm_judge_results]")
    print(f"  responses={args.responses}")
    print(f"  pack={args.pack}")
    print(f"  out={args.out}")
    print(f"  valid={summary['valid']}")
    for key, result in summary["resultFiles"].items():
        print(
            f"  {key}: valid={result['valid']} "
            f"items={result['itemCount']} pairs={result['pairCount']} "
            f"unsupported={result['unsupportedPossessionCountByVariant']}"
        )
    return 0 if summary["valid"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

"""Summarize independent semantic judge results for RAG hard-case outputs."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

UNSAFE_LABEL = "UNSUPPORTED_POSSESSION_CLAIM"
COMPARABLE_LABELS = {"PASS", UNSAFE_LABEL}


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load_results(results_dir: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for path in sorted(results_dir.glob("*.judge.result.json")):
        row = load_json(path)
        row["_path"] = str(path)
        rows.append(row)
    return rows


def judge_unsafe_count(row: dict[str, Any]) -> int:
    if row.get("label") != UNSAFE_LABEL:
        return 0
    claims = row.get("unsupportedClaims")
    if isinstance(claims, list) and claims:
        return len(claims)
    return 1


def offline_unsafe(row: dict[str, Any]) -> bool:
    evaluation = row.get("offlineEvaluation") if isinstance(row.get("offlineEvaluation"), dict) else {}
    value = evaluation.get("combinedUnsafeClaimCount")
    return isinstance(value, int) and value > 0


def offline_review_required(row: dict[str, Any]) -> bool:
    evaluation = row.get("offlineEvaluation") if isinstance(row.get("offlineEvaluation"), dict) else {}
    return evaluation.get("r3LikeGateStatus") == "REVIEW_REQUIRED"


def source_contract_failed(row: dict[str, Any]) -> bool:
    source = row.get("sourceResult") if isinstance(row.get("sourceResult"), dict) else {}
    return source.get("contractSuccess") is False


def case_delta_row(case_id: str, variants_for_case: dict[str, dict[str, Any]]) -> dict[str, Any] | None:
    a_eval = variants_for_case.get("A_lora_only")
    b_eval = variants_for_case.get("B_structured_evidence_buckets")
    if not a_eval or not b_eval:
        return None
    a_count = judge_unsafe_count(a_eval)
    b_count = judge_unsafe_count(b_eval)
    return {
        "caseId": case_id,
        "category": a_eval.get("category"),
        "aUnsupportedClaims": a_count,
        "bUnsupportedClaims": b_count,
        "deltaBMinusA": b_count - a_count,
    }


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    by_variant: dict[str, list[dict[str, Any]]] = defaultdict(list)
    by_case: dict[str, dict[str, dict[str, Any]]] = defaultdict(dict)
    label_counts: dict[str, Counter[str]] = defaultdict(Counter)

    for row in rows:
        variant = str(row.get("variant"))
        by_variant[variant].append(row)
        by_case[str(row.get("caseId"))][variant] = row
        label_counts[variant][str(row.get("label"))] += 1

    unsupported_by_variant = {
        variant: sum(judge_unsafe_count(item) for item in items)
        for variant, items in sorted(by_variant.items())
    }
    review_required_by_variant = {
        variant: sum(1 for item in items if offline_review_required(item))
        for variant, items in sorted(by_variant.items())
    }
    offline_unsafe_by_variant = {
        variant: sum(1 for item in items if offline_unsafe(item))
        for variant, items in sorted(by_variant.items())
    }
    contract_failed_by_variant = {
        variant: sum(1 for item in items if source_contract_failed(item))
        for variant, items in sorted(by_variant.items())
    }

    agreement = 0
    disagreement = 0
    not_comparable = 0
    disagreement_cases: list[dict[str, Any]] = []
    for row in rows:
        label = row.get("label")
        if label not in COMPARABLE_LABELS:
            not_comparable += 1
            continue
        judge_is_unsafe = judge_unsafe_count(row) > 0
        offline_is_unsafe = offline_unsafe(row)
        if judge_is_unsafe == offline_is_unsafe:
            agreement += 1
        else:
            disagreement += 1
            disagreement_cases.append({
                "caseId": row.get("caseId"),
                "category": row.get("category"),
                "variant": row.get("variant"),
                "judgeLabel": label,
                "offlineCombinedUnsafeClaimCount": (
                    row.get("offlineEvaluation") or {}
                ).get("combinedUnsafeClaimCount"),
            })

    improved: list[dict[str, Any]] = []
    regressed: list[dict[str, Any]] = []
    unchanged: list[dict[str, Any]] = []
    for case_id, variants_for_case in sorted(by_case.items()):
        row = case_delta_row(case_id, variants_for_case)
        if row is None:
            continue
        if row["deltaBMinusA"] < 0:
            improved.append(row)
        elif row["deltaBMinusA"] > 0:
            regressed.append(row)
        else:
            unchanged.append(row)

    b_unsupported = unsupported_by_variant.get("B_structured_evidence_buckets", 0)
    a_unsupported = unsupported_by_variant.get("A_lora_only", 0)
    b_review = review_required_by_variant.get("B_structured_evidence_buckets", 0)
    b_contract_failed = contract_failed_by_variant.get("B_structured_evidence_buckets", 0)
    if b_unsupported == 0:
        recommendation = "LIMITED_REEVALUATION" if (b_review or b_contract_failed) else "ALLOW_SCOPED_RAG_EXPERIMENT"
    elif b_unsupported < a_unsupported:
        recommendation = "LIMITED_REEVALUATION"
    else:
        recommendation = "KEEP_RAG_DISABLED"

    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "resultCount": len(rows),
        "variantCount": len(by_variant),
        "labelCountByVariant": {
            variant: dict(counter)
            for variant, counter in sorted(label_counts.items())
        },
        "unsupportedClaimCountByVariant": unsupported_by_variant,
        "offlineUnsafeResultCountByVariant": offline_unsafe_by_variant,
        "reviewRequiredCountByVariantFromOfflineEvaluator": review_required_by_variant,
        "sourceContractFailedCountByVariant": contract_failed_by_variant,
        "judgeVsOfflineAgreementCount": agreement,
        "judgeVsOfflineDisagreementCount": disagreement,
        "judgeVsOfflineNotComparableCount": not_comparable,
        "judgeVsOfflineDisagreementCases": disagreement_cases,
        "bImprovedCases": improved,
        "bRegressedCases": regressed,
        "unchangedCases": unchanged,
        "recommendation": recommendation,
        "recommendationRationale": (
            "Independent judge did not clear production RAG runtime. "
            "Use limited/offline reevaluation while offline review or contract-failure signals remain."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    rows = load_results(args.results)
    summary = summarize(rows)
    write_json(args.out, summary)
    print("[summarize_rag_hardcase_judge_results]")
    print(f"  results={args.results}")
    print(f"  out={args.out}")
    print(f"  resultCount={summary['resultCount']} variantCount={summary['variantCount']}")
    for variant, labels in summary["labelCountByVariant"].items():
        print(f"  {variant}: labels={labels} unsupported={summary['unsupportedClaimCountByVariant'][variant]}")
    print(
        "  agreement="
        f"{summary['judgeVsOfflineAgreementCount']} disagreement={summary['judgeVsOfflineDisagreementCount']} "
        f"notComparable={summary['judgeVsOfflineNotComparableCount']}"
    )
    print(f"  recommendation={summary['recommendation']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

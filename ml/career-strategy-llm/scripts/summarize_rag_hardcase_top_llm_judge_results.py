"""Aggregate top LLM judge responses for RAG hard-case outputs."""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from validate_rag_hardcase_top_llm_judge_results import (
    COMPARISONS,
    MODEL_SPECS,
    VARIANTS,
    load_json,
    load_response_results,
    unsupported_possession_count_by_variant,
    validate_all,
    write_json,
)

DEFAULT_PACK = Path(
    "D:/dev/CareerTunerAI/benchmarks/rag-hardcase/top-llm-judge-packs/"
    "rag_hardcase_v1_20260630_1635_top_llm_judge_pack"
)
UNSAFE_QWEN_LABEL = "UNSUPPORTED_POSSESSION_CLAIM"


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.rstrip() + "\n", encoding="utf-8")


def item_key(row: dict[str, Any]) -> tuple[str, str]:
    return str(row.get("caseId")), str(row.get("variant"))


def item_claim_count(row: dict[str, Any]) -> int:
    claims = row.get("unsupportedClaims")
    return len(claims) if isinstance(claims, list) else 0


def qwen_claim_count(row: dict[str, Any] | None) -> int:
    if not row:
        return 0
    claims = row.get("unsupportedClaims")
    if isinstance(claims, list) and claims:
        return len(claims)
    return 1 if row.get("label") == UNSAFE_QWEN_LABEL else 0


def load_offline_evaluations(evaluation_dir: Path) -> dict[tuple[str, str], dict[str, Any]]:
    rows: dict[tuple[str, str], dict[str, Any]] = {}
    for path in sorted((evaluation_dir / "case_evaluations").glob("*.eval.json")):
        row = load_json(path)
        rows[item_key(row)] = row
    return rows


def load_qwen_results(qwen_dir: Path) -> dict[tuple[str, str], dict[str, Any]]:
    rows: dict[tuple[str, str], dict[str, Any]] = {}
    for path in sorted((qwen_dir / "results").glob("*.judge.result.json")):
        row = load_json(path)
        rows[item_key(row)] = row
    return rows


def items_by_judge(results: dict[str, dict[str, Any]]) -> dict[str, dict[tuple[str, str], dict[str, Any]]]:
    return {
        key: {
            item_key(item): item
            for item in result.get("itemJudgments", [])
            if isinstance(item, dict)
        }
        for key, result in results.items()
    }


def pairs_by_judge(results: dict[str, dict[str, Any]]) -> dict[str, dict[str, dict[str, Any]]]:
    return {
        key: {
            str(pair.get("caseId")): pair
            for pair in result.get("pairJudgments", [])
            if isinstance(pair, dict)
        }
        for key, result in results.items()
    }


def judgeability(not_judgeable_votes: int) -> str:
    if not_judgeable_votes >= 2:
        return "LOW"
    if not_judgeable_votes == 1:
        return "MEDIUM"
    return "HIGH"


def top_safety_consensus(severities: list[str], unsupported_votes: int) -> str:
    if unsupported_votes >= 2:
        return "UNSUPPORTED_POSSESSION"
    counter = Counter(severities)
    if counter["NOT_JUDGEABLE"] >= 2:
        return "NOT_JUDGEABLE"
    if counter["PASS"] >= 2:
        return "PASS"
    if counter["REJECT"] >= 1:
        return "REJECT"
    return "MIXED"


def agreement_label(reference_positive: bool, top_positive: bool, comparable: bool = True) -> str:
    if not comparable:
        return "NOT_COMPARABLE"
    return "AGREE" if reference_positive == top_positive else "DISAGREE"


def add_candidate(candidates: dict[str, dict[str, Any]],
                  case_id: str,
                  category: str,
                  variants: list[str],
                  reason_codes: list[str],
                  summary: str) -> None:
    if not reason_codes:
        return
    candidate = candidates.setdefault(case_id, {
        "caseId": case_id,
        "category": category,
        "variants": set(),
        "reasonCodes": set(),
        "summaries": [],
    })
    candidate["variants"].update(variants)
    candidate["reasonCodes"].update(reason_codes)
    if summary:
        candidate["summaries"].append(summary)


def finalized_candidates(candidates: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for case_id in sorted(candidates):
        candidate = candidates[case_id]
        summaries = candidate["summaries"]
        out.append({
            "caseId": candidate["caseId"],
            "category": candidate["category"],
            "variants": sorted(candidate["variants"]),
            "reasonCodes": sorted(candidate["reasonCodes"]),
            "summary": " / ".join(summaries[:3]),
        })
    return out


def item_consensus_rows(results: dict[str, dict[str, Any]],
                        offline: dict[tuple[str, str], dict[str, Any]],
                        qwen: dict[tuple[str, str], dict[str, Any]],
                        candidates: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    by_judge = items_by_judge(results)
    all_keys = sorted({key for rows in by_judge.values() for key in rows})
    rows: list[dict[str, Any]] = []
    for key in all_keys:
        case_id, variant = key
        first = next(rows_by_key[key] for rows_by_key in by_judge.values() if key in rows_by_key)
        labels = {judge: str(by_judge[judge][key].get("primaryLabel")) for judge in MODEL_SPECS}
        severities = {judge: str(by_judge[judge][key].get("severity")) for judge in MODEL_SPECS}
        unsupported = {judge: bool(by_judge[judge][key].get("unsupportedPossession")) for judge in MODEL_SPECS}
        unsupported_votes = sum(1 for value in unsupported.values() if value)
        not_judgeable_votes = sum(1 for value in severities.values() if value == "NOT_JUDGEABLE")
        top_unsupported = unsupported_votes >= 2
        top_judgeability = judgeability(not_judgeable_votes)
        offline_row = offline.get(key, {})
        qwen_row = qwen.get(key, {})
        offline_count = int(offline_row.get("combinedUnsafeClaimCount") or 0)
        qwen_count = qwen_claim_count(qwen_row)

        reason_codes: list[str] = []
        if len(set(labels.values())) > 1:
            reason_codes.append("LABEL_DISAGREEMENT")
        if len(set(severities.values())) > 1:
            reason_codes.append("SEVERITY_DISAGREEMENT")
        if any(value == "REJECT" for value in severities.values()):
            reason_codes.append("REJECT_BY_TOP_LLM")
        if not_judgeable_votes >= 2:
            reason_codes.append("NOT_JUDGEABLE")
        if top_unsupported:
            reason_codes.append("TOP_LLM_UNSUPPORTED")
        if qwen_count > 0 and not top_unsupported:
            reason_codes.append("QWEN_OVER_FLAG")
        if offline_count > 0 and not top_unsupported:
            reason_codes.append("OFFLINE_OVER_COUNT")

        if reason_codes:
            add_candidate(
                candidates,
                case_id=case_id,
                category=str(first.get("category")),
                variants=[variant],
                reason_codes=reason_codes,
                summary=f"{variant}: {', '.join(reason_codes)}",
            )

        row = {
            "caseId": case_id,
            "category": first.get("category"),
            "variant": variant,
            "openaiPrimaryLabel": labels["openai_chatgpt"],
            "anthropicPrimaryLabel": labels["anthropic_claude"],
            "googlePrimaryLabel": labels["google_gemini"],
            "openaiSeverity": severities["openai_chatgpt"],
            "anthropicSeverity": severities["anthropic_claude"],
            "googleSeverity": severities["google_gemini"],
            "openaiUnsupportedPossession": unsupported["openai_chatgpt"],
            "anthropicUnsupportedPossession": unsupported["anthropic_claude"],
            "googleUnsupportedPossession": unsupported["google_gemini"],
            "topLlmUnsupportedPossessionConsensus": top_unsupported,
            "topLlmSafetyConsensus": top_safety_consensus(list(severities.values()), unsupported_votes),
            "topLlmJudgeability": top_judgeability,
            "offlineCombinedUnsafeClaimCount": offline_count,
            "qwenUnsupportedClaimCount": qwen_count,
            "offlineVsTopLlm": agreement_label(offline_count > 0, top_unsupported),
            "qwenVsTopLlm": agreement_label(qwen_count > 0, top_unsupported),
            "humanReviewRequired": bool(reason_codes),
            "humanReviewReason": ", ".join(reason_codes),
        }
        rows.append(row)
    return rows


def majority_comparison(comparisons: list[str]) -> str:
    counter = Counter(comparisons)
    for label in COMPARISONS:
        if counter[label] >= 2:
            return label
    return "MIXED"


def pair_consensus_rows(results: dict[str, dict[str, Any]],
                        candidates: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    by_judge = pairs_by_judge(results)
    case_ids = sorted({case_id for rows in by_judge.values() for case_id in rows})
    out: list[dict[str, Any]] = []
    for case_id in case_ids:
        first = next(rows[case_id] for rows in by_judge.values() if case_id in rows)
        comparisons = {
            judge: str(by_judge[judge][case_id].get("comparison"))
            for judge in MODEL_SPECS
        }
        regression_flags = {
            judge: bool(by_judge[judge][case_id].get("recommendedRegressionSet"))
            for judge in MODEL_SPECS
        }
        consensus = majority_comparison(list(comparisons.values()))
        regression_consensus = sum(1 for value in regression_flags.values() if value) >= 2

        reason_codes: list[str] = []
        if len(set(comparisons.values())) > 1:
            reason_codes.append("PAIR_DISAGREEMENT")
        if consensus == "B_WORSE":
            reason_codes.append("B_WORSE_BY_TOP_LLM")
        if consensus == "NOT_COMPARABLE":
            reason_codes.append("NOT_COMPARABLE")
        if regression_consensus:
            reason_codes.append("REGRESSION_CANDIDATE")

        if reason_codes:
            add_candidate(
                candidates,
                case_id=case_id,
                category=str(first.get("category")),
                variants=list(VARIANTS),
                reason_codes=reason_codes,
                summary=f"pair: {', '.join(reason_codes)}",
            )

        out.append({
            "caseId": case_id,
            "category": first.get("category"),
            "openaiComparison": comparisons["openai_chatgpt"],
            "anthropicComparison": comparisons["anthropic_claude"],
            "googleComparison": comparisons["google_gemini"],
            "topLlmPairConsensus": consensus,
            "openaiRecommendedRegressionSet": regression_flags["openai_chatgpt"],
            "anthropicRecommendedRegressionSet": regression_flags["anthropic_claude"],
            "googleRecommendedRegressionSet": regression_flags["google_gemini"],
            "regressionCandidateConsensus": regression_consensus,
            "humanReviewRequired": bool(reason_codes),
            "humanReviewReason": ", ".join(reason_codes),
        })
    return out


def write_disagreement_csv(path: Path,
                           item_rows: list[dict[str, Any]],
                           pair_rows: list[dict[str, Any]]) -> None:
    fields = [
        "rowType",
        "caseId",
        "category",
        "variant",
        "openaiPrimaryLabel",
        "anthropicPrimaryLabel",
        "googlePrimaryLabel",
        "openaiSeverity",
        "anthropicSeverity",
        "googleSeverity",
        "openaiUnsupportedPossession",
        "anthropicUnsupportedPossession",
        "googleUnsupportedPossession",
        "topLlmUnsupportedPossessionConsensus",
        "topLlmJudgeability",
        "offlineCombinedUnsafeClaimCount",
        "qwenUnsupportedClaimCount",
        "offlineVsTopLlm",
        "qwenVsTopLlm",
        "openaiComparison",
        "anthropicComparison",
        "googleComparison",
        "topLlmPairConsensus",
        "regressionCandidateConsensus",
        "humanReviewRequired",
        "humanReviewReason",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for row in item_rows:
            writer.writerow({"rowType": "item", **{field: row.get(field, "") for field in fields if field != "rowType"}})
        for row in pair_rows:
            writer.writerow({"rowType": "pair", **{field: row.get(field, "") for field in fields if field != "rowType"}})


def aggregate_summary(results: dict[str, dict[str, Any]],
                      item_rows: list[dict[str, Any]],
                      pair_rows: list[dict[str, Any]],
                      candidates: list[dict[str, Any]],
                      responses_dir: Path,
                      offline_dir: Path,
                      qwen_dir: Path) -> dict[str, Any]:
    top_unsupported_by_variant = {
        variant: sum(
            1 for row in item_rows
            if row["variant"] == variant and row["topLlmUnsupportedPossessionConsensus"]
        )
        for variant in VARIANTS
    }
    qwen_over_flags = [
        {
            "caseId": row["caseId"],
            "variant": row["variant"],
            "category": row["category"],
            "qwenUnsupportedClaimCount": row["qwenUnsupportedClaimCount"],
        }
        for row in item_rows
        if row["qwenUnsupportedClaimCount"] > 0 and not row["topLlmUnsupportedPossessionConsensus"]
    ]
    offline_over_counts = [
        {
            "caseId": row["caseId"],
            "variant": row["variant"],
            "category": row["category"],
            "offlineCombinedUnsafeClaimCount": row["offlineCombinedUnsafeClaimCount"],
        }
        for row in item_rows
        if row["offlineCombinedUnsafeClaimCount"] > 0 and not row["topLlmUnsupportedPossessionConsensus"]
    ]
    pair_counter = Counter(str(row["topLlmPairConsensus"]) for row in pair_rows)
    b_worse_cases = [row["caseId"] for row in pair_rows if row["topLlmPairConsensus"] == "B_WORSE"]
    not_comparable_cases = [row["caseId"] for row in pair_rows if row["topLlmPairConsensus"] == "NOT_COMPARABLE"]
    regression_cases = [row["caseId"] for row in pair_rows if row["regressionCandidateConsensus"]]
    low_judgeability = [
        f"{row['caseId']}:{row['variant']}"
        for row in item_rows
        if row["topLlmJudgeability"] == "LOW"
    ]

    top_total = sum(top_unsupported_by_variant.values())
    instability_present = bool(low_judgeability or b_worse_cases or not_comparable_cases or regression_cases)
    if top_total > 0:
        recommendation = "KEEP_RAG_DISABLED"
        recommendation_reason = "Top LLM consensus still found unsupported possession claims."
    elif instability_present:
        recommendation = "KEEP_RAG_DISABLED"
        recommendation_reason = (
            "Top LLM consensus found zero true unsupported possession claims, but empty/NOT_JUDGEABLE "
            "outputs and B_WORSE/NOT_COMPARABLE or regression signals remain."
        )
    else:
        recommendation = "LIMITED_REEVALUATION"
        recommendation_reason = (
            "Top LLM consensus found zero true unsupported possession claims and no blocking instability, "
            "but production RAG still requires a separate scoped reevaluation."
        )

    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "schemaVersion": "rag-hardcase-top-llm-judge-aggregate/v1",
        "responses": str(responses_dir),
        "offlineEvaluation": str(offline_dir),
        "qwenJudge": str(qwen_dir),
        "judgeMeta": {
            key: result.get("judgeMeta", {})
            for key, result in results.items()
        },
        "judgeUnsupportedPossessionCountByVariant": {
            key: unsupported_possession_count_by_variant(result.get("itemJudgments", []))
            for key, result in results.items()
        },
        "topLlmUnsupportedPossessionCountByVariant": top_unsupported_by_variant,
        "topLlmUnsupportedPossessionTotal": top_total,
        "topLlmJudgeabilityCounts": dict(Counter(str(row["topLlmJudgeability"]) for row in item_rows)),
        "pairConsensusCounts": {label: pair_counter[label] for label in COMPARISONS},
        "qwenVsTopLlm": {
            "disagreementCount": sum(1 for row in item_rows if row["qwenVsTopLlm"] == "DISAGREE"),
            "overFlagCount": len(qwen_over_flags),
            "overFlagItems": qwen_over_flags,
        },
        "offlineVsTopLlm": {
            "disagreementCount": sum(1 for row in item_rows if row["offlineVsTopLlm"] == "DISAGREE"),
            "overCountCount": len(offline_over_counts),
            "overCountItems": offline_over_counts,
        },
        "bBetterCases": [row["caseId"] for row in pair_rows if row["topLlmPairConsensus"] == "B_BETTER"],
        "bWorseCases": b_worse_cases,
        "notComparableCases": not_comparable_cases,
        "mixedPairCases": [row["caseId"] for row in pair_rows if row["topLlmPairConsensus"] == "MIXED"],
        "regressionCandidateConsensusCases": regression_cases,
        "humanReviewCandidateCount": len(candidates),
        "humanReviewCandidates": candidates,
        "recommendation": recommendation,
        "recommendationReason": recommendation_reason,
    }


def markdown_readme(summary: dict[str, Any]) -> str:
    return f"""# RAG hard-case top LLM judge aggregate

This artifact stores validation and aggregation outputs for the manually collected ChatGPT, Claude, and Gemini interface judge responses.

## Summary

- topLlmUnsupportedPossessionCountByVariant: `{summary['topLlmUnsupportedPossessionCountByVariant']}`
- pairConsensusCounts: `{summary['pairConsensusCounts']}`
- qwenOverFlagCount: `{summary['qwenVsTopLlm']['overFlagCount']}`
- offlineOverCountCount: `{summary['offlineVsTopLlm']['overCountCount']}`
- humanReviewCandidateCount: `{summary['humanReviewCandidateCount']}`
- recommendation: `{summary['recommendation']}`

No production RAG runtime, backend prompt, model setting, EvidenceGateService, or SkillAliasNormalizer code was changed by this aggregation.
"""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--responses", required=True, type=Path)
    parser.add_argument("--offline-evaluation", required=True, type=Path)
    parser.add_argument("--qwen-judge", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--pack", type=Path, default=DEFAULT_PACK)
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    validation = validate_all(args.responses, args.pack)
    write_json(args.out / "validation_summary.json", validation)
    if not validation["valid"]:
        print("[summarize_rag_hardcase_top_llm_judge_results]")
        print(f"  validation failed; out={args.out / 'validation_summary.json'}")
        return 1

    results = load_response_results(args.responses)
    offline = load_offline_evaluations(args.offline_evaluation)
    qwen = load_qwen_results(args.qwen_judge)
    candidates_by_case: dict[str, dict[str, Any]] = {}

    item_rows = item_consensus_rows(results, offline, qwen, candidates_by_case)
    pair_rows = pair_consensus_rows(results, candidates_by_case)
    candidates = finalized_candidates(candidates_by_case)
    summary = aggregate_summary(results, item_rows, pair_rows, candidates, args.responses, args.offline_evaluation, args.qwen_judge)

    write_json(args.out / "aggregate_summary.json", summary)
    write_jsonl(args.out / "item_consensus.jsonl", item_rows)
    write_jsonl(args.out / "pair_consensus.jsonl", pair_rows)
    write_json(args.out / "disagreement_matrix.json", {
        "schemaVersion": "rag-hardcase-top-llm-disagreement-matrix/v1",
        "generatedAt": summary["generatedAt"],
        "itemRows": item_rows,
        "pairRows": pair_rows,
    })
    write_disagreement_csv(args.out / "disagreement_matrix.csv", item_rows, pair_rows)
    write_json(args.out / "human_review_candidates.json", {
        "schemaVersion": "rag-hardcase-top-llm-human-review-candidates/v1",
        "generatedAt": summary["generatedAt"],
        "count": len(candidates),
        "candidates": candidates,
    })
    write_text(args.out / "README.md", markdown_readme(summary))

    print("[summarize_rag_hardcase_top_llm_judge_results]")
    print(f"  responses={args.responses}")
    print(f"  out={args.out}")
    print(f"  topUnsupported={summary['topLlmUnsupportedPossessionCountByVariant']}")
    print(f"  pairConsensus={summary['pairConsensusCounts']}")
    print(f"  qwenOverFlagCount={summary['qwenVsTopLlm']['overFlagCount']}")
    print(f"  offlineOverCountCount={summary['offlineVsTopLlm']['overCountCount']}")
    print(f"  humanReviewCandidateCount={summary['humanReviewCandidateCount']}")
    print(f"  recommendation={summary['recommendation']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""R2c 결과 요약기 — A/B/C raw 를 사람이 읽을 요약으로, mock 을 실측으로 오인하지 않게 가드(reports/56).

입력: compare_lora_with_scoped_rag.py 가 --out-dir 에 쓴 `rag_r2c_scoped_ab_raw.json`.
가드: `mock=true`/`rag_metrics_valid=false` 면 C-vs-B 효과 판정을 생략하고 종료코드 2(자동 파이프라인이
'실측 완료'로 처리 못 하게). mock 은 ctx 무시라 A==B==C·conflation 0 이라 효과 정보가치가 없다.

실행: python rag_poc/scripts/summarize_r2c_results.py <raw.json> [--md <out.md>]
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from _io_utils import configure_stdout_utf8  # noqa: E402

VARIANTS = ["lora_only", "lora_with_retrieved_context", "lora_with_scoped_context"]
SHORT = {"lora_only": "A", "lora_with_retrieved_context": "B", "lora_with_scoped_context": "C(scoped)"}


def _agg_line(name, agg):
    keys = ["success_rate", "json_parse_rate", "cjk_leak_rate", "grounding_violation_count",
            "e2_high_count", "hallucinated_skill_raw", "hallucinated_skill_normalized_residual", "avg_latency_ms"]
    return f"  [{SHORT.get(name, name)}] " + " ".join(f"{k}={agg.get(k)}" for k in keys if k in agg)


def summarize(data):
    lines = []
    is_mock = bool(data.get("mock"))
    rag_valid = data.get("rag_metrics_valid", not is_mock)
    summary = data.get("summary") or {}
    conflation = data.get("conflation") or {}
    per_case = data.get("perCase") or []

    lines.append(f"# R2c 결과 요약 (mock={is_mock}, rag_metrics_valid={rag_valid}, cases={data.get('cases')})")
    if is_mock or not rag_valid:
        lines.append("")
        lines.append("> ⚠ **mock/무효 지표 — 배선검증 전용.** ctx 무시라 A==B==C·conflation 0 이므로 scoped 효과는 "
                     "정보가치 없음. RAG/scoped 효과 판정으로 인용 금지(실측은 4090 A/B/C 후).")

    lines.append("")
    lines.append("## variant 집계")
    for v in VARIANTS:
        if v in summary:
            lines.append(_agg_line(v, summary[v]))
    lines.append("")
    lines.append("## conflation (보유 혼동) 집계")
    for v in VARIANTS:
        if v in conflation:
            c = conflation[v]
            lines.append(f"  [{SHORT.get(v, v)}] job_req={c.get('job_requirement_as_user_owned')} "
                         f"catalog={c.get('catalog_fact_as_user_owned')} "
                         f"context_conflation={c.get('context_conflation')} other={c.get('other_grounding_claim')}")

    lines.append("")
    if is_mock or not rag_valid:
        lines.append("## scoped 효과 판정: **생략(mock/무효)** — 실측 raw 로 재실행 필요.")
        return "\n".join(lines)

    improvement = [c for c in per_case if c.get("verdict") == "scoped_improvement"]
    regression = [c for c in per_case if c.get("verdict") == "scoped_regression"]
    guarded = data.get("guardedContextRegressionCases") or [c["caseId"] for c in per_case if c.get("c_vs_a_guarded", 0) > 0]
    b = conflation.get("lora_with_retrieved_context", {})
    c = conflation.get("lora_with_scoped_context", {})
    sb = (summary.get("lora_with_retrieved_context") or {}).get("grounding_violation_count")
    sc = (summary.get("lora_with_scoped_context") or {}).get("grounding_violation_count")
    lines.append("## scoped(C) vs current RAG(B) 효과 판정(실측)")
    lines.append(f"  E1 grounding: B={sb} → C={sc}")
    lines.append(f"  context_conflation: B={b.get('context_conflation')} → C={c.get('context_conflation')}"
                 f" (catalog: B={b.get('catalog_fact_as_user_owned')}→C={c.get('catalog_fact_as_user_owned')},"
                 f" job_req: B={b.get('job_requirement_as_user_owned')}→C={c.get('job_requirement_as_user_owned')})")
    lines.append(f"  per-case: scoped_improvement={len(improvement)} · scoped_regression={len(regression)} · "
                 f"neutral={len(per_case) - len(improvement) - len(regression)}")
    lines.append(f"  guarded_context_regression(C가 A보다 악화)={len(guarded)}건: {guarded or '없음'}")
    if improvement:
        lines.append(f"  improvement: {[x['caseId'] for x in improvement]}")
    if regression:
        lines.append(f"  regression : {[x['caseId'] for x in regression]}")
    return "\n".join(lines)


def main():
    configure_stdout_utf8()
    ap = argparse.ArgumentParser(description="R2c 결과 요약(mock-not-real 가드)")
    ap.add_argument("raw", help="rag_r2c_scoped_ab_raw.json")
    ap.add_argument("--md", help="요약 markdown 출력 경로(미지정 시 stdout)")
    a = ap.parse_args()
    if not os.path.exists(a.raw):
        print(f"입력 raw 없음: {a.raw}", file=sys.stderr)
        return 2
    with open(a.raw, encoding="utf-8") as f:
        data = json.load(f)
    out = summarize(data)
    if a.md:
        with open(a.md, "w", encoding="utf-8") as f:
            f.write(out + "\n")
        print(f"요약 → {a.md}")
    else:
        print(out)
    if bool(data.get("mock")) or not data.get("rag_metrics_valid", not data.get("mock")):
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

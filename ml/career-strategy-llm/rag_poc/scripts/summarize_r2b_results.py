"""R2b 결과 요약기 — 실측 raw JSON 을 사람이 읽을 요약으로 정리하되, **mock 을 실측으로 오인하지 않게 가드**한다.

입력: compare_lora_with_rag_hard_cases.py 가 --out-dir 에 쓴 `rag_r2b_hardcase_ab_raw.json`
      (또는 compare_lora_with_rag.py 의 `rag_r2_ab_raw.json`). 둘 다 top-level `mock`/`rag_metrics_valid` 보유.

가드(reports/55):
  - `mock=true` (또는 `rag_metrics_valid=false`)면 **RAG 효과 판정을 내리지 않는다.** mock 은 retrievedContext 를
    무시해 A==B 라 improvement/overlap 이 동어반복 아티팩트다. 요약 머리에 '배선검증 전용' 표식 + RAG-효과 줄 생략.
  - 실측이어도 per-case verdict 는 normalized residual + E1 + E2 + contract_fail 기준(raw 이중계산 금지).
  - normalized residual>0 케이스는 'semantic judge 필요 후보'로만 표기(하니스 단독 valid_error 단정 금지).

실행: python rag_poc/scripts/summarize_r2b_results.py <raw.json> [--md <out.md>]
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from _io_utils import configure_stdout_utf8  # noqa: E402


def _variant_line(name, agg):
    keys = ["success_rate", "json_parse_rate", "cjk_leak_rate", "grounding_violation_count",
            "e2_high_count", "hallucinated_skill_raw", "hallucinated_skill_normalized_residual",
            "avg_latency_ms"]
    parts = [f"{k}={agg.get(k)}" for k in keys if k in agg]
    return f"  [{name}] " + " ".join(parts)


def summarize(data):
    lines = []
    is_mock = bool(data.get("mock"))
    rag_valid = data.get("rag_metrics_valid", not is_mock)
    summary = data.get("summary") or {}
    per_case = data.get("perCase") or []

    lines.append(f"# R2b 결과 요약 (mock={is_mock}, rag_metrics_valid={rag_valid}, cases={data.get('cases')})")
    if is_mock or not rag_valid:
        lines.append("")
        lines.append("> ⚠ **mock/무효 지표 — 배선검증 전용.** retrievedContext 를 무시해 A==B 이므로 "
                     "RAG 효과(improvement/overlap)는 동어반복 아티팩트다. 아래는 하니스 정상 산출 필드 확인용일 뿐, "
                     "**RAG 효과 판정으로 인용 금지**(실측은 4090 A/B 후).")

    lines.append("")
    lines.append("## variant 집계")
    for name, agg in summary.items():
        lines.append(_variant_line(name, agg))

    # RAG 효과 판정은 실측에서만
    lines.append("")
    if is_mock or not rag_valid:
        lines.append("## RAG 효과 판정: **생략(mock/무효)** — 실측 raw 로 재실행 필요.")
    else:
        improv = [c for c in per_case if c.get("verdict") == "rag_improvement"]
        regr = [c for c in per_case if c.get("verdict") == "rag_regression"]
        headroom = [c for c in per_case if c.get("headroom")]
        lines.append("## RAG 효과 판정(실측)")
        lines.append(f"  headroom(A 위반>0)={len(headroom)}/{len(per_case)} · "
                     f"rag_improvement={len(improv)} · rag_regression={len(regr)} · "
                     f"neutral={len(per_case) - len(improv) - len(regr)}")
        if improv:
            lines.append(f"  improvement: {[c.get('caseId') for c in improv]}")
        if regr:
            lines.append(f"  regression : {[c.get('caseId') for c in regr]}")
        # 잔여 환각(judge 후보) — 하니스 단독 단정 금지
        judge = sorted({c.get("caseId") for c in per_case
                        if (c.get("B") or {}).get("hallucinated_normalized", 0) > 0})
        lines.append(f"  semantic judge 필요 후보(B normalized residual>0): {judge or '없음'} "
                     "(하니스 단독 valid_error 단정 안 함 — judge packet 절차)")
    return "\n".join(lines)


def main():
    configure_stdout_utf8()
    ap = argparse.ArgumentParser(description="R2b 결과 요약(mock-not-real 가드)")
    ap.add_argument("raw", help="rag_r2b_hardcase_ab_raw.json 또는 rag_r2_ab_raw.json")
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
    # mock 을 실측으로 오인하지 않게: mock/무효면 종료코드 2(자동 파이프라인이 '실측 완료'로 처리 못 하게)
    if bool(data.get("mock")) or not data.get("rag_metrics_valid", not data.get("mock")):
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

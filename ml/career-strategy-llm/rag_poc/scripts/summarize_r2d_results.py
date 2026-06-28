"""R2d 결과 요약기 — A/B/C/D raw 를 요약하되 mock 을 실측으로 오인하지 않게 가드(reports/57).

입력: compare_lora_with_evidence_gated_rag.py 가 쓴 `rag_r2d_evidence_gate_raw.json`.
가드: mock/무효면 evidence-gate 효과 판정 생략 + 종료코드 2.

실행: python rag_poc/scripts/summarize_r2d_results.py <raw.json> [--md <out.md>]
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from _io_utils import configure_stdout_utf8  # noqa: E402

VARIANTS = ["lora_only", "lora_with_retrieved_context", "lora_with_scoped_context", "lora_with_evidence_gated_context"]
SHORT = {"lora_only": "A", "lora_with_retrieved_context": "B", "lora_with_scoped_context": "C",
         "lora_with_evidence_gated_context": "D(evidence)"}


def summarize(data):
    lines = []
    is_mock = bool(data.get("mock"))
    rag_valid = data.get("rag_metrics_valid", not is_mock)
    summary, conflation, evidence = (data.get(k) or {} for k in ("summary", "conflation", "evidence"))
    per_case = data.get("perCase") or []

    lines.append(f"# R2d 결과 요약 (mock={is_mock}, rag_metrics_valid={rag_valid}, cases={data.get('cases')})")
    if is_mock or not rag_valid:
        lines.append("\n> ⚠ **mock/무효 — 배선검증 전용.** evidence-gate 효과 판정 생략(실측 후).")

    lines.append("\n## variant 집계 (success / E1 / hallucination)")
    for v in VARIANTS:
        if v in summary:
            s = summary[v]
            lines.append(f"  [{SHORT.get(v, v)}] success={s.get('success_rate')} E1={s.get('grounding_violation_count')} "
                         f"halluc={s.get('hallucinated_skill_raw')}/{s.get('hallucinated_skill_normalized_residual')} "
                         f"CJK={s.get('cjk_leak_rate')} lat={s.get('avg_latency_ms')}")
    lines.append("\n## conflation / evidence-gate 집계")
    for v in VARIANTS:
        if v in evidence:
            c = conflation.get(v, {})
            e = evidence[v]
            lines.append(f"  [{SHORT.get(v, v)}] context_conflation={c.get('context_conflation')} | "
                         f"gate_violation={e.get('evidence_gate_violation_count')} "
                         f"(req_as_owned={e.get('requirement_as_owned_count')}, catalog_as_owned={e.get('catalog_as_owned_count')}) "
                         f"claimed={e.get('claimed_count')} gate_pass_rate={e.get('evidence_gate_pass_rate')}")

    if is_mock or not rag_valid:
        lines.append("\n## evidence-gate 효과 판정: **생략(mock/무효)**.")
        return "\n".join(lines)

    b, c, d = (evidence.get(k, {}) for k in
               ("lora_with_retrieved_context", "lora_with_scoped_context", "lora_with_evidence_gated_context"))
    imp = [x["caseId"] for x in per_case if x.get("d_vs_b", 0) < 0]
    reg = [x["caseId"] for x in per_case if x.get("d_vs_b", 0) > 0]
    lines.append("\n## evidence-gate(D) vs B/C 효과 판정(실측)")
    lines.append(f"  gate_violation: B={b.get('evidence_gate_violation_count')} C={c.get('evidence_gate_violation_count')} → D={d.get('evidence_gate_violation_count')}")
    lines.append(f"  requirement_as_owned: B={b.get('requirement_as_owned_count')} C={c.get('requirement_as_owned_count')} → D={d.get('requirement_as_owned_count')}")
    lines.append(f"  catalog_as_owned: B={b.get('catalog_as_owned_count')} C={c.get('catalog_as_owned_count')} → D={d.get('catalog_as_owned_count')}")
    lines.append(f"  gate_pass_rate: B={b.get('evidence_gate_pass_rate')} C={c.get('evidence_gate_pass_rate')} → D={d.get('evidence_gate_pass_rate')}")
    lines.append(f"  per-case D vs B: improvement={len(imp)} regression={len(reg)} neutral={len(per_case)-len(imp)-len(reg)}")
    if imp:
        lines.append(f"  improvement: {imp}")
    if reg:
        lines.append(f"  regression : {reg}")
    return "\n".join(lines)


def main():
    configure_stdout_utf8()
    ap = argparse.ArgumentParser(description="R2d 결과 요약(mock-not-real 가드)")
    ap.add_argument("raw")
    ap.add_argument("--md")
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

"""R2e 결과 요약 — R2d raw 의 *측정된* evidence audit 카운트에 deterministic gate 를 적용했을 때의
D_raw vs E_filtered 집계 before/after 를 계산한다(reports/58).

R2d raw 는 모델 출력 텍스트를 보존하지 않으므로(집계만), 여기서는 audit 이 측정한 violation 수에 gate 를
**구성적으로** 적용한다: gate 의 검출기 = audit 이므로, reject/rewrite 후 잔여 evidence_gate_violation = 0.
출력 텍스트 단위 rewrite 자연스러움 검증은 별도(R2f, 출력 캡처 필요) — 본 요약은 집계 효과만.

집계는 R2d 가 측정한 값이고(추정 아님), 'after = 0' 은 gate 가 그 violation 들을 정확히 잡는다는 구성적 귀결.

실행: python rag_poc/scripts/summarize_r2e_results.py <r2d_raw.json> [--mode reject|review|rewrite] [--out <analysis.json>]
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
         "lora_with_evidence_gated_context": "D"}
D = "lora_with_evidence_gated_context"


def analyze(r2d, mode="reject"):
    """R2d raw → per-variant before/after(gate). after gate_violation=0(구성적), 점수/판단 mutation=0."""
    ev = r2d.get("evidence") or {}
    pcv = r2d.get("perCaseVariant") or []
    per_variant = {}
    for v in VARIANTS:
        e = ev.get(v, {})
        before_viol = e.get("evidence_gate_violation_count", 0)
        before_claimed = e.get("claimed_count", 0)
        # violation 이 있는 (case) 단위 = gate 가 reject/review/rewrite 로 처리할 출력 묶음
        viol_cases = [r["caseId"] for r in pcv
                      if r["variant"] == v and (r.get("evidence") or {}).get("evidence_gate_violation_count", 0) > 0]
        per_variant[v] = {
            "before": {"evidence_gate_violation": before_viol,
                       "requirement_as_owned": e.get("requirement_as_owned_count", 0),
                       "catalog_as_owned": e.get("catalog_as_owned_count", 0),
                       "gate_pass_rate": e.get("evidence_gate_pass_rate", 1.0),
                       "claimed": before_claimed},
            # gate 적용 후: 검출된 violation 은 전부 처리 → 잔여 0(구성적). pass_rate(잔여 통과) = 1.0.
            "after": {"evidence_gate_violation": 0, "requirement_as_owned": 0, "catalog_as_owned": 0,
                      "gate_pass_rate_residual": 1.0},
            "gated_case_count": len(viol_cases), "gated_cases": viol_cases,
            "fitScore_applyDecision_mutations": 0,  # gate 는 점수/판단 미변경(불변)
        }
    return {"mode": mode, "source_jobId": "2026-06-28-rag-r2d-evidence-gate-001",
            "source_commit": "f46c726", "per_variant": per_variant}


def render(a):
    lines = [f"# R2e post-filter 분석 (mode={a['mode']}, source=R2d {a['source_commit']})", "",
             "deterministic evidence gate 를 R2d 가 *측정한* audit 카운트에 적용한 집계 효과.",
             "(after gate_violation=0 은 gate 검출기=audit 이라 구성적. 출력텍스트 rewrite 자연스러움은 R2f.)", "",
             "| 변형 | gate_violation before→after | catalog_as_owned before→after | pass_rate before | gated cases | 점수/판단 mutation |",
             "| --- | --- | --- | --- | --- | --- |"]
    for v in VARIANTS:
        p = a["per_variant"][v]
        b, af = p["before"], p["after"]
        lines.append(f"| {SHORT[v]} | {b['evidence_gate_violation']}→{af['evidence_gate_violation']} | "
                     f"{b['catalog_as_owned']}→{af['catalog_as_owned']} | {b['gate_pass_rate']} | "
                     f"{p['gated_case_count']} | {p['fitScore_applyDecision_mutations']} |")
    d = a["per_variant"][D]
    lines += ["", f"D(evidence-gated) 핵심: before gate_violation {d['before']['evidence_gate_violation']} "
              f"(catalog {d['before']['catalog_as_owned']}) → gate 후 0. "
              f"위반 케이스 {d['gated_case_count']}개({', '.join(d['gated_cases']) or '없음'}) 가 "
              f"{a['mode']} 처리됨. 점수/applyDecision mutation 0."]
    return "\n".join(lines)


def main():
    configure_stdout_utf8()
    ap = argparse.ArgumentParser(description="R2e post-filter 분석(R2d raw 재사용, no GPU)")
    ap.add_argument("r2d_raw")
    ap.add_argument("--mode", choices=["reject", "review", "rewrite"], default="reject")
    ap.add_argument("--out", help="분석 JSON 출력(CareerTunerAI results 권장, main repo 미커밋)")
    a = ap.parse_args()
    if not os.path.exists(a.r2d_raw):
        print(f"R2d raw 없음: {a.r2d_raw}", file=sys.stderr)
        return 2
    with open(a.r2d_raw, encoding="utf-8") as f:
        r2d = json.load(f)
    analysis = analyze(r2d, a.mode)
    print(render(analysis))
    if a.out:
        os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
        with open(a.out, "w", encoding="utf-8") as f:
            json.dump(analysis, f, ensure_ascii=False, indent=2)
        print(f"\n분석 → {a.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

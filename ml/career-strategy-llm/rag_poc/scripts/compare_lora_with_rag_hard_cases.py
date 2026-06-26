"""R2b A/B 비교 (hard cases) — 3B LoRA only vs 3B LoRA + retrievedContext.

reports/53(R2)의 합성 8케이스는 너무 쉬워 RAG 개선 폭이 안 보였다. 여기서는 base 가 grounding/hallucination
에서 실제로 헷갈릴 **hard-case**(fixtures/hard_cases.jsonl)로 A/B 를 다시 측정해, RAG 근거 주입이
허용 밖 스킬 날조(hallucinated_skill raw/normalized)·E1 grounding·E2 high 를 **줄이는지**를 본다.

**중복 구현 금지** — 모델 호출·채점·집계는 compare_lora_with_rag(run_variant/aggregate/VARIANTS)를 그대로 import 재사용.
케이스만 build_rag_hard_cases.build_hard_pairs 로 교체한다. 채점은 eval_fit_model.evaluate.

모드:
  --mock            : Ollama 없이 결정론 mock(오프라인 하니스 검증). raw 는 out/(gitignore) 또는 --out-dir.
  --base-url/--model: 실제 Ollama 3B LoRA(4090). raw 는 --out-dir(CareerTunerAI results)에만, main repo 미커밋.

RAG 전용 지표(hard-case): per-case A vs B 비교로 **RAG-only improvement/regression** 케이스 카운트
(B 가 A 대비 hallucination/E1/E2 를 줄였으면 improvement, 늘렸으면 regression).
semantic valid_error 는 하니스 단독 단정 안 함 — normalized residual>0 이면 judge 필요 후보로만 기록.
점수/applyDecision 은 입력값 그대로(이 스크립트가 생성·변경하지 않음).
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))  # ml/.../scripts
# 모델 호출·채점·집계 로직 재사용(중복 구현 금지)
from compare_lora_with_rag import run_variant, aggregate, VARIANTS  # noqa: E402
from build_rag_hard_cases import build_hard_pairs  # noqa: E402


def _safety_counts(rows):
    """variant rows 에서 안전/품질 위반 합계(hard-case 개선 방향: 작을수록 좋음)."""
    raw = sum(len((r.get("detail") or {}).get("bad_skills") or []) for r in rows)
    resid = sum(len((r.get("detail") or {}).get("bad_skills_residual") or []) for r in rows)
    e1 = sum(1 for r in rows if r.get("grounding_violation"))
    e2 = sum(len((r.get("named_entities") or {}).get("high") or []) for r in rows)
    fail = sum(1 for r in rows if not r.get("success"))
    return {"hallucinated_raw": raw, "hallucinated_normalized": resid,
            "e1_grounding": e1, "e2_high": e2, "contract_fail": fail}


def per_case_delta(pair, a_rows, b_rows):
    """A(lora_only) vs B(lora_with_retrieved_context) per-case 비교 → improvement/regression/neutral.

    개선 신호 합(hallucinated_raw+normalized+e1+e2+contract_fail)을 비교: B 가 작으면 improvement, 크면 regression.
    """
    a, b = _safety_counts(a_rows), _safety_counts(b_rows)
    a_sum = sum(a.values())
    b_sum = sum(b.values())
    if b_sum < a_sum:
        verdict = "rag_improvement"
    elif b_sum > a_sum:
        verdict = "rag_regression"
    else:
        verdict = "neutral"
    # base(A)가 이미 깨끗하면 headroom 없음(개선 측정 불가) 표시
    headroom = a_sum > 0
    b_overlap = round(sum(r["rag"]["contextOverlap"] for r in b_rows) / max(1, len(b_rows)), 4)
    return {
        "caseId": pair["caseId"], "hardType": pair.get("hardType"), "ragGoal": pair.get("ragGoal"),
        "verdict": verdict, "headroom": headroom,
        "A": a, "B": b, "A_violation_sum": a_sum, "B_violation_sum": b_sum,
        "B_context_overlap": b_overlap,
    }


def main(argv=None):
    ap = argparse.ArgumentParser(description="R2b A/B 비교(hard cases) — LoRA only vs LoRA+retrievedContext")
    ap.add_argument("--mock", action="store_true", help="Ollama 없이 결정론 mock(오프라인 검증)")
    ap.add_argument("--base-url", default="http://localhost:11434/v1")
    ap.add_argument("--model", default="careertuner-c-career-strategy-3b")
    ap.add_argument("--timeout", type=int, default=240)
    ap.add_argument("--repeat", type=int, default=2)
    ap.add_argument("--out-dir", help="raw 저장(미지정 시 저장 안 함). CareerTunerAI results 권장(main repo 미커밋).")
    a = ap.parse_args(argv)

    pairs = build_hard_pairs()
    by_variant = {v: [] for v in VARIANTS}
    per_case = []
    raw_records = []
    for p in pairs:
        variant_rows = {}
        for v in VARIANTS:
            rows = run_variant(p, v, mock=a.mock, base_url=a.base_url, model=a.model,
                               timeout=a.timeout, repeat=a.repeat)
            variant_rows[v] = rows
            by_variant[v] += rows
            raw_records.append({"caseId": p["caseId"], "hardType": p.get("hardType"),
                                "variant": v, "n": len(rows), "agg": aggregate(rows)})
        per_case.append(per_case_delta(p, variant_rows["lora_only"],
                                       variant_rows["lora_with_retrieved_context"]))

    improvement = [c for c in per_case if c["verdict"] == "rag_improvement"]
    regression = [c for c in per_case if c["verdict"] == "rag_regression"]
    headroom_cases = [c for c in per_case if c["headroom"]]
    # normalized residual 잔여(>0)면 semantic judge 필요 후보(하니스 단독 단정 안 함)
    judge_candidates = sorted({
        r["caseId"] for r in raw_records
        if r["agg"].get("hallucinated_skill_normalized_residual", 0) > 0
    })

    print(f"=== R2b A/B (hard cases) (mock={a.mock} model={a.model} repeat={a.repeat} cases={len(pairs)}) ===")
    for v in VARIANTS:
        print(f"[{v}] {aggregate(by_variant[v])}")
    print(f"[per-case] headroom(A 위반>0)={len(headroom_cases)}/{len(per_case)} "
          f"rag_improvement={len(improvement)} rag_regression={len(regression)} "
          f"neutral={len(per_case) - len(improvement) - len(regression)}")
    if improvement:
        print(f"  improvement: {[c['caseId'] for c in improvement]}")
    if regression:
        print(f"  regression : {[c['caseId'] for c in regression]}")
    print(f"[semantic] normalized residual>0 (judge 필요 후보): {judge_candidates or '없음'}")

    if a.out_dir:
        os.makedirs(a.out_dir, exist_ok=True)
        out = os.path.join(a.out_dir, "rag_r2b_hardcase_ab_raw.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump({
                "mock": bool(a.mock), "model": a.model, "repeat": a.repeat, "cases": len(pairs),
                "summary": {v: aggregate(by_variant[v]) for v in VARIANTS},
                "perCase": per_case,
                "perCaseVariant": raw_records,
                "ragImprovementCases": [c["caseId"] for c in improvement],
                "ragRegressionCases": [c["caseId"] for c in regression],
                "headroomCases": [c["caseId"] for c in headroom_cases],
                "semanticJudgeCandidates": judge_candidates,
            }, f, ensure_ascii=False, indent=2)
        print(f"  raw → {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

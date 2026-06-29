"""R2c A/B/C 비교 — LoRA only vs +current RAG vs +scoped/guarded RAG (reports/56).

R2b 에서 RAG(B)가 E1 grounding 을 악화시킨 원인은 모델이 retrievedContext 의 직무요구/catalog 정의를
'지원자 보유'로 혼동(conflation)한 것이다. C 변형(scoped)은 항목에 역할/주장정책을 달고 프롬프트 가드를
추가해 그 혼동을 직접 겨냥한다. 이 스크립트는 A/B/C 를 같은 입력으로 돌려 **C 가 B 대비 E1·conflation 을
줄이는지** 본다.

**중복 구현 금지** — 모델 호출(_call_ollama)·mock(_mock_output)·overlap(_ctx_support)·집계(aggregate)는
compare_lora_with_rag 를 재사용하고, 채점은 eval_fit_model.evaluate, conflation 만 신규 추가한다.
점수/applyDecision 은 입력값 그대로(이 스크립트가 생성·변경하지 않음).

모드: --mock(Ollama 없이 배선검증; ctx 무시라 A==B==C, conflation 0) / --base-url·--model(실측).
raw 는 --out-dir(CareerTunerAI results)에만, main repo 미커밋.
"""
import argparse
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))  # ml/.../scripts
from _io_utils import configure_stdout_utf8  # noqa: E402
from build_rag_scoped_context import build_abc_pairs, role_for, SCOPED_VARIANT  # noqa: E402
# 모델 호출·mock·overlap·집계 재사용(중복 구현 금지)
from compare_lora_with_rag import _call_ollama, _mock_output, _ctx_support, aggregate  # noqa: E402
from eval_fit_model import (  # noqa: E402  (채점·grounding 1차 로직 재사용)
    evaluate, extract_json_span, collect_possession_text, term_in_text,
    _grounding_violation_in_sentence, GROUNDING_SPLIT_RE,
)

VARIANTS = ["lora_only", "lora_with_retrieved_context", SCOPED_VARIANT]
CONF_KEYS = ["job_requirement_as_user_owned", "catalog_fact_as_user_owned",
             "context_conflation", "other_grounding_claim"]


def _norm(s):
    return re.sub(r"\s+", "", str(s or "")).lower()


def _ctx_role(item):
    """항목의 contextRole(C 변형은 명시, B 는 sourceType 에서 파생)."""
    return item.get("contextRole") or role_for(item.get("sourceType"))[0]


def _claimed_possessed(poss_text, skill):
    """출력 보유텍스트(fitSummary+strengths)에서 skill 을 '보유'로 서술했는지 — eval_fit_model 의
    문장 단위 grounding 판정 재사용(보유표현 有 + 결핍/부정 無 + skill 언급)."""
    if not str(skill or "").strip():
        return False
    for sent in GROUNDING_SPLIT_RE.split(poss_text or ""):
        if _grounding_violation_in_sentence(sent, [skill], "conflation"):
            return True
    return False


def detect_conflation(content, case, ctx_items):
    """모델 출력에서 '보유 안 한 직무요구/catalog 스킬을 보유로 서술'한 conflation 을 카운트.

    job_requirement_as_user_owned : not-owned 스킬이 job_requirement 역할 ctx 에 있고 보유로 서술됨
    catalog_fact_as_user_owned    : not-owned 스킬이 catalog_fact 역할 ctx 에 있고 보유로 서술됨
    context_conflation            : 위 둘의 합(ctx 가 유발한 보유 혼동)
    other_grounding_claim         : not-owned 보유 서술이나 ctx 근거 아님(예: A 변형의 순수 over-claim)
    """
    try:
        parsed = json.loads(extract_json_span(content))
        if not isinstance(parsed, dict):
            parsed = {}
    except Exception:  # noqa: BLE001 — 비JSON 출력이면 conflation 0
        parsed = {}
    poss = collect_possession_text(parsed)
    inp = case.get("input") or {}
    owned = {_norm(s) for s in (list(inp.get("matchedSkills") or [])
                                + list(inp.get("profileSkills") or [])
                                + list(inp.get("profileCertificates") or []))}
    universe = set((case.get("expected", {}).get("allowedSkills") or [])
                   + list(inp.get("missingSkills") or [])
                   + list(inp.get("missingRequiredSkills") or [])
                   + list(inp.get("requiredSkills") or []))
    not_owned = [s for s in universe if _norm(s) not in owned]
    jobreq = " ".join(i.get("text", "") for i in (ctx_items or []) if _ctx_role(i) == "job_requirement")
    catalog = " ".join(i.get("text", "") for i in (ctx_items or []) if _ctx_role(i) == "catalog_fact")
    jr = cf = other = 0
    for skill in not_owned:
        if not _claimed_possessed(poss, skill):
            continue
        if jobreq and term_in_text(jobreq, skill):
            jr += 1
        elif catalog and term_in_text(catalog, skill):
            cf += 1
        else:
            other += 1
    return {"job_requirement_as_user_owned": jr, "catalog_fact_as_user_owned": cf,
            "context_conflation": jr + cf, "other_grounding_claim": other}


def run_variant(pair, variant, *, mock, base_url, model, timeout, repeat):
    """변형 실행 — 호출/채점/overlap 재사용 + conflation 부가."""
    v = pair["variants"][variant]
    case = {"id": pair["caseId"], "input": v["input"], "expected": pair["expected"]}
    ctx = v["input"].get("retrievedContext") or []
    msgs = v["messages"]
    rows = []
    for _ in range(repeat):
        content, dt = (_mock_output(case), 0.0) if mock else _call_ollama(base_url, model, msgs, timeout)
        row = evaluate(case, content, None)
        row["latency_ms"] = dt
        row["rag"] = _ctx_support(content, ctx)
        row["conflation"] = detect_conflation(content, case, ctx)
        rows.append(row)
    return rows


def aggregate_conflation(rows):
    return {k: sum((r.get("conflation") or {}).get(k, 0) for r in rows) for k in CONF_KEYS}


def _case_e1(rows):
    return sum(1 for r in rows if r.get("grounding_violation"))


def _case_conflation(rows):
    return sum((r.get("conflation") or {}).get("context_conflation", 0) for r in rows)


def per_case_scoped(pair, vr):
    """per-case C vs B / C vs A. 개선신호 = E1 + conflation 합. C<B → scoped_improvement."""
    e1 = {v: _case_e1(vr[v]) for v in VARIANTS}
    conf = {v: _case_conflation(vr[v]) for v in VARIANTS}
    score = {v: e1[v] + conf[v] for v in VARIANTS}
    c_vs_b = score[SCOPED_VARIANT] - score["lora_with_retrieved_context"]   # 음수 = C 가 B 보다 개선
    c_vs_a = score[SCOPED_VARIANT] - score["lora_only"]                     # 양수 = C 가 A 보다 악화(가드 실패)
    verdict = "scoped_improvement" if c_vs_b < 0 else ("scoped_regression" if c_vs_b > 0 else "neutral")
    return {"caseId": pair["caseId"], "hardType": pair.get("hardType"),
            "e1_by_variant": e1, "conflation_by_variant": conf,
            "c_vs_b": c_vs_b, "c_vs_a_guarded": c_vs_a, "verdict": verdict}


def main(argv=None):
    configure_stdout_utf8()
    ap = argparse.ArgumentParser(description="R2c A/B/C — LoRA / +RAG / +scoped RAG, conflation 측정")
    ap.add_argument("--mock", action="store_true", help="Ollama 없이 결정론 mock(배선검증; ctx 무시 A==B==C)")
    ap.add_argument("--base-url", default="http://localhost:11434/v1")
    ap.add_argument("--model", default="careertuner-c-career-strategy-3b")
    ap.add_argument("--timeout", type=int, default=240)
    ap.add_argument("--repeat", type=int, default=2)
    ap.add_argument("--out-dir", help="raw 저장(미지정 시 저장 안 함). CareerTunerAI results 권장.")
    a = ap.parse_args(argv)

    pairs = build_abc_pairs()
    by_variant = {v: [] for v in VARIANTS}
    per_case, raw_records = [], []
    for p in pairs:
        vr = {}
        for v in VARIANTS:
            rows = run_variant(p, v, mock=a.mock, base_url=a.base_url, model=a.model,
                               timeout=a.timeout, repeat=a.repeat)
            vr[v] = rows
            by_variant[v] += rows
            raw_records.append({"caseId": p["caseId"], "hardType": p.get("hardType"), "variant": v,
                                "n": len(rows), "agg": aggregate(rows, mock=a.mock),
                                "conflation": aggregate_conflation(rows)})
        per_case.append(per_case_scoped(p, vr))

    improvement = [c for c in per_case if c["verdict"] == "scoped_improvement"]
    regression = [c for c in per_case if c["verdict"] == "scoped_regression"]
    guarded_regression = [c for c in per_case if c["c_vs_a_guarded"] > 0]

    print(f"=== R2c A/B/C (mock={a.mock} model={a.model} repeat={a.repeat} cases={len(pairs)}) ===")
    if a.mock:
        print("  ⚠ mock 모드 — ctx 무시라 A==B==C, conflation 0. RAG/scoped 효과는 실측에서만(배선검증 전용).")
    for v in VARIANTS:
        print(f"[{v}] {aggregate(by_variant[v], mock=a.mock)}")
        print(f"   conflation: {aggregate_conflation(by_variant[v])}")
    print(f"[per-case C vs B] scoped_improvement={len(improvement)} scoped_regression={len(regression)} "
          f"neutral={len(per_case) - len(improvement) - len(regression)}")
    print(f"  guarded_context_regression(C가 A보다 악화)={len(guarded_regression)}건: {[c['caseId'] for c in guarded_regression] or '없음'}")
    if improvement:
        print(f"  improvement: {[c['caseId'] for c in improvement]}")
    if regression:
        print(f"  regression : {[c['caseId'] for c in regression]}")

    if a.out_dir:
        os.makedirs(a.out_dir, exist_ok=True)
        out = os.path.join(a.out_dir, "rag_r2c_scoped_ab_raw.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump({
                "mock": bool(a.mock), "rag_metrics_valid": (not a.mock),
                "model": a.model, "repeat": a.repeat, "cases": len(pairs), "variants": VARIANTS,
                "summary": {v: aggregate(by_variant[v], mock=a.mock) for v in VARIANTS},
                "conflation": {v: aggregate_conflation(by_variant[v]) for v in VARIANTS},
                "perCase": per_case, "perCaseVariant": raw_records,
                "scopedImprovementCases": [c["caseId"] for c in improvement],
                "scopedRegressionCases": [c["caseId"] for c in regression],
                "guardedContextRegressionCases": [c["caseId"] for c in guarded_regression],
            }, f, ensure_ascii=False, indent=2)
        print(f"  raw → {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

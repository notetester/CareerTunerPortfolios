"""R2d A/B/C/D 비교 + evidence audit (reports/57).

R2c 에서 프롬프트 가드가 3B 의 conflation 을 못 줄인 걸 확인했다. R2d 는 (1)근거를 evidence bucket 으로
물리 분리한 D 변형을 추가하고 (2)출력을 **evidence audit**(결정론 사후 검증)으로 'userEvidence 가 뒷받침하지
않는 보유 claim'을 잡는다. audit 은 모델 출력을 바꾸지 않고 측정만 한다(서버측 gate 의 프록시).

**중복 구현 금지** — 모델 호출/mock/overlap/집계/채점/conflation 은 compare_lora_with_rag·
compare_lora_with_scoped_rag·eval_fit_model 재사용. evidence audit 만 신규. 점수/판단 불변.
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))  # ml/.../scripts
from _io_utils import configure_stdout_utf8  # noqa: E402
from build_rag_evidence_buckets import build_abcd_pairs, to_evidence_buckets, BUCKET_KEYS, EVIDENCE_VARIANT  # noqa: E402
from compare_lora_with_rag import _call_ollama, _mock_output, _ctx_support, aggregate  # noqa: E402
from compare_lora_with_scoped_rag import _norm, _claimed_possessed, detect_conflation, aggregate_conflation  # noqa: E402
from eval_fit_model import evaluate, extract_json_span, collect_possession_text, term_in_text  # noqa: E402

VARIANTS = ["lora_only", "lora_with_retrieved_context", "lora_with_scoped_context", EVIDENCE_VARIANT]
EVID_KEYS = ["claimed_count", "unsupported_user_owned_claim_count",
             "requirement_as_owned_count", "catalog_as_owned_count", "evidence_gate_violation_count"]


def _skill_universe(case):
    inp = case.get("input") or {}
    return set((case.get("expected", {}).get("allowedSkills") or [])
               + list(inp.get("missingSkills") or []) + list(inp.get("missingRequiredSkills") or [])
               + list(inp.get("requiredSkills") or []))


def _bucket_text(buckets, key):
    return " ".join(i.get("text", "") for i in (buckets.get(key) or []))


def _parsed(content):
    try:
        p = json.loads(extract_json_span(content))
        return p if isinstance(p, dict) else {}
    except Exception:  # noqa: BLE001
        return {}


def normalize_skill_claims(claims):
    return {_norm(c) for c in claims}


def extract_user_owned_claims(content, case):
    """모델 출력에서 universe 스킬 중 '지원자 보유'로 서술된 것(eval_fit_model grounding 재사용)."""
    poss = collect_possession_text(_parsed(content))
    return [s for s in _skill_universe(case) if _claimed_possessed(poss, s)]


def allowed_user_owned_set(buckets, case):
    """보유 주장이 허용되는 스킬 집합(_norm): matchedSkills/profile/certs + userEvidence 텍스트가 뒷받침하는 것."""
    inp = case.get("input") or {}
    allowed = {_norm(s) for s in (list(inp.get("matchedSkills") or [])
                                  + list(inp.get("profileSkills") or [])
                                  + list(inp.get("profileCertificates") or []))}
    ue = _bucket_text(buckets, "userEvidence")
    if ue:
        for s in _skill_universe(case):
            if term_in_text(ue, s):
                allowed.add(_norm(s))
    return allowed


def detect_unsupported_user_owned_claims(content, case, buckets):
    """userEvidence 가 뒷받침하지 않는 보유 claim(= evidence gate 위반)."""
    allowed = allowed_user_owned_set(buckets, case)
    return [s for s in extract_user_owned_claims(content, case) if _norm(s) not in allowed]


def detect_requirement_as_owned(content, case, buckets):
    jr = _bucket_text(buckets, "jobRequirements")
    return [s for s in detect_unsupported_user_owned_claims(content, case, buckets)
            if jr and term_in_text(jr, s)]


def detect_catalog_fact_as_owned(content, case, buckets):
    cat = _bucket_text(buckets, "catalogFacts")
    return [s for s in detect_unsupported_user_owned_claims(content, case, buckets)
            if cat and term_in_text(cat, s)]


def evidence_audit(content, case, buckets):
    claimed = extract_user_owned_claims(content, case)
    unsup = detect_unsupported_user_owned_claims(content, case, buckets)
    req = detect_requirement_as_owned(content, case, buckets)
    cat = detect_catalog_fact_as_owned(content, case, buckets)
    return {"claimed_count": len(claimed),
            "unsupported_user_owned_claim_count": len(unsup),
            "requirement_as_owned_count": len(req),
            "catalog_as_owned_count": len(cat),
            "evidence_gate_violation_count": len(unsup)}


def _variant_buckets(variant_input):
    """변형 입력에서 evidence buckets 획득(D 는 보유, A/B/C 는 retrievedContext 에서 파생 — 비교 일관성)."""
    if "evidenceBuckets" in variant_input:
        return variant_input["evidenceBuckets"]
    return to_evidence_buckets(variant_input.get("retrievedContext") or [], variant_input)


def _flatten_buckets(buckets):
    """conflation 검출기용으로 버킷을 ctx 항목 리스트로 평탄화(sourceType 보존 → 역할 파생)."""
    return [i for k in BUCKET_KEYS for i in (buckets.get(k) or [])]


def run_variant(pair, variant, *, mock, base_url, model, timeout, repeat):
    v = pair["variants"][variant]
    case = {"id": pair["caseId"], "input": v["input"], "expected": pair["expected"]}
    buckets = _variant_buckets(v["input"])
    ctx_overlap = v["input"].get("retrievedContext") or _flatten_buckets(buckets)
    msgs = v["messages"]
    rows = []
    for _ in range(repeat):
        content, dt = (_mock_output(case), 0.0) if mock else _call_ollama(base_url, model, msgs, timeout)
        row = evaluate(case, content, None)
        row["latency_ms"] = dt
        row["rag"] = _ctx_support(content, ctx_overlap)
        row["conflation"] = detect_conflation(content, case, _flatten_buckets(buckets))
        row["evidence"] = evidence_audit(content, case, buckets)
        rows.append(row)
    return rows


def aggregate_evidence(rows):
    agg = {k: sum((r.get("evidence") or {}).get(k, 0) for r in rows) for k in EVID_KEYS}
    claimed = agg["claimed_count"]
    agg["evidence_gate_pass_rate"] = round(1 - agg["evidence_gate_violation_count"] / claimed, 4) if claimed else 1.0
    return agg


def _case_metric(rows):
    e1 = sum(1 for r in rows if r.get("grounding_violation"))
    gate = sum((r.get("evidence") or {}).get("evidence_gate_violation_count", 0) for r in rows)
    return e1 + gate


def main(argv=None):
    configure_stdout_utf8()
    ap = argparse.ArgumentParser(description="R2d A/B/C/D — LoRA / +RAG / +scoped / +evidence-gated, evidence audit")
    ap.add_argument("--mock", action="store_true")
    ap.add_argument("--base-url", default="http://localhost:11434/v1")
    ap.add_argument("--model", default="careertuner-c-career-strategy-3b")
    ap.add_argument("--timeout", type=int, default=240)
    ap.add_argument("--repeat", type=int, default=2)
    ap.add_argument("--out-dir", help="raw 저장(CareerTunerAI results 권장, main repo 미커밋).")
    a = ap.parse_args(argv)

    pairs = build_abcd_pairs()
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
                                "conflation": aggregate_conflation(rows), "evidence": aggregate_evidence(rows)})
        # per-case: D vs B/C on (E1 + evidence gate violation)
        D = EVIDENCE_VARIANT
        score = {v: _case_metric(vr[v]) for v in VARIANTS}
        per_case.append({"caseId": p["caseId"], "hardType": p.get("hardType"),
                         "score_by_variant": score,
                         "d_vs_b": score[D] - score["lora_with_retrieved_context"],
                         "d_vs_c": score[D] - score["lora_with_scoped_context"],
                         "d_vs_a_gate": score[D] - score["lora_only"]})

    d_improve_vs_b = [c for c in per_case if c["d_vs_b"] < 0]
    d_regress_vs_b = [c for c in per_case if c["d_vs_b"] > 0]
    d_regress_vs_a = [c for c in per_case if c["d_vs_a_gate"] > 0]

    print(f"=== R2d A/B/C/D (mock={a.mock} model={a.model} repeat={a.repeat} cases={len(pairs)}) ===")
    if a.mock:
        print("  ⚠ mock 모드 — ctx 무시라 변형 간 동일, evidence/conflation 정보가치 없음(배선검증 전용).")
    for v in VARIANTS:
        print(f"[{v}] {aggregate(by_variant[v], mock=a.mock)}")
        print(f"   conflation: {aggregate_conflation(by_variant[v])}")
        print(f"   evidence:   {aggregate_evidence(by_variant[v])}")
    print(f"[per-case D vs B] improvement={len(d_improve_vs_b)} regression={len(d_regress_vs_b)} "
          f"neutral={len(per_case) - len(d_improve_vs_b) - len(d_regress_vs_b)}")
    print(f"  D가 A보다 악화(gate)={len(d_regress_vs_a)}건")

    if a.out_dir:
        os.makedirs(a.out_dir, exist_ok=True)
        out = os.path.join(a.out_dir, "rag_r2d_evidence_gate_raw.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump({
                "mock": bool(a.mock), "rag_metrics_valid": (not a.mock),
                "model": a.model, "repeat": a.repeat, "cases": len(pairs), "variants": VARIANTS,
                "summary": {v: aggregate(by_variant[v], mock=a.mock) for v in VARIANTS},
                "conflation": {v: aggregate_conflation(by_variant[v]) for v in VARIANTS},
                "evidence": {v: aggregate_evidence(by_variant[v]) for v in VARIANTS},
                "perCase": per_case, "perCaseVariant": raw_records,
                "dImprovementVsBCases": [c["caseId"] for c in d_improve_vs_b],
                "dRegressionVsBCases": [c["caseId"] for c in d_regress_vs_b],
            }, f, ensure_ascii=False, indent=2)
        print(f"  raw → {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""R2f output-capture evidence gate end-to-end (reports/59).

R2e 한계: R2d raw 에 모델 출력 텍스트가 없어 gate(reject/review/rewrite)를 실제 출력에 적용·검증 못 했다.
R2f 는 D(evidence-gated) 변형을 최소 호출로 돌려 **실제 출력 텍스트(rawText)를 저장**하고, 각 출력에
세 gate 를 모두 적용해 reject/review/rewrite 가 실제로 안전하게 동작하는지 본다. **rewrite 후 재-audit** 으로
violation 이 진짜 0 이 되는지(구성적 아니라 실측) 확인한다.

**중복 구현 금지** — D pair/메시지는 build_rag_evidence_buckets, 모델 호출/audit 은 compare_*, gate 는
apply_evidence_gate_filter, 채점은 eval_fit_model.evaluate 재사용. rawText 는 CareerTunerAI 에만(main repo 금지).
"""
import argparse
import copy
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))  # ml/.../scripts
from _io_utils import configure_stdout_utf8  # noqa: E402
from build_rag_evidence_buckets import build_abcd_pairs, EVIDENCE_VARIANT  # noqa: E402
from compare_lora_with_rag import _call_ollama, _mock_output  # noqa: E402
from compare_lora_with_evidence_gated_rag import evidence_audit, _variant_buckets  # noqa: E402
from apply_evidence_gate_filter import apply_gate  # noqa: E402
from eval_fit_model import evaluate  # noqa: E402

EVAL_KEYS = ("success", "json_ok", "required_ok", "grounding_violation", "cjk_leak", "failure")


def _hash(obj):
    return hashlib.sha256(json.dumps(obj, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()[:16]


def _eval_compact(case, content):
    try:
        row = evaluate(case, content, None)
    except Exception as e:  # noqa: BLE001
        return {"error": type(e).__name__}
    return {k: row.get(k) for k in EVAL_KEYS}


def _mutation(original, filtered):
    """gate 후 점수/판단/스킬필드 변경 수(0이어야 함)."""
    if not isinstance(filtered, dict):
        return 0
    return sum(1 for k in ("fitScore", "applyDecision", "matchedSkills", "missingSkills")
               if (original or {}).get(k) != filtered.get(k))


def capture(variant, *, mock, base_url, model, timeout, repeat):
    pairs = build_abcd_pairs()
    records = []
    for p in pairs:
        v = p["variants"][variant]
        case = {"id": p["caseId"], "input": v["input"], "expected": p["expected"]}
        buckets = _variant_buckets(v["input"])
        msgs = v["messages"]
        for rep in range(repeat):
            content, dt = (_mock_output(case), 0.0) if mock else _call_ollama(base_url, model, msgs, timeout)
            try:
                parsed = json.loads(content[content.find("{"):content.rfind("}") + 1])
                parse_status = "ok" if isinstance(parsed, dict) else "not_object"
                if not isinstance(parsed, dict):
                    parsed = None
            except Exception:  # noqa: BLE001
                parsed, parse_status = None, "parse_fail"
            audit = evidence_audit(content, case, buckets)
            g_review = apply_gate(content, case, buckets, "review")
            g_reject = apply_gate(content, case, buckets, "reject")
            g_rewrite = apply_gate(content, case, buckets, "rewrite")
            # rewrite 후 재-audit(실측: violation 진짜 0 되는지) + 점수/판단 mutation
            rewrite_out = g_rewrite.get("filteredOutput")
            audit_after = (evidence_audit(json.dumps(rewrite_out, ensure_ascii=False), case, buckets)
                           if isinstance(rewrite_out, dict) else None)
            records.append({
                "caseId": p["caseId"], "hardType": p.get("hardType"), "repeat": rep, "variant": variant,
                "inputHash": _hash(v["input"]), "messagesHash": _hash(msgs),
                "rawText": content, "parsedJson": parsed, "parseStatus": parse_status,
                "evidenceAudit": audit,
                "gateReview": {k: g_review[k] for k in ("gateStatus", "needsHumanReview", "gateReasons")},
                "gateReject": {k: g_reject[k] for k in ("gateStatus", "needsHumanReview", "gateReasons")},
                "gateRewrite": {"gateStatus": g_rewrite["gateStatus"], "rewriteCount": g_rewrite.get("rewriteCount", 0),
                                "filteredOutput": rewrite_out},
                "evidenceAuditAfterRewrite": audit_after,
                "evaluationBefore": _eval_compact(case, content),
                "evaluationAfterRewrite": (_eval_compact(case, json.dumps(rewrite_out, ensure_ascii=False))
                                           if isinstance(rewrite_out, dict) else None),
                "scoreDecisionMutation": _mutation(parsed, rewrite_out),
                "latency_ms": dt,
            })
    return records


def main(argv=None):
    configure_stdout_utf8()
    ap = argparse.ArgumentParser(description="R2f output capture + gate end-to-end (D variant)")
    ap.add_argument("--mock", action="store_true")
    ap.add_argument("--base-url", default="http://localhost:11434/v1")
    ap.add_argument("--model", default="careertuner-c-career-strategy-3b")
    ap.add_argument("--timeout", type=int, default=240)
    ap.add_argument("--repeat", type=int, default=2)
    ap.add_argument("--variant", default=EVIDENCE_VARIANT, help="기본 D(evidence-gated). A 는 lora_only.")
    ap.add_argument("--out-dir", help="rawText 포함 — CareerTunerAI results 만(main repo 금지).")
    a = ap.parse_args(argv)

    recs = capture(a.variant, mock=a.mock, base_url=a.base_url, model=a.model, timeout=a.timeout, repeat=a.repeat)
    # 콘솔 요약(rawText 미출력)
    st = {}
    for r in recs:
        st[r["gateReject"]["gateStatus"]] = st.get(r["gateReject"]["gateStatus"], 0) + 1
    mut = sum(r["scoreDecisionMutation"] for r in recs)
    rw = [r for r in recs if r["gateRewrite"]["gateStatus"] == "REWRITTEN"]
    rw_resid0 = sum(1 for r in rw if (r.get("evidenceAuditAfterRewrite") or {}).get("evidence_gate_violation_count", 1) == 0)
    print(f"=== R2f capture (mock={a.mock} variant={a.variant} repeat={a.repeat} n={len(recs)}) ===")
    print(f"  reject status dist: {st}")
    print(f"  rewrite={len(rw)} 중 재-audit violation 0: {rw_resid0}/{len(rw)} | 점수/판단 mutation 합: {mut}")
    if a.out_dir:
        os.makedirs(a.out_dir, exist_ok=True)
        out = os.path.join(a.out_dir, "r2f_output_capture.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump({"mock": bool(a.mock), "model": a.model, "variant": a.variant, "repeat": a.repeat,
                       "n": len(recs), "records": recs}, f, ensure_ascii=False, indent=2)
        print(f"  raw(rawText 포함) → {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

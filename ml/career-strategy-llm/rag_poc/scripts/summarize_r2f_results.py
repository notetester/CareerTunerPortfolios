"""R2f 결과 요약 — 캡처한 실제 출력에 gate 를 적용한 end-to-end 효과(reports/59).

D_raw vs E_review/E_reject/E_rewrite. **rewrite 는 재-audit(filteredOutput 을 다시 audit)으로 실측 잔여
violation 을 본다**(R2e 의 구성적 0 이 아니라, 실제 치환된 텍스트가 정말 위반을 없앴는지). 점수/판단 mutation,
계약 유지, parse fail 처리도 집계. rawText 원문은 출력하지 않고 짧은 truncated 샘플만(보고서 안전).

실행: python rag_poc/scripts/summarize_r2f_results.py <r2f_output_capture.json> [--samples N]
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from _io_utils import configure_stdout_utf8  # noqa: E402

SAMPLE_MAX = 90  # 보고서 샘플 truncate 길이(원문 복붙 방지)


def _trunc(s):
    s = " ".join(str(s or "").split())
    return s[:SAMPLE_MAX] + ("…" if len(s) > SAMPLE_MAX else "")


def analyze(data, n_samples=5):
    recs = data.get("records") or []
    n = len(recs)
    reject_dist, review_flagged, parse_fail = {}, 0, 0
    viol_before = viol_after_rw = rewritten = rw_resid0 = mut = contract_ok_rw = 0
    samples = []
    for r in recs:
        st = r["gateReject"]["gateStatus"]
        reject_dist[st] = reject_dist.get(st, 0) + 1
        if r["gateReview"]["needsHumanReview"]:
            review_flagged += 1
        if r.get("parseStatus") == "parse_fail":
            parse_fail += 1
        viol_before += (r.get("evidenceAudit") or {}).get("evidence_gate_violation_count", 0)
        mut += r.get("scoreDecisionMutation", 0)
        if r["gateRewrite"]["gateStatus"] == "REWRITTEN":
            rewritten += 1
            resid = (r.get("evidenceAuditAfterRewrite") or {}).get("evidence_gate_violation_count", 0)
            viol_after_rw += resid
            if resid == 0:
                rw_resid0 += 1
            if (r.get("evaluationAfterRewrite") or {}).get("json_ok") and (r.get("evaluationAfterRewrite") or {}).get("required_ok"):
                contract_ok_rw += 1
            if len(samples) < n_samples:
                before = (r.get("parsedJson") or {}).get("fitSummary", "")
                after = (r["gateRewrite"].get("filteredOutput") or {}).get("fitSummary", "")
                samples.append({"caseId": r["caseId"], "before": _trunc(before), "after": _trunc(after),
                                "residual_violation": resid})
    return {
        "n": n, "reject_status_dist": reject_dist, "review_flagged": review_flagged, "parse_fail": parse_fail,
        "violation_before": viol_before, "rewritten": rewritten,
        "violation_after_rewrite": viol_after_rw, "rewrite_residual_zero": rw_resid0,
        "score_decision_mutation": mut, "rewrite_contract_ok": contract_ok_rw, "samples": samples,
    }


def render(a):
    L = [f"# R2f 요약 (n={a['n']})", "",
         f"- reject status: {a['reject_status_dist']}",
         f"- review needsHumanReview: {a['review_flagged']}/{a['n']}",
         f"- parse_fail: {a['parse_fail']}",
         f"- evidence_gate_violation before(원본): {a['violation_before']}",
         f"- rewrite 적용: {a['rewritten']}건, 재-audit 후 잔여 violation 합: {a['violation_after_rewrite']} "
         f"(잔여 0 달성: {a['rewrite_residual_zero']}/{a['rewritten']})",
         f"- rewrite 후 계약 유지(json_ok+required_ok): {a['rewrite_contract_ok']}/{a['rewritten']}",
         f"- 점수/applyDecision mutation 합: {a['score_decision_mutation']}",
         "", "## rewrite 샘플(truncated, 가독성 검토용)"]
    for s in a["samples"]:
        L.append(f"- [{s['caseId']}] 잔여{s['residual_violation']} | before: {s['before']}")
        L.append(f"      after : {s['after']}")
    return "\n".join(L)


def main():
    configure_stdout_utf8()
    ap = argparse.ArgumentParser(description="R2f 결과 요약(rawText 미출력)")
    ap.add_argument("capture")
    ap.add_argument("--samples", type=int, default=5)
    ap.add_argument("--md")
    a = ap.parse_args()
    if not os.path.exists(a.capture):
        print(f"capture 없음: {a.capture}", file=sys.stderr)
        return 2
    with open(a.capture, encoding="utf-8") as f:
        data = json.load(f)
    out = render(analyze(data, a.samples))
    if a.md:
        with open(a.md, "w", encoding="utf-8") as f:
            f.write(out + "\n")
        print(f"요약 → {a.md}")
    else:
        print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

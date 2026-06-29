"""R2e evidence gate post-filter 테스트 (reports/58).

실행: python rag_poc/tests/test_evidence_gate_filter.py
핵심: reject/review/rewrite 동작 · userEvidence 외 출처 불허 · 점수/판단 불변 · parse 실패 미은폐 ·
filteredOutput 비파괴 · evaluate 계약 유지.
"""
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))
from apply_evidence_gate_filter import apply_gate, gate_reasons, gate_summary  # noqa: E402
from build_rag_evidence_buckets import to_evidence_buckets  # noqa: E402
from eval_fit_model import evaluate  # noqa: E402

CASE = {"input": {"matchedSkills": ["SQL"], "missingSkills": ["Spark", "자료구조"]},
        "expected": {"allowedSkills": ["SQL", "Spark", "자료구조"]}}
JOBREQ = to_evidence_buckets([{"sourceType": "job_posting", "sourceId": "j", "text": "Spark 역량을 요구합니다."}], CASE["input"])
CATALOG = to_evidence_buckets([{"sourceType": "certification_catalog", "sourceId": "c", "text": "정보처리기사는 자료구조 등을 검증합니다."}], CASE["input"])
USEREV = to_evidence_buckets([{"sourceType": "user_profile_summary", "sourceId": "p", "text": "지원자는 Spark 경험이 있습니다."}], CASE["input"])


def _content(fit, strengths=None, extra=None):
    o = {"fitSummary": fit, "strengths": strengths or [], "risks": [], "strategyActions": [], "learningTaskReasons": []}
    if extra:
        o.update(extra)
    return json.dumps(o, ensure_ascii=False)


class GateFilterTest(unittest.TestCase):
    def test_1_reject_mode(self):
        r = apply_gate(_content("지원자는 Spark 역량을 보유하고 있습니다."), CASE, JOBREQ, "reject")
        self.assertEqual("REJECTED", r["gateStatus"])
        self.assertTrue(r["needsHumanReview"])
        self.assertTrue(any(g["claim"] == "Spark" for g in r["gateReasons"]))

    def test_2_review_mode(self):
        r = apply_gate(_content("지원자는 Spark 역량을 보유하고 있습니다."), CASE, JOBREQ, "review")
        self.assertEqual("REVIEW_REQUIRED", r["gateStatus"])
        self.assertTrue(r["needsHumanReview"])

    def test_3_rewrite_mode(self):
        r = apply_gate(_content("지원자는 Spark 역량을 보유하고 있습니다."), CASE, JOBREQ, "rewrite")
        self.assertEqual("REWRITTEN", r["gateStatus"])
        fs = r["filteredOutput"]["fitSummary"]
        self.assertTrue(("요구" in fs) or ("보완" in fs) or ("학습" in fs))
        self.assertNotIn("보유하고", fs)

    def test_4_catalog_only_flagged(self):
        r = apply_gate(_content("지원자는 자료구조 역량을 갖추고 있습니다."), CASE, CATALOG, "reject")
        self.assertEqual("REJECTED", r["gateStatus"])
        self.assertTrue(any(g["type"] == "catalog_as_owned" and g["claim"] == "자료구조" for g in r["gateReasons"]))

    def test_5_job_requirement_only_flagged(self):
        r = apply_gate(_content("지원자는 Spark 역량을 보유합니다."), CASE, JOBREQ, "reject")
        self.assertTrue(any(g["type"] == "requirement_as_owned" for g in r["gateReasons"]))

    def test_6_user_evidence_supported_passes(self):
        r = apply_gate(_content("지원자는 Spark 역량을 보유합니다."), CASE, USEREV, "reject")
        self.assertEqual("PASSED", r["gateStatus"])
        self.assertFalse(r["needsHumanReview"])

    def test_7_score_decision_unchanged(self):
        r = apply_gate(_content("지원자는 Spark 역량을 보유합니다.", extra={"fitScore": 70, "applyDecision": "HOLD"}),
                       CASE, JOBREQ, "rewrite")
        self.assertEqual(70, r["filteredOutput"]["fitScore"])
        self.assertEqual("HOLD", r["filteredOutput"]["applyDecision"])

    def test_8_parse_fail_not_hidden(self):
        r = apply_gate("이건 JSON 아님", CASE, JOBREQ, "rewrite")
        self.assertEqual("PARSE_FAIL", r["gateStatus"])
        self.assertIsNone(r["filteredOutput"])
        self.assertTrue(r["needsHumanReview"])

    def test_9_filtered_does_not_overwrite_original(self):
        r = apply_gate(_content("지원자는 Spark 역량을 보유하고 있습니다."), CASE, JOBREQ, "rewrite")
        self.assertIn("보유", r["originalOutput"]["fitSummary"])      # 원본 보존
        self.assertNotEqual(r["originalOutput"]["fitSummary"], r["filteredOutput"]["fitSummary"])

    def test_10_filtered_maintains_contract(self):
        r = apply_gate(_content("지원자는 Spark 역량을 보유합니다.", ["SQL 보유"]), CASE, JOBREQ, "rewrite")
        row = evaluate(CASE, json.dumps(r["filteredOutput"], ensure_ascii=False), None)
        self.assertTrue(row["json_ok"])
        self.assertTrue(row["required_ok"])    # 필수 키 유지

    def test_11_gate_summary_counts(self):
        results = [
            apply_gate(_content("지원자는 Spark 역량을 보유합니다."), CASE, JOBREQ, "reject"),
            apply_gate(_content("지원자는 SQL 역량을 보유합니다."), CASE, JOBREQ, "reject"),  # SQL=matched → PASS
            apply_gate("not json", CASE, JOBREQ, "reject"),
        ]
        s = gate_summary(results)
        self.assertEqual(3, s["n"])
        self.assertEqual(1, s["rejected"])
        self.assertEqual(1, s["passed"])
        self.assertEqual(1, s["parse_fail"])

    def test_12_passed_output_not_rewritten(self):
        # userEvidence/matched 뒷받침된 정당 보유는 rewrite 되지 않는다(과잉수정 금지)
        r = apply_gate(_content("지원자는 SQL 역량을 보유합니다."), CASE, JOBREQ, "rewrite")
        self.assertEqual("PASSED", r["gateStatus"])
        self.assertEqual(r["originalOutput"], r["filteredOutput"])


if __name__ == "__main__":
    unittest.main(verbosity=2)

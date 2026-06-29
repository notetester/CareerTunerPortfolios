"""R2d evidence audit 테스트 (reports/57).

실행: python rag_poc/tests/test_rag_evidence_audit.py
핵심: userEvidence 가 뒷받침 안 하는 보유 claim 을 잡고(출처별 분류), userEvidence/matched 가 뒷받침하면
통과, 결핍 서술은 오탐 없음.
"""
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from build_rag_evidence_buckets import to_evidence_buckets  # noqa: E402
from compare_lora_with_evidence_gated_rag import (  # noqa: E402
    evidence_audit, extract_user_owned_claims, detect_unsupported_user_owned_claims,
    detect_requirement_as_owned, detect_catalog_fact_as_owned, aggregate_evidence,
)


def _content(fit_summary, strengths=None):
    return json.dumps({"fitSummary": fit_summary, "strengths": strengths or [],
                       "risks": [], "strategyActions": [], "learningTaskReasons": []}, ensure_ascii=False)


# matched=SQL(보유), missing=Spark/자료구조(미보유)
CASE = {"input": {"matchedSkills": ["SQL"], "missingSkills": ["Spark", "자료구조"]},
        "expected": {"allowedSkills": ["SQL", "Spark", "자료구조"]}}
JOBREQ = to_evidence_buckets([{"sourceType": "job_posting", "sourceId": "j", "text": "Spark 역량을 요구합니다."}],
                             CASE["input"])
CATALOG = to_evidence_buckets([{"sourceType": "certification_catalog", "sourceId": "c",
                                "text": "정보처리기사는 자료구조 등을 검증합니다."}], CASE["input"])
USEREV = to_evidence_buckets([{"sourceType": "user_profile_summary", "sourceId": "p",
                               "text": "지원자는 Spark 경험이 있습니다."}], CASE["input"])


class EvidenceAuditTest(unittest.TestCase):
    def test_requirement_as_owned_detected(self):
        out = _content("지원자는 Spark 역량을 보유하고 있습니다.")
        self.assertIn("Spark", detect_requirement_as_owned(out, CASE, JOBREQ))
        a = evidence_audit(out, CASE, JOBREQ)
        self.assertEqual(1, a["requirement_as_owned_count"])
        self.assertEqual(1, a["evidence_gate_violation_count"])

    def test_catalog_fact_as_owned_detected(self):
        out = _content("지원자는 자료구조 역량을 충분히 갖추고 있습니다.")
        self.assertIn("자료구조", detect_catalog_fact_as_owned(out, CASE, CATALOG))
        a = evidence_audit(out, CASE, CATALOG)
        self.assertEqual(1, a["catalog_as_owned_count"])

    def test_matched_skill_claim_passes(self):
        # 실제 보유 SQL 을 보유로 서술하는 건 위반 아님
        out = _content("지원자는 SQL 역량을 보유하고 있습니다.")
        self.assertEqual([], detect_unsupported_user_owned_claims(out, CASE, JOBREQ))
        self.assertEqual(0, evidence_audit(out, CASE, JOBREQ)["evidence_gate_violation_count"])

    def test_user_evidence_supported_claim_passes(self):
        # userEvidence 가 Spark 를 뒷받침하면 보유 서술 허용
        out = _content("지원자는 Spark 역량을 보유하고 있습니다.")
        self.assertEqual([], detect_unsupported_user_owned_claims(out, CASE, USEREV))

    def test_lack_statement_not_flagged(self):
        out = _content("지원자는 Spark 역량이 부족하여 보완이 필요합니다.")
        self.assertEqual(0, evidence_audit(out, CASE, JOBREQ)["evidence_gate_violation_count"])

    def test_extract_claims_only_possession(self):
        out = _content("지원자는 SQL 을 보유합니다.", ["Spark 학습 추천"])
        claims = extract_user_owned_claims(out, CASE)
        self.assertIn("SQL", claims)
        self.assertNotIn("Spark", claims)   # '학습 추천'은 보유 서술 아님

    def test_malformed_no_crash(self):
        self.assertEqual(0, evidence_audit("not json", CASE, JOBREQ)["evidence_gate_violation_count"])

    def test_aggregate_pass_rate(self):
        rows = [
            {"evidence": {"claimed_count": 2, "unsupported_user_owned_claim_count": 1,
                          "requirement_as_owned_count": 1, "catalog_as_owned_count": 0,
                          "evidence_gate_violation_count": 1}},
            {"evidence": {"claimed_count": 2, "unsupported_user_owned_claim_count": 0,
                          "requirement_as_owned_count": 0, "catalog_as_owned_count": 0,
                          "evidence_gate_violation_count": 0}},
        ]
        agg = aggregate_evidence(rows)
        self.assertEqual(4, agg["claimed_count"])
        self.assertEqual(1, agg["evidence_gate_violation_count"])
        self.assertEqual(0.75, agg["evidence_gate_pass_rate"])   # 1 - 1/4


if __name__ == "__main__":
    unittest.main(verbosity=2)

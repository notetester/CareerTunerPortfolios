"""R2c conflation 지표 테스트 (reports/56).

실행: python rag_poc/tests/test_rag_conflation_metrics.py
핵심: 출력이 '보유 안 한 직무요구/catalog 스킬을 보유로 서술'하면 감지하고, 정당한 보유/결핍 서술은
오탐하지 않는다. ctx 역할(job_requirement vs catalog_fact)로 분류한다.
"""
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from compare_lora_with_scoped_rag import detect_conflation, aggregate_conflation  # noqa: E402


def _content(fit_summary, strengths=None):
    return json.dumps({"fitSummary": fit_summary, "strengths": strengths or [],
                       "risks": [], "strategyActions": [], "learningTaskReasons": []}, ensure_ascii=False)


# matched=SQL(보유), missing=Spark/자료구조(미보유). allowedSkills 로 universe 구성.
CASE = {"input": {"matchedSkills": ["SQL"], "missingSkills": ["Spark", "자료구조"]},
        "expected": {"allowedSkills": ["SQL", "Spark", "자료구조"]}}
JOBREQ_CTX = [{"sourceType": "job_posting", "contextRole": "job_requirement",
               "text": "Spark, ETL 역량이 요구됩니다."}]
CATALOG_CTX = [{"sourceType": "certification_catalog", "contextRole": "catalog_fact",
                "text": "정보처리기사는 자료구조·알고리즘 등 SW 역량을 검증합니다."}]


class ConflationMetricTest(unittest.TestCase):
    def test_job_requirement_claimed_as_owned_detected(self):
        # 미보유 Spark(직무요구 ctx)를 보유로 서술 → job_requirement_as_user_owned +1
        out = detect_conflation(_content("지원자는 Spark 역량을 보유하고 있습니다."), CASE, JOBREQ_CTX)
        self.assertEqual(1, out["job_requirement_as_user_owned"])
        self.assertEqual(0, out["catalog_fact_as_user_owned"])
        self.assertEqual(1, out["context_conflation"])

    def test_catalog_fact_claimed_as_owned_detected(self):
        # 미보유 자료구조(catalog 정의)를 보유로 서술 → catalog_fact_as_user_owned +1
        out = detect_conflation(_content("지원자는 자료구조 역량을 충분히 갖추고 있습니다."), CASE, CATALOG_CTX)
        self.assertEqual(1, out["catalog_fact_as_user_owned"])
        self.assertEqual(0, out["job_requirement_as_user_owned"])
        self.assertEqual(1, out["context_conflation"])

    def test_lack_statement_not_flagged(self):
        # 부족하다고 정직하게 서술하면 conflation 아님
        out = detect_conflation(_content("지원자는 Spark 역량이 부족하여 보완이 필요합니다."), CASE, JOBREQ_CTX)
        self.assertEqual(0, out["context_conflation"])

    def test_owned_skill_claim_not_flagged(self):
        # 실제 보유 SQL 을 보유로 서술하는 건 정당 → conflation 0
        out = detect_conflation(_content("지원자는 SQL 역량을 보유하고 있습니다."), CASE, JOBREQ_CTX)
        self.assertEqual(0, out["context_conflation"])

    def test_claim_without_context_is_other(self):
        # 미보유 보유서술이나 어떤 ctx 에도 없으면 other_grounding_claim(예: A 변형 순수 over-claim)
        out = detect_conflation(_content("지원자는 자료구조 역량을 보유하고 있습니다."), CASE, JOBREQ_CTX)
        self.assertEqual(0, out["context_conflation"])      # 자료구조는 jobreq ctx 에 없음
        self.assertEqual(1, out["other_grounding_claim"])

    def test_no_possession_text_zero(self):
        out = detect_conflation(_content("적합도를 설명합니다.", ["성실함"]), CASE, JOBREQ_CTX)
        self.assertEqual(0, out["context_conflation"])
        self.assertEqual(0, out["other_grounding_claim"])

    def test_malformed_output_no_crash(self):
        out = detect_conflation("not json at all", CASE, JOBREQ_CTX)
        self.assertEqual(0, out["context_conflation"])

    def test_aggregate_sums(self):
        rows = [
            {"conflation": {"job_requirement_as_user_owned": 1, "catalog_fact_as_user_owned": 0,
                            "context_conflation": 1, "other_grounding_claim": 0}},
            {"conflation": {"job_requirement_as_user_owned": 0, "catalog_fact_as_user_owned": 2,
                            "context_conflation": 2, "other_grounding_claim": 1}},
        ]
        agg = aggregate_conflation(rows)
        self.assertEqual(1, agg["job_requirement_as_user_owned"])
        self.assertEqual(2, agg["catalog_fact_as_user_owned"])
        self.assertEqual(3, agg["context_conflation"])
        self.assertEqual(1, agg["other_grounding_claim"])


if __name__ == "__main__":
    unittest.main(verbosity=2)

"""R2d evidence bucket / schema 테스트 (reports/57).

실행: python rag_poc/tests/test_rag_evidence_buckets.py
핵심: 버킷 분리 · userEvidence 외 출처는 보유 allowed set 진입 불가 · 점수/판단 불변 · A/B/C/D 같은 base ·
D 만 evidenceBuckets/Evidence Rules · score/vectorDistance 미주입.
"""
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from build_rag_evidence_buckets import (  # noqa: E402
    to_evidence_buckets, build_evidence_input, build_evidence_messages, build_abcd_pairs,
    BUCKET_KEYS, EVIDENCE_VARIANT,
)
from compare_lora_with_evidence_gated_rag import allowed_user_owned_set, _norm  # noqa: E402

CASE_INPUT = {
    "profileSnapshot": {"matchedSkills": ["SQL"]}, "jobPostingSummary": {"jobTitle": "데이터"},
    "fitScore": 66, "applyDecision": "COMPLEMENT_BEFORE_APPLY",
    "matchedSkills": ["SQL"], "missingSkills": ["Spark", "자료구조", "Airflow"],
}
CTX = [
    {"sourceType": "skill_catalog", "sourceId": "skill-spark", "text": "Spark는 대용량 처리 프레임워크입니다.",
     "score": 0.9, "vectorDistance": 0.1, "fitScore": 99},  # 금지/메타 키 일부러
    {"sourceType": "certification_catalog", "sourceId": "cert-info", "text": "정보처리기사는 자료구조 등을 검증합니다."},
    {"sourceType": "company_research_summary", "sourceId": "co-1", "text": "회사는 Airflow 파이프라인을 운영합니다."},
]
EXPECTED = {"allowedSkills": ["SQL", "Spark", "자료구조", "Airflow"]}
CASE = {"input": CASE_INPUT, "expected": EXPECTED}


class EvidenceBucketTest(unittest.TestCase):
    def test_1_builder_separates_buckets(self):
        b = to_evidence_buckets(CTX, CASE_INPUT)
        self.assertEqual(set(b.keys()), set(BUCKET_KEYS))
        self.assertEqual(2, len(b["catalogFacts"]))      # skill_catalog + certification_catalog
        self.assertEqual(1, len(b["companyContext"]))    # company_research_summary
        self.assertGreaterEqual(len(b["userEvidence"]), 1)  # matchedSkills 재표현

    def test_2_skill_not_in_user_evidence_not_allowed(self):
        b = to_evidence_buckets(CTX, CASE_INPUT)
        allowed = allowed_user_owned_set(b, CASE)
        self.assertIn(_norm("SQL"), allowed)              # matchedSkills → 허용
        self.assertNotIn(_norm("Spark"), allowed)         # userEvidence 미지원 → 불허
        self.assertNotIn(_norm("자료구조"), allowed)
        self.assertNotIn(_norm("Airflow"), allowed)

    def test_3_job_requirement_only_not_allowed(self):
        # Spark 를 jobRequirements 에만 둬도 보유 allowed 가 아님
        ctx = [{"sourceType": "job_posting", "sourceId": "j", "text": "Spark 역량을 요구합니다."}]
        b = to_evidence_buckets(ctx, {"matchedSkills": ["SQL"]})
        self.assertNotIn(_norm("Spark"), allowed_user_owned_set(b, CASE))

    def test_4_catalog_only_not_allowed(self):
        ctx = [{"sourceType": "skill_catalog", "sourceId": "s", "text": "Spark는 처리 엔진입니다."}]
        b = to_evidence_buckets(ctx, {"matchedSkills": ["SQL"]})
        self.assertNotIn(_norm("Spark"), allowed_user_owned_set(b, CASE))

    def test_5_company_context_only_not_allowed(self):
        ctx = [{"sourceType": "company_research_summary", "sourceId": "c", "text": "회사는 Airflow 를 씁니다."}]
        b = to_evidence_buckets(ctx, {"matchedSkills": ["SQL"]})
        self.assertNotIn(_norm("Airflow"), allowed_user_owned_set(b, CASE))

    def test_6_user_evidence_supports_claim(self):
        # userEvidence 텍스트가 뒷받침하면 보유 허용
        ctx = [{"sourceType": "user_profile_summary", "sourceId": "p", "text": "지원자는 Spark 실무 경험이 있습니다."}]
        b = to_evidence_buckets(ctx, {"matchedSkills": ["SQL"]})
        self.assertIn(_norm("Spark"), allowed_user_owned_set(b, CASE))

    def test_8_does_not_change_score_or_decision(self):
        out = build_evidence_input(CASE_INPUT, CTX)
        self.assertEqual(66, out["fitScore"])
        self.assertEqual("COMPLEMENT_BEFORE_APPLY", out["applyDecision"])
        self.assertNotIn("evidenceBuckets", CASE_INPUT)   # 원본 비파괴

    def test_9_abcd_share_same_base_input(self):
        pairs = build_abcd_pairs()
        self.assertGreaterEqual(len(pairs), 12)
        for p in pairs:
            bases = []
            for v in ("lora_only", "lora_with_retrieved_context", "lora_with_scoped_context", EVIDENCE_VARIANT):
                x = dict(p["variants"][v]["input"])
                x.pop("retrievedContext", None)
                x.pop("evidenceBuckets", None)
                bases.append(x)
            for x in bases[1:]:
                self.assertEqual(bases[0], x, f"{p['caseId']} base input 다름")

    def test_10_only_d_has_evidence_buckets(self):
        pairs = build_abcd_pairs()
        for p in pairs:
            for v in ("lora_only", "lora_with_retrieved_context", "lora_with_scoped_context"):
                self.assertNotIn("evidenceBuckets", p["variants"][v]["input"])
            self.assertIn("evidenceBuckets", p["variants"][EVIDENCE_VARIANT]["input"])

    def test_11_d_prompt_has_evidence_rules(self):
        pairs = build_abcd_pairs()
        for p in pairs:
            d_sys = p["variants"][EVIDENCE_VARIANT]["messages"][0]["content"]
            self.assertIn("Evidence Rules", d_sys)
            for other in ("lora_only", "lora_with_retrieved_context", "lora_with_scoped_context"):
                self.assertNotIn("Evidence Rules", p["variants"][other]["messages"][0]["content"])

    def test_12_score_vector_not_in_prompt(self):
        msgs = build_evidence_messages(CASE_INPUT, CTX)
        user = msgs[1]["content"]
        self.assertNotIn("vectorDistance", user)
        self.assertNotIn("\"score\"", user)
        # 버킷 항목은 sourceType/sourceId/text 만
        b = to_evidence_buckets(CTX, CASE_INPUT)
        for items in b.values():
            for it in items:
                self.assertEqual(set(it.keys()), {"sourceType", "sourceId", "text"})

    def test_13_value_level_leak_blocked(self):
        with self.assertRaises(AssertionError):
            to_evidence_buckets([{"sourceType": "skill_catalog", "sourceId": "x",
                                  "text": "메모 fitScore: 99 / applyDecision: APPLY"}], {})


if __name__ == "__main__":
    unittest.main(verbosity=2)

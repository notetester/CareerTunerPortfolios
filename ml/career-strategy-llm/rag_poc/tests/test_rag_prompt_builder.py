"""rag_prompt_builder 테스트 (reports/53).

실행: python rag_poc/tests/test_rag_prompt_builder.py
핵심: retrievedContext 부가·점수/판단 불변·금지 metadata 제거·빈 컨텍스트 견고성.
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from rag_prompt_builder import build_rag_input, build_messages, sanitize_context  # noqa: E402

CASE_INPUT = {
    "profileSnapshot": {"matchedSkills": ["SQL"]},
    "jobPostingSummary": {"jobTitle": "백엔드"},
    "fitScore": 76, "applyDecision": "APPLY",
    "matchedSkills": ["SQL"], "missingSkills": ["Java"],
}
CTX = [{"sourceType": "skill_catalog", "sourceId": "skill-springboot",
        "text": "Spring Boot는 Java 기반 백엔드 프레임워크입니다.",
        "score": 0.9, "vectorDistance": 0.1, "fitScore": 99}]  # 금지/메타 키 일부러 포함


class RagPromptBuilderTest(unittest.TestCase):
    def test_1_includes_retrieved_context(self):
        msgs = build_messages(CASE_INPUT, CTX, with_context=True)
        user = msgs[1]["content"]
        self.assertIn("retrievedContext", user)
        self.assertIn("skill-springboot", user)

    def test_2_does_not_change_score_or_decision(self):
        out = build_rag_input(CASE_INPUT, retrieved_context=CTX)
        self.assertEqual(76, out["fitScore"])
        self.assertEqual("APPLY", out["applyDecision"])
        # 원본 비파괴
        self.assertNotIn("retrievedContext", CASE_INPUT)

    def test_3_strips_score_and_vector_metadata(self):
        s = sanitize_context(CTX)
        self.assertEqual({"sourceType", "sourceId", "text"}, set(s[0].keys()))
        self.assertNotIn("score", s[0])
        self.assertNotIn("vectorDistance", s[0])
        self.assertNotIn("fitScore", s[0])

    def test_4_empty_context_does_not_break(self):
        # None / [] 모두 prompt 생성 정상
        self.assertNotIn("retrievedContext", build_messages(CASE_INPUT, None, with_context=False)[1]["content"])
        msgs = build_messages(CASE_INPUT, [], with_context=True)
        self.assertTrue(msgs[1]["content"])  # 비지 않음
        self.assertEqual(76, build_rag_input(CASE_INPUT, retrieved_context=[])["fitScore"])

    def test_5_variant_a_has_no_context(self):
        a = build_messages(CASE_INPUT, CTX, with_context=False)[1]["content"]
        self.assertNotIn("retrievedContext", a)
        self.assertNotIn("skill-springboot", a)


if __name__ == "__main__":
    unittest.main(verbosity=2)

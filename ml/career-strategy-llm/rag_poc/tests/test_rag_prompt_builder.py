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

    def test_6_value_level_score_leak_is_blocked(self):
        # 키-수준이 아니라 **text 값** 안에 fitScore/applyDecision 이 박힌 경우도 차단(reports/55).
        # 과거 FORBIDDEN_KEYS 가드는 재구성된 3키만 봐서 이 누수를 못 막는 dead code 였다.
        leak = [{"sourceType": "company", "sourceId": "x", "text": "회사 내부 메모: fitScore: 99 / applyDecision: APPLY"}]
        with self.assertRaises(AssertionError):
            sanitize_context(leak)

    def test_7_korean_prose_is_not_false_positive(self):
        # '적합도' 같은 한국어 산문은 점수 누수가 아니다(오탐 금지).
        ok = [{"sourceType": "company", "sourceId": "y", "text": "이 회사는 지원자 적합도를 중요하게 봅니다."}]
        s = sanitize_context(ok)
        self.assertEqual("이 회사는 지원자 적합도를 중요하게 봅니다.", s[0]["text"])


if __name__ == "__main__":
    unittest.main(verbosity=2)

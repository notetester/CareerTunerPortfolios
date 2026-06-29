"""retrievedContext builder 테스트 (reports/50 §9, 51).

실행: python rag_poc/tests/test_context_builder.py
핵심: builder 가 sourceType/sourceId/text 를 유지하고, **fitScore/applyDecision 을 생성하지 않는다**.
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from build_retrieved_context import (  # noqa: E402
    build_retrieved_context, FORBIDDEN_KEYS, _assert_no_score_keys, scan_text_for_score_leak,
)

SAMPLE = [
    {"chunkId": "chunk-job-a-001", "sourceType": "job_posting", "sourceId": "job-001",
     "visibility": "application_private", "score": 0.82, "text": "백엔드 개발자는 Java, Spring Boot 역량을 요구합니다."},
    {"chunkId": "chunk-cert-sqld", "sourceType": "certification_catalog", "sourceId": "cert-sqld",
     "visibility": "global", "score": 0.5, "text": "SQLD는 SQL 기본 이해와 데이터 모델링 역량을 검증합니다."},
]


class ContextBuilderTest(unittest.TestCase):
    def test_7_keeps_sourcetype_sourceid_text(self):
        ctx = build_retrieved_context(SAMPLE)
        items = ctx["retrievedContext"]
        self.assertEqual(2, len(items))
        for it, src in zip(items, SAMPLE):
            self.assertEqual(src["sourceType"], it["sourceType"])
            self.assertEqual(src["sourceId"], it["sourceId"])
            self.assertEqual(src["text"], it["text"])

    def test_8_does_not_generate_score_or_decision(self):
        ctx = build_retrieved_context(SAMPLE)
        for it in ctx["retrievedContext"]:
            # 점수/판단 키가 하나도 없어야(설명 근거만)
            self.assertEqual(set(), FORBIDDEN_KEYS & set(it.keys()))
            self.assertNotIn("score", it)        # retrieval 내부 score 도 prompt 컨텍스트엔 미포함
            self.assertEqual({"sourceType", "sourceId", "text"}, set(it.keys()))

    def test_max_items_limit(self):
        big = SAMPLE * 10
        ctx = build_retrieved_context(big, max_items=3)
        self.assertEqual(3, len(ctx["retrievedContext"]))

    def test_guard_fires_on_extra_key(self):
        # 회귀 가드: builder 가 pass-through 로 바뀌어 항목에 금지/추가 키가 끼면 _assert 가 발화해야 한다.
        # (과거엔 재구성된 3키만 봐서 절대 발화 못 하는 dead code 였음.)
        with self.assertRaises(AssertionError):
            _assert_no_score_keys({"retrievedContext": [
                {"sourceType": "jd", "sourceId": "1", "text": "x", "score": 0.5}]})

    def test_guard_fires_on_value_level_leak(self):
        # text 값 안에 점수/판단이 박히면 차단(키-수준만으론 못 막는 누수).
        with self.assertRaises(AssertionError):
            _assert_no_score_keys({"retrievedContext": [
                {"sourceType": "jd", "sourceId": "1", "text": "applyDecision: REJECT 라고 적힘"}]})

    def test_value_leak_scanner_no_false_positive(self):
        self.assertIsNone(scan_text_for_score_leak("적합도가 높은 회사입니다."))
        self.assertIsNotNone(scan_text_for_score_leak("fitScore: 88"))


if __name__ == "__main__":
    unittest.main(verbosity=2)

"""retrievedContext builder 테스트 (reports/50 §9, 51).

실행: python rag_poc/tests/test_context_builder.py
핵심: builder 가 sourceType/sourceId/text 를 유지하고, **fitScore/applyDecision 을 생성하지 않는다**.
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from build_retrieved_context import build_retrieved_context, FORBIDDEN_KEYS  # noqa: E402

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


if __name__ == "__main__":
    unittest.main(verbosity=2)

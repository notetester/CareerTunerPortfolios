"""semantic(R1b) retriever 재현성 + retrievedContext 불변 테스트 (reports/52).

실행: python rag_poc/tests/test_embedding_retriever_quality.py
HashEmbedder 는 결정론이라 동일 입력 → 동일 랭킹(재현 가능). 의미 SOTA 검증이 아니라 구조·재현성·격리불변 검증.
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from offline_retriever import load_chunks  # noqa: E402
from embedding_retriever import EmbeddingRetriever, HashEmbedder, cosine  # noqa: E402
from build_retrieved_context import build_retrieved_context, FORBIDDEN_KEYS  # noqa: E402

CHUNKS = load_chunks(os.path.join(HERE, "..", "fixtures", "sample_chunks.jsonl"))
RET = EmbeddingRetriever(CHUNKS, embedder=HashEmbedder(dim=256))
SCOPE = {"user_id": "user-a", "application_id": "app-a"}


class EmbeddingQualityTest(unittest.TestCase):
    def test_deterministic_reproducible(self):
        # 동일 query 두 번 → 동일 랭킹(결정론 embedding)
        a = RET.retrieve("Java 백엔드 프레임워크", top_k=5, **SCOPE)
        b = RET.retrieve("Java 백엔드 프레임워크", top_k=5, **SCOPE)
        self.assertEqual([(x["chunkId"], x["score"]) for x in a],
                         [(x["chunkId"], x["score"]) for x in b])

    def test_cosine_self_is_one(self):
        emb = HashEmbedder(dim=256)
        v = emb.embed("Spring Boot REST API")
        self.assertAlmostEqual(1.0, cosine(v, v), places=4)   # 정규화 → self-cosine ≈ 1

    def test_retrieved_context_no_score_or_decision(self):
        res = RET.retrieve("SQL 자격증과 데이터 모델링", top_k=5, **SCOPE)
        ctx = build_retrieved_context(res)
        for it in ctx["retrievedContext"]:
            self.assertEqual(set(), FORBIDDEN_KEYS & set(it.keys()))
            self.assertNotIn("score", it)                      # retrieval score 는 prompt 컨텍스트 제외
            self.assertEqual({"sourceType", "sourceId", "text"}, set(it.keys()))

    def test_ngram_partial_match_signal(self):
        # n-gram 임베딩은 부분일치(SQL↔SQLD)에 양의 cosine — vector-search 구조가 lexical-only 보다 부분일치 포착
        emb = HashEmbedder(dim=256)
        self.assertGreater(cosine(emb.embed("SQL"), emb.embed("SQLD 자격")), 0.0)


if __name__ == "__main__":
    unittest.main(verbosity=2)

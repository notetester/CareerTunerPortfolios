"""semantic(R1b) retriever scope / fail-closed 회귀 테스트 (reports/52).

실행: python rag_poc/tests/test_embedding_retriever_scope.py
핵심: 임베딩/vector search 를 붙여도 scope filter 가 **검색 전에** 적용돼 격리·fail-closed 유지.
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from offline_retriever import load_chunks, in_scope  # noqa: E402
from embedding_retriever import EmbeddingRetriever, HashEmbedder  # noqa: E402

CHUNKS = load_chunks(os.path.join(HERE, "..", "fixtures", "sample_chunks.jsonl"))
# 결정론 백엔드 고정(테스트 재현성) — ST 환경 여부와 무관
RET = EmbeddingRetriever(CHUNKS, embedder=HashEmbedder(dim=256))


def ids(results):
    return {r["chunkId"] for r in results}


class EmbeddingScopeTest(unittest.TestCase):
    def test_1_user_a_cannot_retrieve_user_b(self):
        r = RET.retrieve("지원자 Python Pandas Spark 미경험", user_id="user-a", top_k=10, min_score=None)
        self.assertNotIn("chunk-profile-b-001", ids(r))

    def test_2_app_a_cannot_retrieve_app_b(self):
        r = RET.retrieve("데이터 엔지니어 Python Spark 파이프라인",
                         user_id="user-a", application_id="app-a", top_k=10, min_score=None)
        self.assertNotIn("chunk-job-b-001", ids(r))

    def test_3_user_private_without_userid_empty(self):
        r = RET.retrieve("SQL JPA 보유 정보처리기사", user_id=None, top_k=10, min_score=None)
        self.assertFalse(any(x["visibility"] == "user_private" for x in r))

    def test_4_application_private_without_appid_empty(self):
        r = RET.retrieve("Java Spring Boot REST API", user_id="user-a", application_id=None, top_k=10, min_score=None)
        self.assertFalse(any(x["visibility"] == "application_private" for x in r))

    def test_5_global_retrievable(self):
        r = RET.retrieve("SQLD 자격 데이터 모델링", user_id=None, top_k=10, min_score=None)
        self.assertIn("chunk-cert-sqld", ids(r))

    def test_6_scope_filter_applied_before_vector_search(self):
        # top_k 를 매우 크게, min_score 를 매우 낮게(전부 통과) 줘도 out-of-scope 는 절대 등장 안 함
        # → 랭킹(vector search) 이 허용집합에서만 일어남(검색 전 필터) 의 구조적 증거.
        r = RET.retrieve("아무 query SQL Python Java Spark", user_id="user-a", application_id=None,
                         top_k=100, min_score=-1.0)
        allowed = RET.allowed_ids(user_id="user-a", application_id=None)
        self.assertTrue(ids(r).issubset(allowed))                 # 결과 ⊆ 허용집합
        self.assertNotIn("chunk-job-b-001", ids(r))               # 타 지원건 없음
        self.assertNotIn("chunk-profile-b-001", ids(r))           # 타 사용자 없음
        self.assertFalse(any(x["visibility"] == "application_private" for x in r))  # appId 없으니 app-private 0


if __name__ == "__main__":
    unittest.main(verbosity=2)

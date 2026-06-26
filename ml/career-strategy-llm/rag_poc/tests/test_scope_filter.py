"""scope filter / fail-closed 보안 테스트 (reports/50 §8, 51).

실행: python rag_poc/tests/test_scope_filter.py  (stdlib unittest, 네트워크·모델 불필요)
핵심: 다른 사용자/지원 건 데이터가 검색되지 않고, 필요한 id 누락 시 fail-closed(0건)인지.
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from offline_retriever import load_chunks, retrieve, in_scope  # noqa: E402

CHUNKS = load_chunks(os.path.join(HERE, "..", "fixtures", "sample_chunks.jsonl"))


def ids(results):
    return {r["chunkId"] for r in results}


class ScopeFilterTest(unittest.TestCase):
    def test_1_user_a_can_retrieve_own(self):
        r = retrieve(CHUNKS, "SQL JPA 보유 정보처리기사", user_id="user-a")
        self.assertIn("chunk-profile-a-001", ids(r))

    def test_2_user_a_cannot_retrieve_user_b(self):
        # user-b 프로필 텍스트와 강하게 겹치는 query 라도 scope 로 차단돼야 함
        r = retrieve(CHUNKS, "지원자 B Python Pandas Spark 미경험", user_id="user-a")
        self.assertNotIn("chunk-profile-b-001", ids(r))

    def test_3_app_a_cannot_retrieve_app_b(self):
        r = retrieve(CHUNKS, "데이터 엔지니어 Python Spark 데이터 파이프라인",
                     user_id="user-a", application_id="app-a")
        self.assertNotIn("chunk-job-b-001", ids(r))

    def test_4_user_private_without_userid_is_empty(self):
        # userId 없이 user_private 를 노린 query → 결과에 user_private 가 하나도 없어야(fail-closed)
        r = retrieve(CHUNKS, "SQL JPA 보유 정보처리기사", user_id=None)
        self.assertFalse(any(r_i["visibility"] == "user_private" for r_i in r))
        self.assertNotIn("chunk-profile-a-001", ids(r))

    def test_5_application_private_without_appid_is_empty(self):
        r = retrieve(CHUNKS, "Java Spring Boot REST API SQL 백엔드", user_id="user-a", application_id=None)
        self.assertFalse(any(r_i["visibility"] == "application_private" for r_i in r))

    def test_6_global_catalog_retrievable(self):
        r = retrieve(CHUNKS, "SQLD SQL 데이터 모델링 자격", user_id=None)
        self.assertIn("chunk-cert-sqld", ids(r))

    # ── in_scope 직접 단위검증(fail-closed 명시) ──
    def test_in_scope_fail_closed(self):
        up = {"visibility": "user_private", "userId": "user-a"}
        ap = {"visibility": "application_private", "userId": "user-a", "applicationId": "app-a"}
        gl = {"visibility": "global"}
        self.assertTrue(in_scope(up, user_id="user-a"))
        self.assertFalse(in_scope(up, user_id=None))            # id 없음 → fail-closed
        self.assertFalse(in_scope(up, user_id="user-b"))        # 타 사용자
        self.assertTrue(in_scope(ap, user_id="user-a", application_id="app-a"))
        self.assertFalse(in_scope(ap, user_id="user-a", application_id=None))   # appId 없음
        self.assertFalse(in_scope(ap, user_id="user-a", application_id="app-b"))  # 타 지원건
        self.assertTrue(in_scope(gl))
        self.assertFalse(in_scope({"visibility": "weird"}))     # 미상 visibility → fail-closed


if __name__ == "__main__":
    unittest.main(verbosity=2)

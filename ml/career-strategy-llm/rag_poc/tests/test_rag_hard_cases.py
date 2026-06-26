"""RAG R2b hard-case 테스트 (reports/54).

실행: python rag_poc/tests/test_rag_hard_cases.py   (stdlib unittest, fresh checkout 실행 가능)
핵심 보장:
  1) hard-case fixture synthetic(이메일/전화/주민번호 패턴 없음)
  2) A/B pair 가 같은 caseId
  3) 차이는 retrievedContext 유무뿐
  4) retrievedContext 에 score/fitScore/applyDecision 없음
  5) negative control(retrievedContext 빈 케이스) 포함
  6) MSSQL vs SQL 케이스 포함
  7) fitScore/applyDecision 이 양 variant 에서 불변
"""
import copy
import json
import os
import re
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from build_rag_hard_cases import load_hard_cases, build_hard_pairs  # noqa: E402
from build_retrieved_context import FORBIDDEN_KEYS  # noqa: E402

CASES = load_hard_cases()
PAIRS = build_hard_pairs()
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_RE = re.compile(r"01[016789][- ]?\d{3,4}[- ]?\d{4}")
RRN_RE = re.compile(r"\b\d{6}[- ]?\d{7}\b")  # 주민번호 형태


class RagHardCasesTest(unittest.TestCase):
    def test_1_fixture_synthetic_no_pii(self):
        blob = json.dumps(CASES, ensure_ascii=False)
        self.assertIsNone(EMAIL_RE.search(blob), "이메일 형태 발견")
        self.assertIsNone(PHONE_RE.search(blob), "전화번호 형태 발견")
        self.assertIsNone(RRN_RE.search(blob), "주민번호 형태 발견")

    def test_2_ab_pair_same_caseid(self):
        self.assertGreaterEqual(len(PAIRS), 12, "hard-case 12개 이상이어야 함")
        for p in PAIRS:
            self.assertIn("lora_only", p["variants"])
            self.assertIn("lora_with_retrieved_context", p["variants"])
            self.assertTrue(p["caseId"])

    def test_3_pair_differs_only_by_retrieved_context(self):
        for p in PAIRS:
            a = copy.deepcopy(p["variants"]["lora_only"]["input"])
            b = copy.deepcopy(p["variants"]["lora_with_retrieved_context"]["input"])
            self.assertNotIn("retrievedContext", a, f"{p['caseId']} A 에 retrievedContext 존재")
            b.pop("retrievedContext", None)
            self.assertEqual(a, b, f"{p['caseId']} A/B 가 retrievedContext 외에도 다름")

    def test_4_no_score_decision_in_retrieved_context(self):
        for p in PAIRS:
            ctx = p["variants"]["lora_with_retrieved_context"]["input"].get("retrievedContext") or []
            for item in ctx:
                bad = FORBIDDEN_KEYS & set(item.keys())
                self.assertFalse(bad, f"{p['caseId']} retrievedContext 금지 키 {bad}")
                # sourceType/sourceId/text 만 허용
                self.assertEqual(set(item.keys()), {"sourceType", "sourceId", "text"},
                                 f"{p['caseId']} retrievedContext 키가 sourceType/sourceId/text 외 포함")

    def test_5_negative_control_present(self):
        negs = [c for c in CASES if c["hardType"] == "negative_control"]
        self.assertTrue(negs, "negative_control 케이스 없음")
        for c in negs:
            self.assertEqual([], c["retrievedContext"], f"{c['caseId']} negative control 인데 ctx 비어있지 않음")
        # builder 통과 후에도 B 의 retrievedContext 가 빈 배열
        for c in negs:
            p = [x for x in PAIRS if x["caseId"] == c["caseId"]][0]
            self.assertEqual([], p["variants"]["lora_with_retrieved_context"]["input"].get("retrievedContext"))

    def test_6_mssql_vs_sql_case_present(self):
        mssql = [c for c in CASES if c["hardType"] == "mssql_vs_sql"]
        self.assertTrue(mssql, "mssql_vs_sql 케이스 없음")
        for c in mssql:
            allowed = c["expected"]["allowedSkills"]
            # 허용엔 일반 SQL 만 있고 특정 제품 SQL(MSSQL 등)은 없어야 hard-case 성립
            self.assertIn("SQL", allowed)
            self.assertNotIn("MSSQL", allowed)

    def test_7_score_decision_invariant_both_variants(self):
        for p in PAIRS:
            a = p["variants"]["lora_only"]["input"]
            b = p["variants"]["lora_with_retrieved_context"]["input"]
            self.assertEqual(a["fitScore"], b["fitScore"], f"{p['caseId']} fitScore 변동")
            self.assertEqual(a["applyDecision"], b["applyDecision"], f"{p['caseId']} applyDecision 변동")

    def test_8_all_required_hard_types_present(self):
        types = {c["hardType"] for c in CASES}
        required = {
            "mssql_vs_sql", "fake_product_name", "cert_catalog_grounding",
            "company_research_context", "similar_stack_confusion",
            "data_role_confusion", "negative_control", "score_decision_invariant",
        }
        missing = required - types
        self.assertFalse(missing, f"누락 hardType: {missing}")


if __name__ == "__main__":
    unittest.main(verbosity=2)

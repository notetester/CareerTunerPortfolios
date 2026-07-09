"""build_rag_eval_cases 테스트 (reports/53).

실행: python rag_poc/tests/test_rag_eval_cases.py
핵심: A/B pair 가 같은 caseId·retrievedContext 유무만 차이·synthetic(개인정보 없음).
"""
import copy
import json
import os
import re
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from build_rag_eval_cases import build_cases, build_pairs  # noqa: E402

CASES = build_cases()
PAIRS = build_pairs(CASES)
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_RE = re.compile(r"01[016789][- ]?\d{3,4}[- ]?\d{4}")
RRN_RE = re.compile(r"\b\d{6}[- ]?\d{7}\b")  # 주민번호 형태


class RagEvalCasesTest(unittest.TestCase):
    def test_5_ab_pair_same_caseid(self):
        for p in PAIRS:
            self.assertIn("lora_only", p["variants"])
            self.assertIn("lora_with_retrieved_context", p["variants"])
            # 같은 caseId 하에 두 변형
            self.assertTrue(p["caseId"])

    def test_6_pair_differs_only_by_retrieved_context(self):
        for p in PAIRS:
            a = copy.deepcopy(p["variants"]["lora_only"]["input"])
            b = copy.deepcopy(p["variants"]["lora_with_retrieved_context"]["input"])
            # A 에는 retrievedContext 없음
            self.assertNotIn("retrievedContext", a)
            # B 에서 retrievedContext 를 떼면 A 와 동일해야(차이는 ctx 유무뿐)
            b.pop("retrievedContext", None)
            self.assertEqual(a, b, f"{p['caseId']} A/B 가 ctx 외에도 다름")

    def test_7_no_real_pii_in_fixtures(self):
        blob = json.dumps(CASES, ensure_ascii=False)
        self.assertIsNone(EMAIL_RE.search(blob), "이메일 형태 발견")
        self.assertIsNone(PHONE_RE.search(blob), "전화번호 형태 발견")
        self.assertIsNone(RRN_RE.search(blob), "주민번호 형태 발견")

    def test_negative_control_empty_context(self):
        neg = [p for p in PAIRS if p["caseId"] == "rag-negctrl-008"][0]
        self.assertEqual([], neg["variants"]["lora_with_retrieved_context"]["input"].get("retrievedContext"))

    def test_score_decision_preserved_in_both_variants(self):
        for p in PAIRS:
            a = p["variants"]["lora_only"]["input"]
            b = p["variants"]["lora_with_retrieved_context"]["input"]
            self.assertEqual(a["fitScore"], b["fitScore"])
            self.assertEqual(a["applyDecision"], b["applyDecision"])


if __name__ == "__main__":
    unittest.main(verbosity=2)

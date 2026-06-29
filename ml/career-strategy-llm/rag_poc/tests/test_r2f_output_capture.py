"""R2f output-capture 구조/불변 테스트 (reports/59).

실행: python rag_poc/tests/test_r2f_output_capture.py   (mock 캡처 — GPU 불필요)
핵심: rawText 저장 · parsedJson 분리 · hash 생성 · 3 gate 별도 필드 · originalOutput 비파괴 ·
mutation 감지 · parse fail 미은폐 · rewrite 후 계약 평가 · 샘플 truncate.
"""
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))
from capture_r2f_outputs import capture, _mutation, _hash  # noqa: E402
from summarize_r2f_results import analyze, _trunc, SAMPLE_MAX  # noqa: E402
from build_rag_evidence_buckets import EVIDENCE_VARIANT  # noqa: E402

RECS = capture(EVIDENCE_VARIANT, mock=True, base_url="", model="m", timeout=1, repeat=1)


class R2fCaptureTest(unittest.TestCase):
    def test_1_record_has_rawtext(self):
        self.assertTrue(RECS)
        for r in RECS:
            self.assertIn("rawText", r)
            self.assertIsInstance(r["rawText"], str)
            self.assertTrue(r["rawText"])

    def test_2_parsed_and_rawtext_separate(self):
        for r in RECS:
            self.assertIn("parsedJson", r)
            self.assertNotEqual(r["rawText"], r["parsedJson"])  # 문자열 vs dict

    def test_3_hashes_generated(self):
        for r in RECS:
            self.assertEqual(16, len(r["inputHash"]))
            self.assertEqual(16, len(r["messagesHash"]))

    def test_4_three_gates_separate_fields(self):
        for r in RECS:
            self.assertIn("gateReview", r)
            self.assertIn("gateReject", r)
            self.assertIn("gateRewrite", r)
            self.assertIn(r["gateReject"]["gateStatus"],
                          ("REJECTED", "PASSED", "REVIEW_REQUIRED", "REWRITTEN", "PARSE_FAIL"))

    def test_5_original_not_overwritten(self):
        # rewrite filteredOutput 은 parsedJson 과 별개 객체(원본 보존)
        for r in RECS:
            fo = r["gateRewrite"].get("filteredOutput")
            if r["gateRewrite"]["gateStatus"] == "REWRITTEN":
                self.assertIsNot(fo, r["parsedJson"])

    def test_6_mutation_detection(self):
        # _mutation 이 점수/판단 변화를 감지 + 캡처 레코드는 mutation 0
        self.assertEqual(1, _mutation({"fitScore": 70}, {"fitScore": 80}))
        self.assertEqual(0, _mutation({"fitScore": 70}, {"fitScore": 70}))
        self.assertEqual(0, sum(r["scoreDecisionMutation"] for r in RECS))

    def test_7_parse_fail_preserved(self):
        recs = capture(EVIDENCE_VARIANT, mock=True, base_url="", model="m", timeout=1, repeat=1)
        # mock 출력은 정상 JSON → parse ok. parseStatus 필드 존재 확인.
        for r in recs:
            self.assertIn(r["parseStatus"], ("ok", "not_object", "parse_fail"))

    def test_8_rewrite_contract_evaluable(self):
        for r in RECS:
            if r["gateRewrite"]["gateStatus"] == "REWRITTEN":
                self.assertIsNotNone(r["evaluationAfterRewrite"])
                self.assertIn("json_ok", r["evaluationAfterRewrite"])

    def test_9_audit_after_rewrite_present(self):
        for r in RECS:
            if r["gateRewrite"]["gateStatus"] == "REWRITTEN":
                self.assertIsNotNone(r["evidenceAuditAfterRewrite"])

    def test_10_sample_truncated(self):
        a = analyze({"records": RECS}, n_samples=5)
        for s in a["samples"]:
            self.assertLessEqual(len(s["before"]), SAMPLE_MAX + 1)
            self.assertLessEqual(len(s["after"]), SAMPLE_MAX + 1)
        # truncate 동작
        self.assertTrue(_trunc("x" * 200).endswith("…"))

    def test_11_hash_deterministic(self):
        self.assertEqual(_hash({"a": 1}), _hash({"a": 1}))


if __name__ == "__main__":
    unittest.main(verbosity=2)

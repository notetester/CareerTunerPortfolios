"""judge_consensus 단위 테스트 — 합의 규칙 + 병렬 지표.

실행: python scripts/test_judge_consensus.py   (stdlib unittest, 모델/네트워크 불필요)
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from judge_consensus import consensus_for_candidate, run_consensus, CONF_THRESHOLD  # noqa: E402
from semantic_skill_judge import canon_decision, normalize_verdict, validate_verdict  # noqa: E402


def v(judge, decision, conf=0.9, hr=False):
    return {"judge": judge, "decision": decision, "confidence": conf, "needsHumanReview": hr, "rationale": ""}


class ConsensusRuleTest(unittest.TestCase):
    def test_majority_wins(self):
        r = consensus_for_candidate([v("a", "valid_error"), v("b", "valid_error"), v("c", "acceptable_gray")])
        self.assertEqual("valid_error", r["finalDecision"])
        self.assertEqual("2/3", r["agreement"])

    def test_unanimous_high_conf_no_human(self):
        r = consensus_for_candidate([v("a", "harness_false_positive"), v("b", "harness_false_positive"),
                                     v("c", "harness_false_positive")])
        self.assertEqual("harness_false_positive", r["finalDecision"])
        self.assertFalse(r["needsHumanReview"])

    def test_tie_is_needs_policy_and_human(self):
        r = consensus_for_candidate([v("a", "valid_error"), v("b", "acceptable_gray")])
        self.assertEqual("needs_policy", r["finalDecision"])
        self.assertTrue(r["needsHumanReview"])

    def test_any_needs_policy_flags_human(self):
        r = consensus_for_candidate([v("a", "acceptable_gray"), v("b", "acceptable_gray"), v("c", "needs_policy")])
        self.assertEqual("acceptable_gray", r["finalDecision"])
        self.assertTrue(r["needsHumanReview"])

    def test_any_judge_human_flags_human(self):
        r = consensus_for_candidate([v("a", "acceptable_gray"), v("b", "acceptable_gray", hr=True),
                                     v("c", "acceptable_gray")])
        self.assertTrue(r["needsHumanReview"])

    def test_low_confidence_flags_human(self):
        lo = CONF_THRESHOLD - 0.2
        r = consensus_for_candidate([v("a", "acceptable_gray", conf=lo), v("b", "acceptable_gray", conf=lo),
                                     v("c", "acceptable_gray", conf=lo)])
        self.assertEqual("acceptable_gray", r["finalDecision"])
        self.assertTrue(r["needsHumanReview"])

    def test_no_verdicts(self):
        r = consensus_for_candidate([])
        self.assertEqual("needs_policy", r["finalDecision"])
        self.assertTrue(r["needsHumanReview"])
        self.assertEqual("0/0", r["agreement"])

    def test_tie_preserves_confidence(self):
        # 1-1 동률에서 둘 다 0.9 확신이면 conf 가 0.0 으로 사라지면 안 된다(과거 버그).
        r = consensus_for_candidate([v("a", "valid_error", 0.9), v("b", "acceptable_gray", 0.9)])
        self.assertEqual("needs_policy", r["finalDecision"])
        self.assertEqual(0.9, r["confidence"])
        self.assertEqual("tie 1-1", r["agreement"])


class VerdictGateTest(unittest.TestCase):
    """semantic_skill_judge 의 경계 게이트(외부 judge 신뢰불가 verdict 차단)를 직접 호출 검증.
    과거엔 consensus 테스트가 헬퍼 v()로 항상 well-formed verdict 만 만들어 이 게이트가 미검증이었다."""

    def test_validate_rejects_unknown_decision(self):
        errs = validate_verdict({"candidateId": "c1", "decision": "banana", "confidence": 0.5})
        self.assertTrue(any("decision" in e for e in errs))

    def test_validate_rejects_out_of_range_confidence(self):
        self.assertTrue(any("confidence" in e for e in
                            validate_verdict({"candidateId": "c1", "decision": "valid_error", "confidence": 1.5})))
        self.assertTrue(any("confidence" in e for e in
                            validate_verdict({"candidateId": "c1", "decision": "valid_error", "confidence": -0.1})))

    def test_validate_rejects_nonbool_human_review(self):
        errs = validate_verdict({"candidateId": "c1", "decision": "valid_error",
                                 "confidence": 0.5, "needsHumanReview": "yes"})
        self.assertTrue(any("needsHumanReview" in e for e in errs))

    def test_validate_rejects_empty_candidate(self):
        errs = validate_verdict({"candidateId": "", "decision": "valid_error", "confidence": 0.5})
        self.assertTrue(any("candidateId" in e for e in errs))

    def test_validate_accepts_clean(self):
        self.assertEqual([], validate_verdict({"candidateId": "c1", "decision": "valid_error",
                                               "confidence": 0.9, "needsHumanReview": False}))

    def test_canon_decision_aliases_to_gray(self):
        self.assertEqual("acceptable_gray", canon_decision("acceptable_same_skill"))
        self.assertEqual("acceptable_gray", canon_decision("acceptable_learning_context"))
        self.assertEqual("valid_error", canon_decision("valid_error"))

    def test_normalize_carries_synthetic_flag(self):
        nv = normalize_verdict({"candidateId": "c1", "decision": "acceptable",
                                "confidence": 0.6, "synthetic": True})
        self.assertEqual("acceptable_gray", nv["decision"])   # alias 정규화
        self.assertTrue(nv["synthetic"])                      # mock 식별 플래그 보존
        self.assertFalse(normalize_verdict({"candidateId": "c2", "decision": "valid_error",
                                            "confidence": 0.9})["synthetic"])


class MetricsTest(unittest.TestCase):
    def setUp(self):
        self.cands = [
            {"candidateId": "c1", "caseId": "case1", "flaggedText": "X",
             "occurrences": [{"model": "base", "run": 0}], "normalizer": {"status": "unresolved"}},
            {"candidateId": "c2", "caseId": "case2", "flaggedText": "Y",
             "occurrences": [{"model": "base", "run": 0}, {"model": "base", "run": 1}],
             "normalizer": {"status": "soft_match"}},
        ]
        self.verdicts = {
            "c1": [v("a", "valid_error"), v("b", "valid_error"), v("c", "valid_error")],
            "c2": [v("a", "harness_false_positive"), v("b", "harness_false_positive"),
                   v("c", "acceptable_gray")],
        }
        self.stats = {"raw_hallucination_flag_items": 10, "stage1_resolved_false_positive": 7,
                      "stage1_residual_to_judge": 3}

    def test_parallel_metrics(self):
        rows, m = run_consensus(self.cands, self.verdicts, self.stats)
        # raw 유지(대체 아님)
        self.assertEqual(10, m["raw_hallucination_flag_items"])
        self.assertEqual(7, m["stage1_resolved_false_positive"])
        self.assertEqual(3, m["normalized_hallucination_count"])
        # c1 valid_error 1 occurrence
        self.assertEqual(1, m["semantic_hallucination_count"])
        # c2(2 occurrences) harness_fp + stage1 7
        self.assertEqual(7 + 2, m["harness_false_positive_count"])
        self.assertEqual({"valid_error": 1, "harness_false_positive": 1}.get("valid_error"),
                         m["unique_by_final_decision"]["valid_error"])

    def test_by_model_breakdown(self):
        rows, m = run_consensus(self.cands, self.verdicts, self.stats)
        self.assertIn("base", m["by_model_occurrences"])
        self.assertEqual(1, m["by_model_occurrences"]["base"]["valid_error"])


if __name__ == "__main__":
    unittest.main(verbosity=2)

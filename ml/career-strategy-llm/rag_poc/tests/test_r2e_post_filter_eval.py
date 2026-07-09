"""R2e post-filter 집계 분석 테스트 (reports/58).

실행: python rag_poc/tests/test_r2e_post_filter_eval.py
핵심: R2d raw 의 측정 audit 카운트 → gate 적용 후 잔여 violation 0(구성적), 점수/판단 mutation 0,
위반 케이스 카운트 정확. (출력 텍스트 단위 동작은 test_evidence_gate_filter 가 검증.)
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from summarize_r2e_results import analyze, D  # noqa: E402

# 최소 R2d-raw 모사: D 가 2 케이스에서 위반(catalog), 1 케이스 통과.
R2D = {
    "evidence": {
        "lora_only": {"evidence_gate_violation_count": 10, "claimed_count": 33, "evidence_gate_pass_rate": 0.697,
                      "requirement_as_owned_count": 0, "catalog_as_owned_count": 0},
        "lora_with_retrieved_context": {"evidence_gate_violation_count": 15, "claimed_count": 40,
                                        "evidence_gate_pass_rate": 0.625, "requirement_as_owned_count": 0, "catalog_as_owned_count": 5},
        "lora_with_scoped_context": {"evidence_gate_violation_count": 22, "claimed_count": 51,
                                     "evidence_gate_pass_rate": 0.569, "requirement_as_owned_count": 0, "catalog_as_owned_count": 6},
        "lora_with_evidence_gated_context": {"evidence_gate_violation_count": 14, "claimed_count": 40,
                                             "evidence_gate_pass_rate": 0.65, "requirement_as_owned_count": 0, "catalog_as_owned_count": 4},
    },
    "perCaseVariant": [
        {"caseId": "hard-cat-1", "variant": D, "evidence": {"evidence_gate_violation_count": 3}},
        {"caseId": "hard-cat-2", "variant": D, "evidence": {"evidence_gate_violation_count": 2}},
        {"caseId": "hard-ok-1", "variant": D, "evidence": {"evidence_gate_violation_count": 0}},
    ],
}


class R2ePostFilterTest(unittest.TestCase):
    def test_after_violation_zero(self):
        a = analyze(R2D, "reject")
        for v, p in a["per_variant"].items():
            self.assertEqual(0, p["after"]["evidence_gate_violation"], v)
            self.assertEqual(0, p["after"]["catalog_as_owned"], v)

    def test_before_matches_measured(self):
        a = analyze(R2D, "reject")
        self.assertEqual(14, a["per_variant"][D]["before"]["evidence_gate_violation"])
        self.assertEqual(4, a["per_variant"][D]["before"]["catalog_as_owned"])

    def test_gated_case_count(self):
        a = analyze(R2D, "rewrite")
        # D 는 위반 케이스 2개(hard-cat-1/2), hard-ok-1 은 통과
        self.assertEqual(2, a["per_variant"][D]["gated_case_count"])
        self.assertEqual(["hard-cat-1", "hard-cat-2"], a["per_variant"][D]["gated_cases"])

    def test_no_score_decision_mutation(self):
        a = analyze(R2D, "rewrite")
        for p in a["per_variant"].values():
            self.assertEqual(0, p["fitScore_applyDecision_mutations"])

    def test_mode_recorded(self):
        self.assertEqual("review", analyze(R2D, "review")["mode"])


if __name__ == "__main__":
    unittest.main(verbosity=2)

"""judge_packet_builder 단위 테스트 — 결과+골든셋에서 후보 추출·dedup·통계.

실행: python scripts/test_judge_packet_builder.py   (stdlib unittest, 모델/네트워크 불필요)
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from judge_packet_builder import build_candidates  # noqa: E402

CASES = {
    "case-fp": {
        "id": "case-fp", "expectedDecision": "HOLD",
        "input": {"jobTitle": "백엔드", "requiredSkills": ["협업", "코드리뷰"], "preferredSkills": [],
                  "duties": "개발", "profileSkills": [], "profileCertificates": [],
                  "matchedSkills": [], "missingRequiredSkills": ["협업", "코드리뷰"], "missingPreferredSkills": []},
        "expected": {"allowedSkills": ["협업", "코드리뷰", "커뮤니케이션"]},
    },
    "case-soft": {
        "id": "case-soft", "expectedDecision": "COMPLEMENT_BEFORE_APPLY",
        "input": {"jobTitle": "SCM", "requiredSkills": ["수요예측"], "preferredSkills": [],
                  "duties": "수요예측", "profileSkills": [], "profileCertificates": [],
                  "matchedSkills": [], "missingRequiredSkills": ["수요예측"], "missingPreferredSkills": []},
        "expected": {"allowedSkills": ["수요예측", "재고관리"]},
    },
    "case-oos": {
        "id": "case-oos", "expectedDecision": "APPLY",
        "input": {"jobTitle": "CS", "requiredSkills": ["고객 상담"], "preferredSkills": [],
                  "duties": "상담", "profileSkills": [], "profileCertificates": [],
                  "matchedSkills": [], "missingRequiredSkills": [], "missingPreferredSkills": []},
        "expected": {"allowedSkills": ["고객 상담", "VOC 관리"]},
    },
}


def row(cid, run, bad, ltr):
    return {"id": cid, "run": run, "detail": {"bad_skills": bad},
            "parsed": {"learningTaskReasons": [{"skill": s, "why": f"{s} 근거"} for s in ltr]}}


class BuildCandidatesTest(unittest.TestCase):
    def setUp(self):
        rows_by_model = {
            "lora": [
                row("case-fp", 0, ["협업, 코드리뷰"], ["협업, 코드리뷰"]),         # 결정론 FP
                row("case-soft", 0, ["데이터 기반 수요예측"], ["데이터 기반 수요예측"]),  # soft → judge
                row("case-oos", 0, ["헬프데스크 솔루션 이해"], ["헬프데스크 솔루션 이해"]),  # unresolved → judge
            ],
            "base": [
                row("case-soft", 1, ["데이터 기반 수요예측"], ["데이터 기반 수요예측"]),  # 같은 후보 재출현
            ],
        }
        self.cands, self.stats = build_candidates(rows_by_model, CASES)

    def test_raw_count(self):
        self.assertEqual(4, self.stats["raw_hallucination_flag_items"])

    def test_stage1_resolves_obvious_fp(self):
        self.assertEqual(1, self.stats["stage1_resolved_false_positive"])

    def test_residual_and_unique(self):
        # soft(2 occurrences) + unresolved(1) = 3 잔여, 그러나 unique 후보는 2
        self.assertEqual(3, self.stats["stage1_residual_to_judge"])
        self.assertEqual(2, self.stats["unique_judge_candidates"])
        self.assertEqual(2, len(self.cands))

    def test_dedup_keeps_occurrences(self):
        soft = next(c for c in self.cands if c["caseId"] == "case-soft")
        self.assertEqual(2, len(soft["occurrences"]))
        self.assertEqual({"lora", "base"}, {o["model"] for o in soft["occurrences"]})

    def test_schema_fields_present(self):
        c = self.cands[0]
        for k in ("candidateId", "caseId", "flagType", "field", "flaggedText", "allowedSkills",
                  "matchedSkills", "missingSkills", "jobRequirements", "profileSkills",
                  "profileCertificates", "rawExcerpt", "normalizer", "expectedDecision"):
            self.assertIn(k, c, f"필드 {k} 누락")
        self.assertEqual("HALLUCINATED_SKILL", c["flagType"])

    def test_raw_excerpt_has_why(self):
        oos = next(c for c in self.cands if c["caseId"] == "case-oos")
        self.assertIn("헬프데스크", str(oos["rawExcerpt"]))

    def test_fp_not_in_candidates(self):
        self.assertNotIn("case-fp", {c["caseId"] for c in self.cands})


if __name__ == "__main__":
    unittest.main(verbosity=2)

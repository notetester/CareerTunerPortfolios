from __future__ import annotations

import unittest

from dataset_contract import sample_fingerprint, validate_sample


def valid_self_intro() -> dict:
    original = ("가" * 300) + "\n\n" + ("나" * 300)
    corrected = ("다" * 300) + "\n\n" + ("라" * 300)
    return {
        "id": "e-unified-v2-self-intro-0001",
        "task_type": "SELF_INTRO_CORRECTION",
        "input": {
            "original_text": original,
            "target_role": "백엔드 개발자",
            "job_context": {"company": "가상회사", "requirements": ["협업"], "preferred_skills": []},
            "user_profile_facts": ["사실 1", "사실 2", "사실 3"],
            "constraints": {
                "tone": "professional",
                "min_chars": 560,
                "target_chars": 602,
                "max_chars": 650,
                "preserve_paragraphs": True,
                "preserve_facts_only": True,
            },
        },
        "output": {
            "status": "ok",
            "task_type": "SELF_INTRO_CORRECTION",
            "corrected_text": corrected,
            "summary": "표현을 개선했다.",
            "changes": [
                {"before": "가", "after": "다", "reason": "구체화", "evidence_source": "original_text"},
                {"before": "나", "after": "라", "reason": "명료화", "evidence_source": "original_text"},
                {"before": "표현", "after": "개선", "reason": "직무 연결", "evidence_source": "job_context"},
            ],
            "risk_flags": [],
            "preserved_meaning": True,
            "added_facts": [],
            "recommended_keywords": ["협업"],
            "confidence": 0.9,
        },
    }


class DatasetContractTest(unittest.TestCase):
    def test_valid_unified_sample_passes(self) -> None:
        errors, warnings, metrics = validate_sample(valid_self_intro(), unified_contract=True)

        self.assertEqual([], errors)
        self.assertEqual([], warnings)
        self.assertAlmostEqual(1.0, metrics["output_ratio"])
        self.assertEqual(2, metrics["output_paragraphs"])

    def test_shortened_output_is_rejected(self) -> None:
        row = valid_self_intro()
        row["output"]["corrected_text"] = "짧은 결과"

        errors, _, _ = validate_sample(row, unified_contract=True)

        self.assertTrue(any("ratio" in error for error in errors))
        self.assertTrue(any("below min_chars" in error for error in errors))
        self.assertTrue(any("paragraphs" in error for error in errors))

    def test_preserved_meaning_false_is_rejected(self) -> None:
        row = valid_self_intro()
        row["output"]["preserved_meaning"] = False

        errors, _, _ = validate_sample(row, unified_contract=True)

        self.assertIn("output.preserved_meaning must be true", errors)

    def test_fingerprint_ignores_whitespace_and_case(self) -> None:
        left = valid_self_intro()
        right = valid_self_intro()
        right["input"]["original_text"] = "  " + left["input"]["original_text"].upper() + "  "

        self.assertEqual(sample_fingerprint(left), sample_fingerprint(right))


if __name__ == "__main__":
    unittest.main()

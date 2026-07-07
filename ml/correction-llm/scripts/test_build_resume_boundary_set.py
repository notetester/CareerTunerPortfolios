import unittest

from build_resume_boundary_set import build_variants


def sample() -> dict:
    return {
        "id": "resume-1",
        "task_type": "RESUME_EXPRESSION_IMPROVEMENT",
        "input": {
            "original_text": "a" * 220,
            "target_role": "role",
            "job_context": {},
            "user_profile_facts": [],
            "constraints": {
                "tone": "professional",
                "min_chars": 120,
                "target_chars": 180,
                "max_chars": 260,
                "preserve_paragraphs": False,
                "preserve_facts_only": True,
            },
        },
        "output": {
            "status": "ok",
            "task_type": "RESUME_EXPRESSION_IMPROVEMENT",
            "corrected_text": "b" * 180,
            "summary": "summary",
            "changes": [
                {"before": "a", "after": "b", "reason": "r", "evidence_source": "original_text"},
                {"before": "c", "after": "d", "reason": "r", "evidence_source": "original_text"},
                {"before": "e", "after": "f", "reason": "r", "evidence_source": "original_text"},
            ],
            "risk_flags": [],
            "preserved_meaning": True,
            "added_facts": [],
            "recommended_keywords": [],
            "confidence": 0.9,
        },
    }


class ResumeBoundarySetTest(unittest.TestCase):
    def test_generates_caps_that_keep_output_valid(self) -> None:
        variants = build_variants(sample(), [0, 5, 10], None)

        self.assertEqual([180, 185, 190], [row["input"]["constraints"]["max_chars"] for row in variants])


if __name__ == "__main__":
    unittest.main()

import json
import unittest

from evaluate_controller_infer import patch_minor_length_shortfall


class EvaluateControllerInferTest(unittest.TestCase):
    def test_patches_tiny_shortfall(self) -> None:
        sample = {
            "input": {
                "constraints": {
                    "min_chars": 10,
                    "max_chars": 12,
                }
            }
        }
        text = json.dumps(
            {
                "status": "ok",
                "task_type": "RESUME_EXPRESSION_IMPROVEMENT",
                "corrected_text": "abcdefghi",
                "summary": "s",
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
            ensure_ascii=False,
        )

        patched = patch_minor_length_shortfall(text, sample)
        parsed = json.loads(patched)
        self.assertEqual("abcdefghi.", parsed["corrected_text"])


if __name__ == "__main__":
    unittest.main()

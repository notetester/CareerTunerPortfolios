import json
import unittest

from evaluate_controller_infer import patch_minor_length_shortfall, restore_repair_paragraphs


class EvaluateControllerInferTest(unittest.TestCase):
    def test_restores_required_paragraphs_without_changing_sentences(self) -> None:
        sample = {
            "input": {
                "original_text": "첫 문단\n\n둘째 문단\n\n셋째 문단",
                "constraints": {"preserve_paragraphs": True, "max_chars": 100},
            }
        }
        text = json.dumps({"corrected_text": "첫 문장입니다. 둘째 문장입니다. 셋째 문장입니다."}, ensure_ascii=False)

        restored = restore_repair_paragraphs(text, sample, ["output paragraphs 1 do not preserve source paragraphs 3"])

        self.assertEqual(
            "첫 문장입니다.\n\n둘째 문장입니다.\n\n셋째 문장입니다.",
            json.loads(restored)["corrected_text"],
        )

    def test_keeps_output_when_sentence_boundaries_are_insufficient(self) -> None:
        sample = {
            "input": {
                "original_text": "첫 문단\n\n둘째 문단\n\n셋째 문단",
                "constraints": {"preserve_paragraphs": True, "max_chars": 100},
            }
        }
        text = json.dumps({"corrected_text": "첫 문장입니다. 둘째 문장입니다."}, ensure_ascii=False)

        restored = restore_repair_paragraphs(text, sample, ["paragraph contract failed"])

        self.assertEqual(text, restored)

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

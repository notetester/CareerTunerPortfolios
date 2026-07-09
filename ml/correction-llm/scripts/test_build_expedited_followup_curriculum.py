import json
import random
import unittest

from build_expedited_followup_curriculum import (
    balanced_anchors,
    boundary_rows,
    direct_message_to_raw,
)


def message(sample_id: str, task_type: str, corrected_text: str) -> dict:
    payload = {
        "id": sample_id,
        "task_type": task_type,
        "input": {"original_text": "원문", "constraints": {}},
    }
    output = {"corrected_text": corrected_text}
    return {
        "messages": [
            {"role": "system", "content": "system"},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
            {"role": "assistant", "content": json.dumps(output, ensure_ascii=False)},
        ]
    }


class ExpeditedCurriculumTest(unittest.TestCase):
    def test_balanced_anchors_excludes_ids(self) -> None:
        rows = [
            message("i-1", "INTERVIEW_ANSWER_CORRECTION", "가"),
            message("i-2", "INTERVIEW_ANSWER_CORRECTION", "나"),
            message("r-1", "RESUME_EXPRESSION_IMPROVEMENT", "다"),
            message("r-2", "RESUME_EXPRESSION_IMPROVEMENT", "라"),
        ]

        selected = balanced_anchors(rows, {"i-1", "r-1"}, 1, random.Random(42))

        ids = {json.loads(row["messages"][1]["content"])["id"] for row in selected}
        self.assertEqual({"i-2", "r-2"}, ids)

    def test_boundary_rows_filters_output_length_and_limits_variants(self) -> None:
        rows = [
            message("r-1", "RESUME_EXPRESSION_IMPROVEMENT", "가" * 245),
            message("r-1", "RESUME_EXPRESSION_IMPROVEMENT", "나" * 250),
            message("r-1", "RESUME_EXPRESSION_IMPROVEMENT", "다" * 255),
            message("r-2", "RESUME_EXPRESSION_IMPROVEMENT", "라" * 280),
        ]

        selected = boundary_rows(
            rows,
            excluded_ids=set(),
            limit=2,
            max_per_id=2,
            rng=random.Random(42),
            min_output_length=240,
            max_output_length=260,
        )

        self.assertEqual(2, len(selected))
        self.assertEqual(
            {"r-1"},
            {json.loads(row["messages"][1]["content"])["id"] for row in selected},
        )

    def test_direct_message_to_raw_preserves_contract_parts(self) -> None:
        row = message("r-1", "RESUME_EXPRESSION_IMPROVEMENT", "첨삭문")

        raw = direct_message_to_raw(row)

        self.assertEqual("r-1", raw["id"])
        self.assertEqual("원문", raw["input"]["original_text"])
        self.assertEqual("첨삭문", raw["output"]["corrected_text"])


if __name__ == "__main__":
    unittest.main()

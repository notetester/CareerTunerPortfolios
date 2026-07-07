from __future__ import annotations

import unittest
from collections import Counter

from generate_unified_data import (
    TASKS,
    assign_ids,
    normalize_constraints,
    remaining_tasks,
    response_schema,
    validate_batch,
)
from test_dataset_contract import valid_self_intro


class GenerateUnifiedDataTest(unittest.TestCase):
    def test_schema_requests_exact_batch_size(self) -> None:
        schema = response_schema(list(TASKS))
        samples = schema["properties"]["samples"]

        self.assertEqual(4, samples["minItems"])
        self.assertEqual(4, samples["maxItems"])

    def test_assign_ids_uses_task_sequence(self) -> None:
        sample = valid_self_intro()
        sample.pop("id")

        assigned = assign_ids([sample], Counter({"SELF_INTRO_CORRECTION": 2}))

        self.assertEqual("e-unified-v2-self-intro-0003", assigned[0]["id"])
        self.assertEqual([], validate_batch(assigned, ["SELF_INTRO_CORRECTION"], set()))

    def test_remaining_tasks_honors_task_filter(self) -> None:
        remaining = remaining_tasks(
            Counter({"SELF_INTRO_CORRECTION": 1, "INTERVIEW_ANSWER_CORRECTION": 1}),
            2,
            ("SELF_INTRO_CORRECTION",),
        )

        self.assertEqual(["SELF_INTRO_CORRECTION"], remaining)

    def test_normalize_constraints_derives_limits_from_original(self) -> None:
        sample = valid_self_intro()
        sample.pop("id")
        sample["input"]["constraints"]["min_chars"] = 9999

        normalized = normalize_constraints([sample])[0]

        constraints = normalized["input"]["constraints"]
        self.assertEqual(512, constraints["min_chars"])
        self.assertEqual(602, constraints["target_chars"])
        self.assertEqual(662, constraints["max_chars"])


if __name__ == "__main__":
    unittest.main()

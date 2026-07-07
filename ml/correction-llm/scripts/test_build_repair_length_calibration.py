import json
import unittest

from build_repair_length_calibration import build, qualifying_task


def row(task: str, length: int, index: int) -> dict:
    return {
        "messages": [
            {"role": "system", "content": "system"},
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "id": index,
                        "task_type": task,
                        "input": {"constraints": {"min_chars": 3, "max_chars": 5}},
                    }
                ),
            },
            {"role": "user", "content": "repair"},
            {"role": "assistant", "content": json.dumps({"corrected_text": "가" * length})},
        ]
    }


class BuildRepairLengthCalibrationTest(unittest.TestCase):
    def test_filters_outputs_outside_length_contract(self) -> None:
        self.assertEqual("A", qualifying_task(row("A", 4, 1)))
        self.assertIsNone(qualifying_task(row("A", 6, 2)))

    def test_balances_tasks(self) -> None:
        rows = [row(task, 4, index) for task in ("A", "B") for index in range(3)]
        selected, summary = build(rows, per_task=2, seed=7)

        self.assertEqual(4, len(selected))
        self.assertEqual({"A": 2, "B": 2}, summary["task_counts"])


if __name__ == "__main__":
    unittest.main()

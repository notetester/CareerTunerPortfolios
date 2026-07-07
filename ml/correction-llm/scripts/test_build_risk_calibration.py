import json
import unittest

from build_risk_calibration import build


def row(task: str, risky: bool, index: int) -> dict:
    output = {"risk_flags": ["확인 필요"] if risky else []}
    return {
        "messages": [
            {"role": "system", "content": "system"},
            {"role": "user", "content": json.dumps({"task_type": task, "id": index})},
            {"role": "assistant", "content": json.dumps(output)},
        ]
    }


class BuildRiskCalibrationTest(unittest.TestCase):
    def test_balances_each_task_and_risk_class(self) -> None:
        rows = [row(task, risky, index) for task in ("A", "B") for risky in (False, True) for index in range(3)]
        selected, summary = build(rows, per_task=2, seed=7)

        self.assertEqual(8, len(selected))
        self.assertEqual({"clean": 2, "risky": 2}, summary["task_counts"]["A"])
        self.assertEqual({"clean": 2, "risky": 2}, summary["task_counts"]["B"])

    def test_can_select_only_risky_rows(self) -> None:
        rows = [row("A", risky, index) for risky in (False, True) for index in range(3)]
        selected, summary = build(rows, per_task=2, seed=7, class_mode="risky-only")

        self.assertEqual(2, len(selected))
        self.assertEqual({"risky": 2}, summary["task_counts"]["A"])


if __name__ == "__main__":
    unittest.main()

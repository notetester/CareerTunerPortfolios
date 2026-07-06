import json
import unittest

from build_p1_curriculum import build_curriculum, repair_message


TASKS = [
    "SELF_INTRO_CORRECTION",
    "INTERVIEW_ANSWER_CORRECTION",
    "RESUME_EXPRESSION_IMPROVEMENT",
    "PORTFOLIO_DESCRIPTION_IMPROVEMENT",
]


def raw_row(sample_id: str, task_type: str) -> dict:
    return {
        "id": sample_id,
        "task_type": task_type,
        "input": {"original_text": "원문", "constraints": {"min_chars": 2, "max_chars": 20}},
        "output": {
            "status": "ok",
            "task_type": task_type,
            "corrected_text": "개선 문장",
            "summary": "요약",
            "changes": [
                {
                    "before": "원문",
                    "after": "개선 문장",
                    "reason": "표현 개선",
                    "evidence_source": "original_text",
                },
                {
                    "before": "원문",
                    "after": "개선 문장",
                    "reason": "문장 개선",
                    "evidence_source": "original_text",
                },
                {
                    "before": "원문",
                    "after": "개선 문장",
                    "reason": "근거 유지",
                    "evidence_source": "original_text",
                },
            ],
            "risk_flags": [],
            "preserved_meaning": True,
            "added_facts": [],
            "recommended_keywords": [],
            "confidence": 0.9,
        },
    }


def message_row(sample_id: str, task_type: str) -> dict:
    return {
        "messages": [
            {"role": "system", "content": "system"},
            {"role": "user", "content": json.dumps({"id": sample_id, "task_type": task_type})},
            {"role": "assistant", "content": "{}"},
        ]
    }


class BuildP1CurriculumTest(unittest.TestCase):
    def test_repair_message_never_uses_invalid_output_as_assistant_target(self) -> None:
        row = repair_message(raw_row("train-self", TASKS[0]), "missing_changes")

        self.assertEqual(["system", "user", "user", "assistant"], [item["role"] for item in row["messages"]])
        self.assertIn("root has missing or extra keys", row["messages"][2]["content"])
        self.assertNotIn('"changes"', row["messages"][2]["content"].split("<invalid_output>", 1)[1].split("</invalid_output>", 1)[0])
        self.assertIn('"changes"', row["messages"][3]["content"])

    def test_curriculum_balances_repair_variants_and_repeats_target_long_tasks(self) -> None:
        repair_train = [raw_row(f"train-{index}", task) for index, task in enumerate(TASKS)]
        repair_val = [raw_row(f"val-{index}", task) for index, task in enumerate(TASKS)]
        long_train = [message_row(f"long-train-{index}", task) for index, task in enumerate(TASKS)]
        long_val = [message_row(f"long-val-{index}", task) for index, task in enumerate(TASKS)]

        train, val, summary = build_curriculum(
            repair_train,
            repair_val,
            long_train,
            long_val,
            repair_train_per_task=1,
            repair_val_per_task=1,
            seed=42,
        )

        self.assertEqual(14, len(train))
        self.assertEqual(12, len(val))
        self.assertEqual(4, summary["train_kind_counts"]["repair:missing_changes"])
        self.assertEqual(4, summary["train_kind_counts"]["repair:preserved_false"])
        self.assertEqual(2, summary["train_kind_counts"]["long:targeted-repeat"])

    def test_curriculum_splits_overlapping_repair_source_without_id_leakage(self) -> None:
        repair_source = [
            raw_row(f"shared-{task}-{index}", task)
            for task in TASKS
            for index in range(2)
        ]

        train, val, _ = build_curriculum(
            repair_source,
            repair_source,
            [],
            [],
            repair_train_per_task=1,
            repair_val_per_task=1,
            seed=42,
        )

        train_ids = {json.loads(row["messages"][1]["content"])["id"] for row in train}
        val_ids = {json.loads(row["messages"][1]["content"])["id"] for row in val}
        self.assertTrue(train_ids.isdisjoint(val_ids))

    def test_curriculum_aligns_repair_samples_with_existing_long_split(self) -> None:
        repair_source = [
            raw_row(f"shared-{task}-{index}", task)
            for task in TASKS
            for index in range(2)
        ]
        long_train = [message_row(f"shared-{task}-0", task) for task in TASKS]
        long_val = [message_row(f"shared-{task}-1", task) for task in TASKS]

        train, val, summary = build_curriculum(
            repair_source,
            repair_source,
            long_train,
            long_val,
            repair_train_per_task=1,
            repair_val_per_task=1,
            seed=42,
        )

        train_ids = {json.loads(row["messages"][1]["content"])["id"] for row in train}
        val_ids = {json.loads(row["messages"][1]["content"])["id"] for row in val}
        self.assertTrue(train_ids.isdisjoint(val_ids))
        self.assertEqual(0, summary["train_val_id_overlap_count"])


if __name__ == "__main__":
    unittest.main()

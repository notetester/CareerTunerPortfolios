from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from build_messages import to_messages
from merge_messages import merge
from test_dataset_contract import valid_self_intro


class MergeMessagesTest(unittest.TestCase):
    def test_merge_keeps_distinct_message_rows(self) -> None:
        first = valid_self_intro()
        second = valid_self_intro()
        second["id"] = "e-unified-v2-self-intro-0002"
        second["input"]["original_text"] = second["input"]["original_text"].replace("가", "마")

        with tempfile.TemporaryDirectory() as temp_dir:
            first_path = Path(temp_dir) / "first.jsonl"
            second_path = Path(temp_dir) / "second.jsonl"
            first_path.write_text(json.dumps(to_messages(first), ensure_ascii=False) + "\n", encoding="utf-8")
            second_path.write_text(json.dumps(to_messages(second), ensure_ascii=False) + "\n", encoding="utf-8")

            rows, summary = merge([first_path, second_path])

        self.assertEqual(2, len(rows))
        self.assertEqual(2, summary["merged_count"])
        self.assertEqual(0, summary["duplicate_texts_skipped"])

    def test_merge_preserves_baseline_variants_and_dedupes_added_data(self) -> None:
        baseline_one = valid_self_intro()
        baseline_two = valid_self_intro()
        baseline_two["id"] = "e-unified-v2-self-intro-0002"
        baseline_two["output"]["summary"] = "같은 원문의 다른 기준 응답"
        added = valid_self_intro()
        added["id"] = "e-unified-v2-self-intro-0003"

        with tempfile.TemporaryDirectory() as temp_dir:
            baseline_path = Path(temp_dir) / "baseline.jsonl"
            added_path = Path(temp_dir) / "added.jsonl"
            baseline_path.write_text(
                "\n".join(
                    [
                        json.dumps(to_messages(baseline_one), ensure_ascii=False),
                        json.dumps(to_messages(baseline_two), ensure_ascii=False),
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            added_path.write_text(json.dumps(to_messages(added), ensure_ascii=False) + "\n", encoding="utf-8")

            rows, summary = merge([baseline_path, added_path])

        self.assertEqual(2, len(rows))
        self.assertEqual(1, summary["baseline_duplicate_texts_preserved"])
        self.assertEqual(1, summary["duplicate_texts_skipped"])


if __name__ == "__main__":
    unittest.main()

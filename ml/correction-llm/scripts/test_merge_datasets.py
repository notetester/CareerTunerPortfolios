from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from merge_datasets import merge
from test_dataset_contract import valid_self_intro


class MergeDatasetsTest(unittest.TestCase):
    def test_merge_skips_duplicate_original_text(self) -> None:
        first = valid_self_intro()
        second = valid_self_intro()
        second["id"] = "e-unified-v2-self-intro-0002"

        with tempfile.TemporaryDirectory() as temp_dir:
            first_path = Path(temp_dir) / "first.jsonl"
            second_path = Path(temp_dir) / "second.jsonl"
            first_path.write_text(json.dumps(first, ensure_ascii=False) + "\n", encoding="utf-8")
            second_path.write_text(json.dumps(second, ensure_ascii=False) + "\n", encoding="utf-8")

            rows, summary = merge([first_path, second_path])

        self.assertEqual(1, len(rows))
        self.assertEqual(1, summary["duplicate_texts_skipped"])

    def test_merge_rejects_conflicting_duplicate_id(self) -> None:
        first = valid_self_intro()
        second = valid_self_intro()
        second["output"]["summary"] = "다른 결과"

        with tempfile.TemporaryDirectory() as temp_dir:
            first_path = Path(temp_dir) / "first.jsonl"
            second_path = Path(temp_dir) / "second.jsonl"
            first_path.write_text(json.dumps(first, ensure_ascii=False) + "\n", encoding="utf-8")
            second_path.write_text(json.dumps(second, ensure_ascii=False) + "\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "same id"):
                merge([first_path, second_path])


if __name__ == "__main__":
    unittest.main()

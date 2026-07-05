from __future__ import annotations

import unittest

from build_messages import to_messages
from remove_message_overlap import remove_overlap
from test_dataset_contract import valid_self_intro


class RemoveMessageOverlapTest(unittest.TestCase):
    def test_validation_fingerprint_removes_train_variant(self) -> None:
        train_sample = valid_self_intro()
        train_sample["id"] = "train-1"
        val_sample = valid_self_intro()
        val_sample["id"] = "val-1"

        cleaned, summary = remove_overlap([to_messages(train_sample)], [to_messages(val_sample)])

        self.assertEqual([], cleaned)
        self.assertEqual(1, summary["removed_count"])
        self.assertEqual("fingerprint", summary["removed"][0]["reason"])
        self.assertEqual(0, summary["remaining_fingerprint_overlap"])

    def test_distinct_train_row_is_preserved(self) -> None:
        train_sample = valid_self_intro()
        train_sample["id"] = "train-1"
        train_sample["input"]["original_text"] = train_sample["input"]["original_text"].replace(
            "가", "나"
        )
        val_sample = valid_self_intro()
        val_sample["id"] = "val-1"

        cleaned, summary = remove_overlap([to_messages(train_sample)], [to_messages(val_sample)])

        self.assertEqual(1, len(cleaned))
        self.assertEqual(0, summary["removed_count"])
        self.assertEqual(0, summary["remaining_id_overlap"])
        self.assertEqual(0, summary["remaining_fingerprint_overlap"])


if __name__ == "__main__":
    unittest.main()

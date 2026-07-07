import unittest

from evaluate_repair_infer import repair_messages


class EvaluateRepairInferTest(unittest.TestCase):
    def test_repair_messages_match_runtime_user_repair_shape(self) -> None:
        sample = {
            "id": "sample-1",
            "task_type": "SELF_INTRO_CORRECTION",
            "input": {"original_text": "원문"},
        }
        messages = repair_messages(sample, '{"status":"ok"}', "root is missing changes")

        self.assertEqual(["system", "user", "user"], [message["role"] for message in messages])
        self.assertIn("root is missing changes", messages[2]["content"])
        self.assertIn('<invalid_output>\n{"status":"ok"}', messages[2]["content"])


if __name__ == "__main__":
    unittest.main()

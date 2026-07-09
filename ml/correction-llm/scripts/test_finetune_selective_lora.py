import unittest

from finetune_selective_lora import json_key_span


class SelectiveLossSpanTest(unittest.TestCase):
    def test_finds_complete_array_value(self) -> None:
        content = '{"status":"ok","risk_flags":["수치 확인 필요","범위 확인 필요"],"confidence":0.8}'
        start, end = json_key_span(content, "risk_flags")

        self.assertEqual('"risk_flags":["수치 확인 필요","범위 확인 필요"]', content[start:end])

    def test_finds_empty_array(self) -> None:
        content = '{"risk_flags":[],"confidence":0.9}'
        start, end = json_key_span(content, "risk_flags")

        self.assertEqual('"risk_flags":[]', content[start:end])


if __name__ == "__main__":
    unittest.main()

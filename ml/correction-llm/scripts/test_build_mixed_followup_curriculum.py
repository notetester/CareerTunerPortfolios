import json
import tempfile
import unittest
from pathlib import Path

from build_mixed_followup_curriculum import build_dataset, parse_source_spec


class MixedFollowupCurriculumTest(unittest.TestCase):
    def test_parses_source_spec(self) -> None:
        label, path, repeat = parse_source_spec("base=data.jsonl@3")
        self.assertEqual("base", label)
        self.assertEqual(Path("data.jsonl"), path)
        self.assertEqual(3, repeat)

    def test_repeats_sources(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "rows.jsonl"
            path.write_text(json.dumps({"messages": []}) + "\n", encoding="utf-8")
            rows, summary = build_dataset([f"base={path}@2"], seed=7)
            self.assertEqual(2, len(rows))
            self.assertEqual(2, summary["base"]["expanded_count"])


if __name__ == "__main__":
    unittest.main()

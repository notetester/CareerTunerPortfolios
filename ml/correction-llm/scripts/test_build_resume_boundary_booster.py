import json
import tempfile
import unittest
from pathlib import Path


class ResumeBoundaryBoosterTest(unittest.TestCase):
    def test_builds_direct_and_repair_rows(self) -> None:
        from build_resume_boundary_booster import main
        import sys

        payload = {
            "id": "resume-1",
            "task_type": "RESUME_EXPRESSION_IMPROVEMENT",
            "input": {
                "original_text": "a" * 260,
                "target_role": "role",
                "job_context": {},
                "user_profile_facts": ["fact"],
                "constraints": {
                    "tone": "professional",
                    "min_chars": 200,
                    "target_chars": 245,
                    "max_chars": 260,
                    "preserve_paragraphs": False,
                    "preserve_facts_only": True,
                },
            },
        }
        output = {
            "status": "ok",
            "task_type": "RESUME_EXPRESSION_IMPROVEMENT",
            "corrected_text": "b" * 245,
            "summary": "summary",
            "changes": [
                {"before": "a", "after": "b", "reason": "r", "evidence_source": "original_text"},
                {"before": "c", "after": "d", "reason": "r", "evidence_source": "original_text"},
                {"before": "e", "after": "f", "reason": "r", "evidence_source": "original_text"},
            ],
            "risk_flags": [],
            "preserved_meaning": True,
            "added_facts": [],
            "recommended_keywords": [],
            "confidence": 0.9,
        }
        row = {
            "messages": [
                {"role": "system", "content": "sys"},
                {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
                {"role": "assistant", "content": json.dumps(output, ensure_ascii=False)},
            ]
        }

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            messages_path = tmp_path / "messages.jsonl"
            direct_out = tmp_path / "direct.jsonl"
            repair_out = tmp_path / "repair.jsonl"
            summary_out = tmp_path / "summary.json"
            messages_path.write_text(json.dumps(row, ensure_ascii=False) + "\n", encoding="utf-8")

            argv = sys.argv[:]
            sys.argv = [
                "build_resume_boundary_booster.py",
                "--messages",
                str(messages_path),
                "--direct-out",
                str(direct_out),
                "--repair-out",
                str(repair_out),
                "--summary-out",
                str(summary_out),
                "--cap-step",
                "5",
            ]
            try:
                main()
            finally:
                sys.argv = argv

            direct_rows = [json.loads(line) for line in direct_out.read_text(encoding="utf-8").splitlines() if line.strip()]
            repair_rows = [json.loads(line) for line in repair_out.read_text(encoding="utf-8").splitlines() if line.strip()]
            summary = json.loads(summary_out.read_text(encoding="utf-8"))

            self.assertEqual(4, len(direct_rows))
            self.assertEqual(4, len(repair_rows))
            self.assertEqual(1, summary["unique_ids"])
            self.assertEqual({"245": 1, "250": 1, "255": 1, "260": 1}, summary["cap_counts"])


if __name__ == "__main__":
    unittest.main()

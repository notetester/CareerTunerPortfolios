import json
import tempfile
import unittest
from pathlib import Path

from build_failure_specific_curriculum import main as _main  # noqa: F401
from followup_pipeline_common import build_runtime_repair_messages, classify_problem_kind


class FailureSpecificCurriculumTest(unittest.TestCase):
    def test_classifies_major_failure_kinds(self) -> None:
        self.assertEqual("missing_risk_flags", classify_problem_kind(["risk_flags_missing_for_expected_risky_sample"]))
        self.assertEqual("length_overflow", classify_problem_kind(["contract:output.corrected_text length 295 exceeds max_chars 260"]))
        self.assertEqual("json_parse_fail", classify_problem_kind(["JSON_PARSE_FAIL"]))
        self.assertEqual("missing_preserved_meaning", classify_problem_kind(["output.preserved_meaning must be true"]))
        self.assertEqual(
            "paragraph_contract",
            classify_problem_kind(["contract:output paragraphs 1 do not preserve source paragraphs 3"]),
        )
        self.assertEqual("cjk_leak", classify_problem_kind(["CJK_LEAK"]))

    def test_builds_paragraph_specific_repair_prompt(self) -> None:
        sample = {
            "id": "sample-1",
            "task_type": "SELF_INTRO_CORRECTION",
            "input": {
                "original_text": "첫 문단\n\n둘째 문단\n\n셋째 문단",
                "constraints": {"min_chars": 10, "target_chars": 20, "max_chars": 30},
            },
        }

        messages = build_runtime_repair_messages(
            "system",
            "검증 실패 사유: {validation_error}\n{previous_output}",
            sample,
            previous_output="{}",
            validation_error="contract:output paragraphs 1 do not preserve source paragraphs 3",
        )

        self.assertIn("정확히 3개 문단", messages[-1]["content"])
        self.assertIn("<invalid_output>\n{}\n</invalid_output>", messages[-1]["content"])
        self.assertIn("검증 실패 사유를 risk_flags", messages[-1]["content"])

    def test_builds_cjk_specific_repair_prompt(self) -> None:
        sample = {
            "id": "sample-1",
            "task_type": "INTERVIEW_ANSWER_CORRECTION",
            "input": {"original_text": "원문", "constraints": {}},
        }

        messages = build_runtime_repair_messages(
            "system",
            "검증 실패 사유: {validation_error}\n{previous_output}",
            sample,
            previous_output="{}",
            validation_error="CJK_LEAK",
        )

        self.assertIn("중국어·일본어 문자를 모두 제거", messages[-1]["content"])

    def test_can_emit_bucket_files(self) -> None:
        from build_failure_specific_curriculum import main
        import sys

        raw_row = {
            "id": "sample-1",
            "task_type": "RESUME_EXPRESSION_IMPROVEMENT",
            "input": {
                "original_text": "a" * 200,
                "target_role": "role",
                "job_context": {},
                "user_profile_facts": [],
                "constraints": {
                    "tone": "professional",
                    "min_chars": 10,
                    "target_chars": 20,
                    "max_chars": 40,
                    "preserve_paragraphs": False,
                    "preserve_facts_only": True,
                },
            },
            "output": {
                "status": "ok",
                "task_type": "RESUME_EXPRESSION_IMPROVEMENT",
                "corrected_text": "b" * 20,
                "summary": "summary",
                "changes": [
                    {"before": "a", "after": "b", "reason": "r", "evidence_source": "original_text"},
                    {"before": "c", "after": "d", "reason": "r", "evidence_source": "original_text"},
                    {"before": "e", "after": "f", "reason": "r", "evidence_source": "original_text"},
                ],
                "risk_flags": ["flag"],
                "preserved_meaning": True,
                "added_facts": [],
                "recommended_keywords": [],
                "confidence": 0.9,
            },
        }
        direct_row = {
            "id": "sample-1",
            "task_type": "RESUME_EXPRESSION_IMPROVEMENT",
            "passed": False,
            "problems": ["contract:output.corrected_text length 295 exceeds max_chars 260"],
            "output": "{}",
        }

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            raw_path = tmp_path / "raw.jsonl"
            direct_path = tmp_path / "direct.jsonl"
            out_dir = tmp_path / "out"
            summary_path = tmp_path / "summary.json"
            raw_path.write_text(json.dumps(raw_row, ensure_ascii=False) + "\n", encoding="utf-8")
            direct_path.write_text(json.dumps(direct_row, ensure_ascii=False) + "\n", encoding="utf-8")
            argv = sys.argv[:]
            sys.argv = [
                "build_failure_specific_curriculum.py",
                "--raw",
                str(raw_path),
                "--direct-report",
                str(direct_path),
                "--out-dir",
                str(out_dir),
                "--summary-out",
                str(summary_path),
            ]
            try:
                main()
            finally:
                sys.argv = argv

            self.assertTrue((out_dir / "direct.failed.length_overflow.messages.jsonl").exists())
            self.assertTrue((out_dir / "repair.length_overflow.messages.jsonl").exists())


if __name__ == "__main__":
    unittest.main()

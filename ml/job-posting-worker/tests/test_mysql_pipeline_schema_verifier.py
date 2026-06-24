import importlib.util
import sys
import unittest
from pathlib import Path
from unittest import mock


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "22_verify_mysql_pipeline_schema.py"


def load_script():
    spec = importlib.util.spec_from_file_location("mysql_pipeline_schema_verifier", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class MysqlPipelineSchemaVerifierTest(unittest.TestCase):
    def test_parse_rows_reads_mysql_batch_output(self):
        module = load_script()

        rows = module.parse_rows(
            "application_case_extraction.quality_status\t1\n"
            "ai_runtime_setting.table\t0\n"
            "ignored malformed row\n"
        )

        self.assertEqual(rows["application_case_extraction.quality_status"], 1)
        self.assertEqual(rows["ai_runtime_setting.table"], 0)
        self.assertNotIn("ignored malformed row", rows)

    def test_evaluate_rows_passes_when_all_required_schema_parts_exist(self):
        module = load_script()
        rows = {name: 1 for name in module.expected_checks()}

        summary = module.evaluate_rows(rows, "team1_db")

        self.assertTrue(summary["ok"])
        self.assertEqual(summary["database"], "team1_db")
        self.assertTrue(all(check["passed"] for check in summary["checks"]))

    def test_evaluate_rows_fails_when_schema_part_is_missing(self):
        module = load_script()
        rows = {name: 1 for name in module.expected_checks()}
        rows["application_case_extraction.reviewed_at"] = 0

        summary = module.evaluate_rows(rows, "team1_db")

        self.assertFalse(summary["ok"])
        failed = [check for check in summary["checks"] if not check["passed"]]
        self.assertEqual(failed[0]["name"], "application_case_extraction.reviewed_at")

    def test_run_mysql_reports_stderr_on_client_failure(self):
        module = load_script()

        class Args:
            mysql_bin = "mysql"
            password_env = "DB_PASSWORD"
            host = "127.0.0.1"
            port = 3306
            user = "root"
            database = "team1_db"

        completed = module.subprocess.CompletedProcess(
            args=["mysql"],
            returncode=1,
            stdout="",
            stderr="ERROR 2003 (HY000): Can't connect to MySQL server",
        )
        with mock.patch.object(module.shutil, "which", return_value="mysql"), \
                mock.patch.object(module.subprocess, "run", return_value=completed):
            with self.assertRaisesRegex(RuntimeError, "Can't connect to MySQL server"):
                module.run_mysql(Args)


if __name__ == "__main__":
    unittest.main()

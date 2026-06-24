import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "26_run_release_evidence.py"


def load_script():
    spec = importlib.util.spec_from_file_location("release_evidence_runner", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ReleaseEvidenceRunnerTest(unittest.TestCase):
    def test_collects_all_evidence_and_returns_production_blockers(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = root / "raw"
            ocr = root / "ocr"
            raw.mkdir()
            ocr.mkdir()
            (raw / "posting.pdf").write_bytes(b"%PDF")
            (ocr / "posting.txt").write_text("Company: Acme", encoding="utf-8")

            with mock.patch.object(module.DRILLS, "run_drills", return_value={"ok": True, "failed": 0}), \
                    mock.patch.object(module.STABILIZATION, "run_check", return_value={"passed": False, "total": 1}), \
                    mock.patch.object(module.READINESS, "run_check", return_value={"ok": False}), \
                    mock.patch.object(module.PRODUCTION, "run_audit", return_value={
                        "ready": False,
                        "blockingItems": ["real regression target count"],
                    }):
                summary = module.run_release_evidence(
                    repo_root=root,
                    target_count=3,
                    raw_dir=raw,
                    ocr_dir=ocr,
                    include_runtime_smoke=False,
                    include_db_check=False,
                )

        self.assertFalse(summary["ok"])
        self.assertEqual(summary["blockingItems"], ["real regression target count"])
        step_names = [step["name"] for step in summary["steps"]]
        self.assertIn("worker operational drills", step_names)
        self.assertIn("real regression inventory", step_names)
        self.assertIn("real regression set", step_names)
        self.assertIn("production readiness audit", step_names)

    def test_continues_when_a_step_raises(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = root / "raw"
            ocr = root / "ocr"
            raw.mkdir()
            ocr.mkdir()
            (raw / "posting.pdf").write_bytes(b"%PDF")
            (ocr / "posting.txt").write_text("Company: Acme", encoding="utf-8")

            with mock.patch.object(module.DRILLS, "run_drills", side_effect=RuntimeError("boom")), \
                    mock.patch.object(module.STABILIZATION, "run_check", return_value={"passed": False, "total": 1}), \
                    mock.patch.object(module.READINESS, "run_check", return_value={"ok": False}), \
                    mock.patch.object(module.PRODUCTION, "run_audit", return_value={"ready": False, "blockingItems": []}):
                summary = module.run_release_evidence(
                    repo_root=root,
                    target_count=1,
                    raw_dir=raw,
                    ocr_dir=ocr,
                    include_runtime_smoke=False,
                    include_db_check=False,
                )

        drill_step = next(step for step in summary["steps"] if step["name"] == "worker operational drills")
        self.assertFalse(drill_step["ok"])
        self.assertEqual(drill_step["payload"]["error"], "boom")
        self.assertEqual(summary["steps"][-1]["name"], "production readiness audit")

    def test_db_check_uses_configured_connection_args(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = root / "raw"
            ocr = root / "ocr"
            raw.mkdir()
            ocr.mkdir()
            (raw / "posting.pdf").write_bytes(b"%PDF")
            (ocr / "posting.txt").write_text("Company: Acme", encoding="utf-8")
            seen = {}

            def fake_run_mysql(args):
                seen.update({
                    "host": args.host,
                    "port": args.port,
                    "database": args.database,
                    "user": args.user,
                    "password_env": args.password_env,
                    "mysql_bin": args.mysql_bin,
                })
                return "ok\t1\n"

            with mock.patch.object(module.DRILLS, "run_drills", return_value={"ok": True, "failed": 0}), \
                    mock.patch.object(module.STABILIZATION, "run_check", return_value={"passed": True, "total": 1}), \
                    mock.patch.object(module.READINESS, "run_check", return_value={"ok": True}), \
                    mock.patch.object(module.DB_SCHEMA, "run_mysql", side_effect=fake_run_mysql), \
                    mock.patch.object(module.DB_SCHEMA, "parse_rows", return_value={"ok": 1}), \
                    mock.patch.object(module.DB_SCHEMA, "evaluate_rows", return_value={
                        "ok": True,
                        "database": "stage_db",
                        "checks": [],
                    }), \
                    mock.patch.object(module.PRODUCTION, "run_audit", return_value={
                        "ready": True,
                        "blockingItems": [],
                    }):
                summary = module.run_release_evidence(
                    repo_root=root,
                    target_count=1,
                    raw_dir=raw,
                    ocr_dir=ocr,
                    include_runtime_smoke=False,
                    include_db_check=True,
                    db_host="db.internal",
                    db_port=3307,
                    db_database="stage_db",
                    db_user="ct_user",
                    db_password_env="STAGE_DB_PASSWORD",
                    mysql_bin="mariadb",
                )

        self.assertTrue(summary["ok"])
        self.assertEqual(seen["host"], "db.internal")
        self.assertEqual(seen["port"], 3307)
        self.assertEqual(seen["database"], "stage_db")
        self.assertEqual(seen["user"], "ct_user")
        self.assertEqual(seen["password_env"], "STAGE_DB_PASSWORD")
        self.assertEqual(seen["mysql_bin"], "mariadb")
        db_step = next(step for step in summary["steps"] if step["name"] == "staging DB migration evidence")
        self.assertEqual(db_step["payload"]["mysqlBin"], "mariadb")
        self.assertEqual(db_step["payload"]["host"], "db.internal")


if __name__ == "__main__":
    unittest.main()

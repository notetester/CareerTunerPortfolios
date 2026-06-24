import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "18_check_release_readiness.py"
REPO_ROOT = Path(__file__).resolve().parents[3]


def load_script():
    spec = importlib.util.spec_from_file_location("release_readiness_check", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ReleaseReadinessCheckTest(unittest.TestCase):
    def test_static_readiness_checks_current_repo(self):
        module = load_script()

        summary = module.run_check(REPO_ROOT, include_artifacts=False, min_files=20)

        self.assertTrue(summary["ok"])
        names = {check["name"] for check in summary["checks"]}
        self.assertIn("service pipeline CI", names)
        self.assertIn("compose worker service", names)
        self.assertIn("quality metadata migration", names)
        self.assertIn("self AI automatic analysis pipeline", names)
        self.assertIn("worker OCR image option", names)
        self.assertIn("worker local OCR execution path", names)
        self.assertIn("worker script 23_run_worker_docker_smoke.py", names)
        self.assertIn("worker script 24_run_ocr_runtime_smoke.py", names)
        self.assertIn("worker script 25_prepare_real_regression_set.py", names)
        self.assertIn("worker script 26_run_release_evidence.py", names)
        self.assertIn("worker script 27_summarize_release_evidence.py", names)

    def test_artifact_gate_fails_when_min_file_count_is_too_small(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            stabilization_summary = root / "stabilization_summary.json"
            worker_drills = root / "worker_drills.json"
            stabilization_summary.write_text(json.dumps({
                "total": 20,
                "passed": True,
                "gates": {
                    "minFileCount": True,
                    "passOrReviewRate": True,
                    "failedRate": True,
                },
            }), encoding="utf-8")
            worker_drills.write_text(json.dumps({"ok": True, "failed": 0}), encoding="utf-8")

            summary = module.run_check(
                REPO_ROOT,
                include_artifacts=True,
                min_files=43,
                stabilization_summary=stabilization_summary,
                worker_drills=worker_drills,
            )

        self.assertFalse(summary["ok"])
        failed = [check for check in summary["checks"] if not check["passed"]]
        self.assertEqual(failed[0]["name"], "stabilization min file count")


if __name__ == "__main__":
    unittest.main()

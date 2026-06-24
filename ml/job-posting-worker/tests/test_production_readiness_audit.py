import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "21_audit_production_readiness.py"


def load_script():
    spec = importlib.util.spec_from_file_location("production_readiness_audit", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def write_json(path: Path, payload: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


class ProductionReadinessAuditTest(unittest.TestCase):
    def test_audit_fails_when_production_evidence_is_missing(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            release = root / "release.json"
            inventory = root / "inventory.json"
            stabilization = root / "stabilization.json"
            regression_set = root / "regression-set.json"
            drills = root / "drills.json"
            write_json(release, {"ok": False})
            write_json(inventory, {
                "readyJobPostingCount": 20,
                "uniqueReadyJobPostingCount": 20,
                "duplicateReadyExtraCount": 0,
                "missingOcrJobPostingCount": 2,
                "nonJobReferenceCount": 4,
            })
            write_json(stabilization, {"passed": True, "total": 20})
            write_json(regression_set, {"ok": False, "selectedCount": 20})
            write_json(drills, {"ok": True, "failed": 0})

            summary = module.run_audit(
                root,
                target_count=43,
                release_readiness=release,
                inventory=inventory,
                stabilization_summary=stabilization,
                regression_set=regression_set,
                worker_drills=drills,
            )

        self.assertFalse(summary["ready"])
        self.assertIn("release readiness target-file gate", summary["blockingItems"])
        self.assertIn("real regression target count", summary["blockingItems"])
        self.assertIn("real regression OCR backfill", summary["blockingItems"])
        self.assertIn("real regression set manifest", summary["blockingItems"])
        self.assertIn("worker Docker runtime smoke", summary["blockingItems"])
        self.assertIn("staging DB migration evidence", summary["blockingItems"])
        self.assertNotIn("local docker CLI available", summary["blockingItems"])
        self.assertNotIn("local PaddleOCR runtime available", summary["blockingItems"])

    def test_audit_passes_when_all_required_evidence_is_present(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            release = root / "release.json"
            inventory = root / "inventory.json"
            stabilization = root / "stabilization.json"
            regression_set = root / "regression-set.json"
            drills = root / "drills.json"
            docker_smoke = root / "docker.json"
            ocr_runtime = root / "ocr.json"
            db = root / "db.json"
            write_json(release, {"ok": True})
            write_json(inventory, {
                "readyJobPostingCount": 43,
                "uniqueReadyJobPostingCount": 43,
                "duplicateReadyExtraCount": 0,
                "missingOcrJobPostingCount": 0,
                "nonJobReferenceCount": 4,
            })
            write_json(stabilization, {"passed": True, "total": 43})
            write_json(regression_set, {"ok": True, "selectedCount": 43})
            write_json(drills, {"ok": True, "failed": 0})
            write_json(docker_smoke, {"ok": True})
            write_json(ocr_runtime, {"ok": True})
            write_json(db, {"ok": True})
            with mock.patch.object(module.shutil, "which", return_value="docker"), \
                    mock.patch.object(module, "has_module", return_value=True):
                summary = module.run_audit(
                    root,
                    target_count=43,
                    release_readiness=release,
                    inventory=inventory,
                    stabilization_summary=stabilization,
                    regression_set=regression_set,
                    worker_drills=drills,
                    docker_smoke=docker_smoke,
                    ocr_runtime=ocr_runtime,
                    db_migration=db,
                )

        self.assertTrue(summary["ready"])
        self.assertEqual(summary["blockingItems"], [])

    def test_audit_treats_local_tool_checks_as_diagnostics_when_evidence_passes(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            release = root / "release.json"
            inventory = root / "inventory.json"
            stabilization = root / "stabilization.json"
            regression_set = root / "regression-set.json"
            drills = root / "drills.json"
            docker_smoke = root / "docker.json"
            ocr_runtime = root / "ocr.json"
            db = root / "db.json"
            write_json(release, {"ok": True})
            write_json(inventory, {
                "readyJobPostingCount": 43,
                "uniqueReadyJobPostingCount": 43,
                "duplicateReadyExtraCount": 0,
                "missingOcrJobPostingCount": 0,
                "nonJobReferenceCount": 4,
            })
            write_json(stabilization, {"passed": True, "total": 43})
            write_json(regression_set, {"ok": True, "selectedCount": 43})
            write_json(drills, {"ok": True, "failed": 0})
            write_json(docker_smoke, {"ok": True})
            write_json(ocr_runtime, {"ok": True})
            write_json(db, {"ok": True})
            with mock.patch.object(module.shutil, "which", return_value=None), \
                    mock.patch.object(module, "has_module", return_value=False):
                summary = module.run_audit(
                    root,
                    target_count=43,
                    release_readiness=release,
                    inventory=inventory,
                    stabilization_summary=stabilization,
                    regression_set=regression_set,
                    worker_drills=drills,
                    docker_smoke=docker_smoke,
                    ocr_runtime=ocr_runtime,
                    db_migration=db,
                )

        self.assertTrue(summary["ready"])
        diagnostics = [check for check in summary["checks"] if not check["blocking"]]
        self.assertTrue(diagnostics)
        self.assertTrue(all(not check["passed"] for check in diagnostics))

    def test_audit_blocks_exact_duplicate_ready_postings(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            release = root / "release.json"
            inventory = root / "inventory.json"
            stabilization = root / "stabilization.json"
            regression_set = root / "regression-set.json"
            drills = root / "drills.json"
            docker_smoke = root / "docker.json"
            ocr_runtime = root / "ocr.json"
            db = root / "db.json"
            write_json(release, {"ok": True})
            write_json(inventory, {
                "readyJobPostingCount": 43,
                "uniqueReadyJobPostingCount": 42,
                "duplicateReadyExtraCount": 1,
                "missingOcrJobPostingCount": 0,
                "nonJobReferenceCount": 4,
            })
            write_json(stabilization, {"passed": True, "total": 43})
            write_json(regression_set, {"ok": True, "selectedCount": 43})
            write_json(drills, {"ok": True, "failed": 0})
            write_json(docker_smoke, {"ok": True})
            write_json(ocr_runtime, {"ok": True})
            write_json(db, {"ok": True})

            summary = module.run_audit(
                root,
                target_count=43,
                release_readiness=release,
                inventory=inventory,
                stabilization_summary=stabilization,
                regression_set=regression_set,
                worker_drills=drills,
                docker_smoke=docker_smoke,
                ocr_runtime=ocr_runtime,
                db_migration=db,
            )

        self.assertFalse(summary["ready"])
        self.assertIn("real regression target count", summary["blockingItems"])
        self.assertIn("real regression duplicate check", summary["blockingItems"])


if __name__ == "__main__":
    unittest.main()

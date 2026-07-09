import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "20_audit_real_regression_inventory.py"


def load_script():
    spec = importlib.util.spec_from_file_location("real_regression_inventory_audit", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class RealRegressionInventoryAuditTest(unittest.TestCase):
    def test_inventory_counts_ready_missing_ocr_and_non_job_references(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw_dir = root / "raw"
            ocr_dir = root / "ocr"
            raw_dir.mkdir()
            ocr_dir.mkdir()
            (raw_dir / "backend job posting.pdf").write_bytes(b"%PDF")
            (raw_dir / "frontend job posting.jpg").write_bytes(b"image")
            (raw_dir / "acme 기업정보 - source.pdf").write_bytes(b"%PDF")
            (ocr_dir / "backend job posting.txt").write_text("Responsibilities and qualifications", encoding="utf-8")

            summary = module.audit_inventory(raw_dir, ocr_dir, target_count=3)

        self.assertEqual(summary["rawFileCount"], 3)
        self.assertEqual(summary["jobPostingCandidateCount"], 2)
        self.assertEqual(summary["readyJobPostingCount"], 1)
        self.assertEqual(summary["uniqueReadyJobPostingCount"], 1)
        self.assertEqual(summary["duplicateReadyExtraCount"], 0)
        self.assertEqual(summary["missingOcrJobPostingCount"], 1)
        self.assertEqual(summary["nonJobReferenceCount"], 1)
        self.assertEqual(summary["additionalReadyNeeded"], 2)
        self.assertEqual(summary["ocrBackfillNeeded"], ["frontend job posting.jpg"])
        self.assertEqual(summary["nonJobReferences"], ["acme 기업정보 - source.pdf"])
        self.assertFalse(summary["targetSatisfied"])

    def test_inventory_reports_exact_duplicate_ready_text(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw_dir = root / "raw"
            ocr_dir = root / "ocr"
            raw_dir.mkdir()
            ocr_dir.mkdir()
            (raw_dir / "backend job a.pdf").write_bytes(b"%PDF-a")
            (raw_dir / "backend job b.pdf").write_bytes(b"%PDF-b")
            same_text = "Company: Acme\nRole: Backend Engineer\nResponsibilities: build APIs\n"
            (ocr_dir / "backend job a.txt").write_text(same_text, encoding="utf-8")
            (ocr_dir / "backend job b.txt").write_text(same_text, encoding="utf-8")

            summary = module.audit_inventory(raw_dir, ocr_dir, target_count=2)

        self.assertEqual(summary["readyJobPostingCount"], 2)
        self.assertEqual(summary["uniqueReadyJobPostingCount"], 1)
        self.assertEqual(summary["duplicateReadyGroupCount"], 1)
        self.assertEqual(summary["duplicateReadyExtraCount"], 1)
        self.assertEqual(
            summary["duplicateReadyGroups"][0]["files"],
            ["backend job a.pdf", "backend job b.pdf"],
        )
        self.assertEqual(summary["additionalReadyNeeded"], 1)
        self.assertFalse(summary["targetSatisfied"])


if __name__ == "__main__":
    unittest.main()

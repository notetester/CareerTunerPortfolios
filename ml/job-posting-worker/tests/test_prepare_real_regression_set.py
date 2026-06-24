import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "25_prepare_real_regression_set.py"


def load_script():
    spec = importlib.util.spec_from_file_location("prepare_real_regression_set", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class PrepareRealRegressionSetTest(unittest.TestCase):
    def test_prepares_only_ready_job_posting_files(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = root / "raw"
            ocr = root / "ocr"
            raw_out = root / "out" / "raw"
            ocr_out = root / "out" / "ocr"
            raw.mkdir()
            ocr.mkdir()
            (raw / "backend job.pdf").write_bytes(b"%PDF")
            (raw / "frontend job.txt").write_text("Company: Acme", encoding="utf-8")
            (raw / "company info.pdf").write_bytes(b"%PDF")
            (raw / "missing ocr.pdf").write_bytes(b"%PDF")
            (ocr / "backend job.txt").write_text("Company: Acme\nResponsibilities: build APIs", encoding="utf-8")

            summary = module.prepare_regression_set(
                repo_root=root,
                raw_dir=raw,
                ocr_dir=ocr,
                raw_output_dir=raw_out,
                ocr_output_dir=ocr_out,
                target_count=2,
                clean=True,
            )

        self.assertTrue(summary["ok"])
        self.assertEqual(summary["selectedCount"], 2)
        self.assertEqual(summary["duplicateSkippedCount"], 0)
        self.assertEqual([item["fileName"] for item in summary["copied"]], ["backend job.pdf", "frontend job.txt"])
        self.assertEqual(summary["inventory"]["missingOcrJobPostingCount"], 1)

    def test_reports_additional_ready_needed_when_target_is_not_met(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = root / "raw"
            ocr = root / "ocr"
            raw_out = root / "out" / "raw"
            ocr_out = root / "out" / "ocr"
            raw.mkdir()
            ocr.mkdir()
            (raw / "backend job.pdf").write_bytes(b"%PDF")
            (ocr / "backend job.txt").write_text("Company: Acme", encoding="utf-8")

            summary = module.prepare_regression_set(
                repo_root=root,
                raw_dir=raw,
                ocr_dir=ocr,
                raw_output_dir=raw_out,
                ocr_output_dir=ocr_out,
                target_count=3,
                clean=True,
            )

        self.assertFalse(summary["ok"])
        self.assertEqual(summary["selectedCount"], 1)
        self.assertEqual(summary["additionalReadyNeeded"], 2)
        self.assertIn("--min-files 3", summary["stabilizationCommand"])

    def test_skips_exact_duplicate_ready_text_when_preparing_set(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = root / "raw"
            ocr = root / "ocr"
            raw_out = root / "out" / "raw"
            ocr_out = root / "out" / "ocr"
            raw.mkdir()
            ocr.mkdir()
            (raw / "backend job a.pdf").write_bytes(b"%PDF-a")
            (raw / "backend job b.pdf").write_bytes(b"%PDF-b")
            same_text = "Company: Acme\nRole: Backend Engineer\nResponsibilities: build APIs\n"
            (ocr / "backend job a.txt").write_text(same_text, encoding="utf-8")
            (ocr / "backend job b.txt").write_text(same_text, encoding="utf-8")

            summary = module.prepare_regression_set(
                repo_root=root,
                raw_dir=raw,
                ocr_dir=ocr,
                raw_output_dir=raw_out,
                ocr_output_dir=ocr_out,
                target_count=2,
                clean=True,
            )

            self.assertFalse(summary["ok"])
            self.assertEqual(summary["selectedCount"], 1)
            self.assertEqual(summary["duplicateSkippedCount"], 1)
            self.assertEqual(summary["duplicateSkipped"], ["backend job b.pdf"])
            self.assertEqual([item["fileName"] for item in summary["copied"]], ["backend job a.pdf"])
            self.assertEqual(summary["inventory"]["duplicateReadyExtraCount"], 1)

    def test_main_writes_manifest(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = root / "raw"
            ocr = root / "ocr"
            raw_out = root / "out" / "raw"
            ocr_out = root / "out" / "ocr"
            manifest = root / "manifest.json"
            raw.mkdir()
            ocr.mkdir()
            (raw / "backend job.pdf").write_bytes(b"%PDF")
            (ocr / "backend job.txt").write_text("Company: Acme", encoding="utf-8")

            summary = module.prepare_regression_set(
                repo_root=root,
                raw_dir=raw,
                ocr_dir=ocr,
                raw_output_dir=raw_out,
                ocr_output_dir=ocr_out,
                target_count=1,
                clean=True,
            )
            manifest.write_text(json.dumps(summary, ensure_ascii=False), encoding="utf-8")

            payload = json.loads(manifest.read_text(encoding="utf-8"))
            self.assertTrue(payload["ok"])
            self.assertEqual(payload["selectedCount"], 1)


if __name__ == "__main__":
    unittest.main()

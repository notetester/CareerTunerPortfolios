import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "28_import_real_regression_candidates.py"


def load_script():
    spec = importlib.util.spec_from_file_location("import_real_regression_candidates", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class FakeExtractor:
    @staticmethod
    def output_paths(input_path, output_dir):
        return output_dir / f"{input_path.stem}.txt", output_dir / f"{input_path.stem}.meta.json"

    @staticmethod
    def extract_document(input_path, output_dir, existing_ocr_dir=None):
        text_path, _ = FakeExtractor.output_paths(input_path, output_dir)
        text_path.write_text(
            "Company: Acme\nRole: Backend Engineer\nResponsibilities: build APIs\n" * 5,
            encoding="utf-8",
        )
        return {
            "qualityStatus": "PASS",
            "qualityScore": 88,
            "textSource": "PADDLE_OCR",
        }


class ImportRealRegressionCandidatesTest(unittest.TestCase):
    def test_imports_new_posting_and_backfills_ocr(self):
        module = load_script()
        module.EXTRACTOR = FakeExtractor
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source"
            raw = root / "raw"
            ocr = root / "ocr"
            source.mkdir()
            raw.mkdir()
            ocr.mkdir()
            (source / "new job.pdf").write_bytes(b"%PDF-new")
            (source / "company info.pdf").write_bytes(b"%PDF-company")
            (source / "duplicate.pdf").write_bytes(b"%PDF-existing")
            (raw / "existing.pdf").write_bytes(b"%PDF-existing")
            (ocr / "existing.txt").write_text("Company: Existing", encoding="utf-8")

            summary = module.import_candidates(
                [source],
                raw,
                ocr,
                recursive=False,
                dry_run=False,
            )

            self.assertTrue(summary["ok"])
            self.assertEqual(summary["importedCount"], 1)
            self.assertEqual(summary["readyImportedCount"], 1)
            statuses = {item["fileName"]: item["importStatus"] for item in summary["items"]}
            self.assertEqual(statuses["new job.pdf"], "imported")
            self.assertEqual(statuses["company info.pdf"], "non_job_reference_document")
            self.assertEqual(statuses["duplicate.pdf"], "duplicate_content")
            self.assertTrue((raw / "new job.pdf").exists())
            self.assertTrue((ocr / "new job.txt").exists())
            self.assertEqual(summary["inventory"]["readyJobPostingCount"], 2)

    def test_dry_run_does_not_copy_or_extract(self):
        module = load_script()

        class FailingExtractor:
            @staticmethod
            def extract_document(*args, **kwargs):
                raise AssertionError("dry-run must not OCR")

        module.EXTRACTOR = FailingExtractor
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source"
            raw = root / "raw"
            ocr = root / "ocr"
            source.mkdir()
            raw.mkdir()
            ocr.mkdir()
            (source / "new job.pdf").write_bytes(b"%PDF-new")

            summary = module.import_candidates(
                [source],
                raw,
                ocr,
                recursive=False,
                dry_run=True,
            )

            self.assertTrue(summary["ok"])
            self.assertEqual(summary["importedCount"], 1)
            self.assertFalse((raw / "new job.pdf").exists())
            self.assertFalse((ocr / "new job.txt").exists())
            self.assertIsNone(summary["inventory"])

    def test_recursive_discovery_excludes_synthetic_and_generated_outputs(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source"
            raw = root / "raw"
            ocr = root / "ocr"
            cleaned = source / "data" / "cleaned"
            ocr_improvement = source / "data" / "real_validation" / "ocr_improvement" / "tiles"
            outputs = source / "data" / "outputs" / "job_analysis_gold"
            raw_jobs = source / "data" / "raw" / "job_postings"
            real = source / "real"
            cleaned.mkdir(parents=True)
            ocr_improvement.mkdir(parents=True)
            outputs.mkdir(parents=True)
            raw_jobs.mkdir(parents=True)
            real.mkdir(parents=True)
            raw.mkdir()
            ocr.mkdir()
            (cleaned / "syn-company-ai-domain-001.txt").write_text(
                "SYNTHETIC_JOB_POSTING\nCompany: synthetic",
                encoding="utf-8",
            )
            (outputs / "syn-company-ai-domain-001.txt").write_text("Company: synthetic", encoding="utf-8")
            (ocr_improvement / "captured_job__tile_001.png").write_bytes(b"tile")
            (raw_jobs / "job-003.pdf").write_bytes(b"%PDF-synthetic")
            (raw_jobs / "source_index.md").write_text(
                "| job-003.pdf | LEGACY_SYNTHETIC_PDF |\n",
                encoding="utf-8",
            )
            (real / "new job.txt").write_text("Company: Acme\nRole: Backend Engineer\n", encoding="utf-8")

            summary = module.import_candidates(
                [source],
                raw,
                ocr,
                recursive=True,
                dry_run=True,
            )

            self.assertTrue(summary["ok"])
            self.assertEqual(summary["sourceCount"], 1)
            self.assertEqual(summary["importedCount"], 1)
            self.assertEqual(summary["items"][0]["fileName"], "new job.txt")

    def test_failed_ocr_candidate_is_not_copied_to_raw_inventory(self):
        module = load_script()

        class FailedExtractor:
            @staticmethod
            def output_paths(input_path, output_dir):
                return output_dir / f"{input_path.stem}.txt", output_dir / f"{input_path.stem}.meta.json"

            @staticmethod
            def extract_document(input_path, output_dir, existing_ocr_dir=None):
                text_path, _ = FailedExtractor.output_paths(input_path, output_dir)
                text_path.write_text("", encoding="utf-8")
                return {
                    "qualityStatus": "FAILED",
                    "qualityScore": 12,
                    "warnings": ["blank_extraction"],
                }

        module.EXTRACTOR = FailedExtractor
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source"
            raw = root / "raw"
            ocr = root / "ocr"
            source.mkdir()
            raw.mkdir()
            ocr.mkdir()
            (source / "bad scan.pdf").write_bytes(b"%PDF-bad")

            summary = module.import_candidates(
                [source],
                raw,
                ocr,
                recursive=False,
                dry_run=False,
            )

            self.assertTrue(summary["ok"])
            self.assertEqual(summary["importedCount"], 0)
            self.assertEqual(summary["skippedCount"], 1)
            self.assertEqual(summary["items"][0]["importStatus"], "ocr_quality_rejected")
            self.assertEqual(summary["items"][0]["ocr"]["status"], "failed_quality_gate")
            self.assertFalse((raw / "bad scan.pdf").exists())
            self.assertFalse((ocr / "bad scan.txt").exists())


if __name__ == "__main__":
    unittest.main()

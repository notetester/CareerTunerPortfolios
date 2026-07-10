import importlib.util
import sys
import unittest
from pathlib import Path
from unittest import mock


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "24_run_ocr_runtime_smoke.py"


def load_script():
    spec = importlib.util.spec_from_file_location("ocr_runtime_smoke", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class OcrRuntimeSmokeTest(unittest.TestCase):
    def test_accepts_layout_and_line_ocr_sources(self):
        module = load_script()
        text = "Company: Acme\nResponsibilities: build production APIs\n" * 3

        self.assertTrue(module.extraction_ok(
            {"textSource": "PPSTRUCTURE", "qualityStatus": "REVIEW_REQUIRED"},
            text,
        ))
        self.assertTrue(module.extraction_ok(
            {"textSource": "PADDLE_OCR", "qualityStatus": "PASS"},
            text,
        ))

    def test_reports_missing_ocr_modules(self):
        module = load_script()

        with mock.patch.object(module, "has_module", return_value=False):
            summary = module.run_smoke(Path.cwd(), lang="en")

        self.assertFalse(summary["ok"])
        self.assertEqual(summary["error"], "required OCR runtime modules are not installed")

    def test_passes_when_runtime_extraction_succeeds(self):
        module = load_script()

        class FakeExtractionOptions:
            def __init__(self, enable_paddle_ocr=True, paddle_ocr_lang="en"):
                self.enable_paddle_ocr = enable_paddle_ocr
                self.paddle_ocr_lang = paddle_ocr_lang

        class FakeExtractionModule:
            ExtractionOptions = FakeExtractionOptions

            @staticmethod
            def extract_document(input_path, output_dir, existing_ocr_dir=None, options=None):
                output_dir.mkdir(parents=True, exist_ok=True)
                output_name = f"{input_path.stem}.txt"
                (output_dir / output_name).write_text(
                    "Company: Acme\nRole: Backend Engineer\nResponsibilities: build production APIs\n" * 4,
                    encoding="utf-8",
                )
                return {"textSource": "PADDLE_OCR", "qualityStatus": "PASS"}

        with mock.patch.object(module, "has_module", return_value=True), \
                mock.patch.object(module, "create_sample_image"), \
                mock.patch.object(module, "create_sample_pdf"), \
                mock.patch.object(module, "load_extraction_module", return_value=FakeExtractionModule):
            summary = module.run_smoke(Path.cwd(), lang="en")

        self.assertTrue(summary["ok"])
        self.assertEqual(summary["checks"][-2]["name"], "PaddleOCR image extraction")
        self.assertEqual(summary["checks"][-1]["name"], "PaddleOCR image PDF extraction")


if __name__ == "__main__":
    unittest.main()

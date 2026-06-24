import importlib.util
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "14_extract_document_text.py"
FIXTURE_PATH = Path(__file__).resolve().parent / "fixtures" / "quality_gate_cases.json"


def load_script():
    spec = importlib.util.spec_from_file_location("document_text_extraction", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class DocumentTextExtractionTest(unittest.TestCase):
    def test_configures_writable_ocr_cache_environment(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            cache = Path(tmp) / "ocr-cache"
            original = {
                "HOME": os.environ.get("HOME"),
                "USERPROFILE": os.environ.get("USERPROFILE"),
                "XDG_CACHE_HOME": os.environ.get("XDG_CACHE_HOME"),
                "PADDLE_OCR_BASE_DIR": os.environ.get("PADDLE_OCR_BASE_DIR"),
            }
            try:
                for key in original:
                    os.environ.pop(key, None)

                configured = module.configure_ocr_cache_env(cache)

                self.assertEqual(configured, cache)
                self.assertEqual(os.environ["HOME"], str(cache))
                self.assertEqual(os.environ["USERPROFILE"], str(cache))
                self.assertEqual(os.environ["XDG_CACHE_HOME"], str(cache / ".cache"))
                self.assertEqual(os.environ["PADDLE_OCR_BASE_DIR"], str(cache / ".paddleocr"))
                self.assertTrue(cache.exists())
            finally:
                for key, value in original.items():
                    if value is None:
                        os.environ.pop(key, None)
                    else:
                        os.environ[key] = value

    def test_quality_gate_shared_fixture_statuses_match_backend_contract(self):
        module = load_script()
        cases = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))

        for case in cases:
            with self.subTest(case=case["name"]):
                analysis = module.analyze_quality(case["text"])

                self.assertEqual(analysis["qualityStatus"], case["expectedStatus"])

    def test_scores_clean_text_as_pass_and_writes_contract_files(self):
        module = load_script()
        text = "\n".join(
            [
                "모집부문",
                "백엔드 개발자",
                "담당업무",
                "Spring Boot 기반 API를 개발하고 MySQL 쿼리를 개선합니다.",
                "자격요건",
                "Java와 SQL 경험이 필요합니다.",
                "우대사항",
                "AWS 운영 경험을 우대합니다.",
                "근무조건",
                "정규직이며 주 5일 근무합니다.",
            ]
        )
        text = text + "\n" + ("서비스 운영 경험을 바탕으로 장애를 분석하고 개선합니다. " * 16)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            input_path = root / "posting.txt"
            output_dir = root / "out"
            input_path.write_text(text, encoding="utf-8")

            result = module.extract_document(input_path=input_path, output_dir=output_dir)

            self.assertEqual(result["strategy"], "TEXT_DIRECT")
            self.assertEqual(result["qualityStatus"], "PASS")
            self.assertGreaterEqual(result["qualityScore"], 70)
            self.assertFalse(result["fallbackEligible"])
            self.assertTrue((output_dir / "posting.txt").exists())
            meta = json.loads((output_dir / "posting.meta.json").read_text(encoding="utf-8"))
            self.assertIn("metrics", meta)
            self.assertIn("sectionHints", meta)
            self.assertEqual(meta["qualityStatus"], "PASS")

    def test_marks_short_or_empty_extraction_as_failed(self):
        module = load_script()

        analysis = module.analyze_quality("로그인\n회원가입\n")

        self.assertEqual(analysis["qualityStatus"], "FAILED")
        self.assertLess(analysis["qualityScore"], 40)
        self.assertIn("text_too_short", analysis["warnings"])

    def test_korean_joined_ocr_job_sections_require_review_instead_of_failed(self):
        module = load_script()
        text = "\n".join(
            [
                "상시채용",
                "홈페이지 지원",
                "근무지역",
                "근무형태",
                "합류하면함께할업무예요",
                "리더십과동행하며미팅의흐름을파악하고 필요한지원을선제적으로제공해요",
                "이력서는이렇게작성해주세요",
                "포지션에지원한동기를들려주세요",
                "지원방법",
                "홈페이지지원",
                "로그인 공유하기 추천공고 지도 스크랩",
            ]
        )
        text = text + "\n" + ("조직을지원하며업무가매끄럽게진행되도록돕는경험을확인합니다. " * 30)

        analysis = module.analyze_quality(text)

        self.assertIn(analysis["qualityStatus"], {"PASS", "REVIEW_REQUIRED"})
        self.assertGreaterEqual(analysis["qualityScore"], 40)
        self.assertNotIn("section_keywords_missing", analysis["warnings"])

    def test_classifies_long_image_and_uses_existing_ocr_text(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            image_path = root / "long.jpg"
            existing_ocr_dir = root / "ocr"
            output_dir = root / "out"
            existing_ocr_dir.mkdir()
            Image.new("RGB", (540, 5000), "white").save(image_path)
            (existing_ocr_dir / "long.txt").write_text(
                "담당업무\n데이터 파이프라인 운영\n자격요건\nPython 경험\n",
                encoding="utf-8",
            )

            result = module.extract_document(
                input_path=image_path,
                output_dir=output_dir,
                existing_ocr_dir=existing_ocr_dir,
            )

            self.assertEqual(result["strategy"], "LONG_IMAGE_TILING")
            self.assertEqual(result["textSource"], "EXISTING_OCR")
            self.assertTrue(result["fallbackEligible"])
            self.assertEqual((output_dir / "long.txt").read_text(encoding="utf-8").splitlines()[0], "담당업무")

    def test_pdf_without_extractable_text_is_image_pdf_ocr_candidate(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pdf_path = root / "scan.pdf"
            output_dir = root / "out"
            pdf_path.write_bytes(b"%PDF-1.4\n% image-only placeholder\n")

            result = module.extract_document(
                input_path=pdf_path,
                output_dir=output_dir,
                options=module.ExtractionOptions(enable_paddle_ocr=False),
            )

            self.assertEqual(result["strategy"], "IMAGE_PDF_OCR")
            self.assertEqual(result["qualityStatus"], "FAILED")
            self.assertTrue(result["fallbackEligible"])
            self.assertIn("ocr_not_executed", result["warnings"])

    def test_image_pdf_uses_local_paddleocr_when_existing_ocr_is_missing(self):
        module = load_script()

        class FakePaddleOCR:
            def __init__(self, **kwargs):
                self.kwargs = kwargs

            def ocr(self, path, cls=True):
                text = [
                    "Company: Acme",
                    "Role: Backend Engineer",
                    "Responsibilities: build APIs and operate services",
                    "Qualifications: Java Spring MySQL Docker testing",
                    "Skills: Java Spring MySQL Docker Python React TypeScript",
                    "Apply before deadline",
                ]
                repeated = text + [
                    f"Responsibilities detail {index}: production ownership and collaboration experience required"
                    for index in range(12)
                ]
                return [[None, [line, 0.98]] for line in repeated]

        module.load_paddleocr_class = lambda: FakePaddleOCR
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pdf_path = root / "scan.pdf"
            output_dir = root / "out"
            pdf_path.write_bytes(b"%PDF-1.4\n% image-only placeholder\n")

            result = module.extract_document(input_path=pdf_path, output_dir=output_dir)

            self.assertEqual(result["strategy"], "IMAGE_PDF_OCR")
            self.assertEqual(result["textSource"], "PADDLE_OCR")
            self.assertEqual(result["qualityStatus"], "PASS")
            self.assertGreaterEqual(result["qualityScore"], 70)
            self.assertEqual(result["modelVersions"]["ocr"], "existing_output_or_paddleocr")
            self.assertIn("Responsibilities", (output_dir / "scan.txt").read_text(encoding="utf-8"))

    def test_batch_run_writes_report_without_warning_format_collision(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            input_dir = root / "input"
            output_dir = root / "out"
            report_path = root / "report.md"
            input_dir.mkdir()
            (input_dir / "posting.txt").write_text(
                "\n".join([
                    "Company: Acme",
                    "Role: Backend Engineer",
                    "Responsibilities: build APIs and operate services",
                    "Qualifications: Java Spring MySQL Docker testing",
                    "Skills: Java Spring MySQL Docker Python React TypeScript",
                ])
                + "\n"
                + "\n".join(f"Responsibilities detail {index}: production engineering ownership" for index in range(12)),
                encoding="utf-8",
            )

            exit_code = module.run(
                input_path=input_dir,
                output_dir=output_dir,
                existing_ocr_dir=None,
                report_path=report_path,
                limit=None,
            )

            self.assertEqual(exit_code, 0)
            self.assertTrue(report_path.exists())
            self.assertIn("posting.txt", report_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()

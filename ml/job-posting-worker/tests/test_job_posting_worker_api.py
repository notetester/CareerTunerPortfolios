import base64
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "15_job_posting_worker_api.py"


def load_script():
    spec = importlib.util.spec_from_file_location("job_posting_worker_api", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def passing_text() -> str:
    return "\n".join(
        [
            "Company: Acme",
            "Role: Backend Engineer",
            "Responsibilities: Build Spring APIs and operate batch workers.",
            "Qualifications: Java, Spring Boot, MySQL, testing, and production debugging.",
            "Skills: Java Spring MyBatis React TypeScript Python Docker monitoring.",
            "Employment: full-time Seoul hybrid role with benefits.",
            "Apply: submit resume before the deadline.",
            "Deadline: 2026-07-31",
        ]
    ) + "\n" + ("Commercial service operation and API improvement experience required. " * 10)


class JobPostingWorkerApiTest(unittest.TestCase):
    def test_extracts_text_payload_without_openai(self):
        module = load_script()

        result = module.extract_job_posting({"sourceType": "TEXT", "text": passing_text()})

        self.assertEqual(result["strategy"], "TEXT_DIRECT")
        self.assertEqual(result["qualityStatus"], "PASS")
        self.assertGreaterEqual(result["qualityScore"], 70)
        self.assertFalse(result["fallbackEligible"])
        self.assertIn("qualityStatus", result["meta"])

    def test_extracts_file_payload_using_document_contract(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            posting = root / "posting.txt"
            posting.write_text(passing_text(), encoding="utf-8")

            result = module.extract_job_posting({"sourceType": "TEXT", "filePath": str(posting)})

            self.assertEqual(result["strategy"], "TEXT_DIRECT")
            self.assertEqual(result["qualityStatus"], "PASS")
            self.assertIn("Responsibilities", result["text"])

    def test_http_handler_payload_is_json_serializable(self):
        module = load_script()

        result = module.extract_job_posting({"sourceType": "URL", "html": "<main><h1>Role</h1></main>"})

        encoded = json.dumps(result, ensure_ascii=False)
        self.assertIn("HTML_TEXT", encoded)
        self.assertEqual(result["qualityStatus"], "FAILED")

    def test_error_response_uses_same_metadata_contract(self):
        module = load_script()

        result = module.error_response(ValueError("bad request"))

        self.assertEqual(result["qualityStatus"], "FAILED")
        self.assertEqual(result["meta"]["qualityStatus"], "FAILED")
        self.assertEqual(result["strategy"], "WORKER_ERROR")
        self.assertIn("metrics", result)
        self.assertIn("sectionHints", result)
        self.assertIn("generatedAt", result)
        self.assertIn("modelVersions", result)
        self.assertFalse(result["fallbackEligible"])
        self.assertIn("worker_error:ValueError", result["warnings"])

    def test_file_base64_takes_priority_over_file_path(self):
        module = load_script()
        encoded = base64.b64encode(passing_text().encode("utf-8")).decode("ascii")

        # filePath 는 존재하지 않는 경로 → fileBase64 를 우선 사용해 디코드→temp file→추출까지 성공해야 한다.
        result = module.extract_job_posting(
            {
                "sourceType": "TEXT",
                "fileName": "posting.txt",
                "fileBase64": encoded,
                "filePath": "/nonexistent/should-not-be-read.txt",
            }
        )

        self.assertEqual(result["strategy"], "TEXT_DIRECT")
        self.assertEqual(result["qualityStatus"], "PASS")
        self.assertIn("Responsibilities", result["text"])

    def test_invalid_base64_returns_invalid_base64_source(self):
        module = load_script()

        result = module.extract_job_posting(
            {
                "sourceType": "IMAGE",
                "fileName": "broken.png",
                "fileBase64": "@@@ not valid base64 @@@",
            }
        )

        # DOCUMENT 호출 전에 결정론 에러로 빠져야 한다(디코드 실패).
        self.assertEqual(result["text"], "")
        self.assertIn("invalid_base64", result["warnings"])


if __name__ == "__main__":
    unittest.main()

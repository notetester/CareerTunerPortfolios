import http.client
import importlib.util
import json
import sys
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "15_job_posting_worker_api.py"


def load_script():
    spec = importlib.util.spec_from_file_location("job_posting_worker_http_api", SCRIPT_PATH)
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


class WorkerHttpServerTest(unittest.TestCase):
    def test_health_and_extract_endpoints_return_contract_json(self):
        module = load_script()
        server = ThreadingHTTPServer(("127.0.0.1", 0), module.WorkerHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        port = server.server_address[1]
        try:
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
            connection.request("GET", "/health")
            health = connection.getresponse()
            self.assertEqual(health.status, 200)
            self.assertEqual(json.loads(health.read().decode("utf-8"))["status"], "ok")

            body = json.dumps({"sourceType": "TEXT", "text": passing_text()})
            connection.request(
                "POST",
                "/extract/job-posting",
                body=body,
                headers={"Content-Type": "application/json"},
            )
            response = connection.getresponse()
            payload = json.loads(response.read().decode("utf-8"))

            self.assertEqual(response.status, 200)
            self.assertEqual(payload["qualityStatus"], "PASS")
            self.assertEqual(payload["meta"]["qualityStatus"], "PASS")
            self.assertIn("metrics", payload["meta"])
            self.assertFalse(payload["meta"]["fallbackEligible"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_oversized_request_returns_failed_contract(self):
        module = load_script()
        server = ThreadingHTTPServer(("127.0.0.1", 0), module.WorkerHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        port = server.server_address[1]
        try:
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
            connection.putrequest("POST", "/extract/job-posting")
            connection.putheader("Content-Type", "application/json")
            connection.putheader("Content-Length", str(module.MAX_REQUEST_BODY_BYTES + 1))
            connection.endheaders()
            response = connection.getresponse()
            payload = json.loads(response.read().decode("utf-8"))

            self.assertEqual(response.status, 500)
            self.assertEqual(payload["qualityStatus"], "FAILED")
            self.assertEqual(payload["strategy"], "WORKER_ERROR")
            self.assertIn("worker_error:ValueError", payload["warnings"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_malformed_json_request_returns_failed_contract(self):
        module = load_script()
        server = ThreadingHTTPServer(("127.0.0.1", 0), module.WorkerHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        port = server.server_address[1]
        try:
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
            connection.request(
                "POST",
                "/extract/job-posting",
                body="{broken-json",
                headers={"Content-Type": "application/json"},
            )
            response = connection.getresponse()
            payload = json.loads(response.read().decode("utf-8"))

            self.assertEqual(response.status, 500)
            self.assertEqual(payload["qualityStatus"], "FAILED")
            self.assertEqual(payload["strategy"], "WORKER_ERROR")
            self.assertIn("worker_error:JSONDecodeError", payload["warnings"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()

import http.client
import importlib.util
import json
import sys
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest import mock


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

    def test_capabilities_endpoint_routes_and_returns_probe_json(self):
        # 라우팅 + JSON 직렬화만 검증한다. 실제 엔진 초기화는 probe_capabilities() 를 mock 해
        # 배제한다(Paddle 설치 환경에서 첫 초기화가 길어져 flaky 해지는 것 방지 — 초기화 판정은
        # WorkerCapabilitiesProbeTest 전담).
        module = load_script()
        fake_capabilities = {
            "status": "ok",
            "engines": {
                "paddleocr": {"ready": True},
                "ppstructure": {"ready": False, "reason": "not_installed"},
            },
            "readyEngines": ["paddleocr"],
        }
        server = ThreadingHTTPServer(("127.0.0.1", 0), module.WorkerHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        port = server.server_address[1]
        try:
            with mock.patch.object(module, "probe_capabilities", return_value=fake_capabilities):
                connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
                connection.request("GET", "/capabilities")
                response = connection.getresponse()
                payload = json.loads(response.read().decode("utf-8"))

            self.assertEqual(response.status, 200)
            self.assertEqual(payload, fake_capabilities)
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


class WorkerCapabilitiesProbeTest(unittest.TestCase):
    """probe_capabilities() 의 실제 엔진 초기화 판정·캐시 동작을 mock 으로 검증한다(paddle 미설치 환경에서도 결정적)."""

    def setUp(self):
        self.module = load_script()
        self.module._CAPABILITIES_CACHE = None

    @staticmethod
    def _fake_find_spec(installed: bool):
        def fake(name, *args, **kwargs):
            # probe 는 paddle/paddleocr 만 조회한다. 그 외 이름은 조회되지 않으므로 None.
            if name in ("paddle", "paddleocr"):
                return object() if installed else None
            return None
        return fake

    def test_missing_packages_report_not_installed(self):
        paddle_factory = mock.Mock()
        ppstructure_factory = mock.Mock()
        with mock.patch("importlib.util.find_spec", side_effect=self._fake_find_spec(False)), \
                mock.patch.object(self.module.DOCUMENT, "create_paddle_ocr", paddle_factory), \
                mock.patch.object(self.module.DOCUMENT, "create_ppstructure", ppstructure_factory):
            result = self.module.probe_capabilities()

        self.assertFalse(result["engines"]["paddleocr"]["ready"])
        self.assertFalse(result["engines"]["ppstructure"]["ready"])
        self.assertEqual(result["engines"]["paddleocr"]["reason"], "not_installed")
        self.assertEqual(result["engines"]["ppstructure"]["reason"], "not_installed")
        self.assertEqual(result["readyEngines"], [])
        # 미설치면 실제 엔진 초기화는 시도하지 않는다(무거운 로딩 회피).
        paddle_factory.assert_not_called()
        ppstructure_factory.assert_not_called()

    def test_single_engine_init_failure_marks_only_that_engine(self):
        with mock.patch("importlib.util.find_spec", side_effect=self._fake_find_spec(True)), \
                mock.patch.object(self.module.DOCUMENT, "create_paddle_ocr", mock.Mock()), \
                mock.patch.object(self.module.DOCUMENT, "create_ppstructure",
                                  mock.Mock(side_effect=RuntimeError("ppstructure init failed"))):
            result = self.module.probe_capabilities()

        self.assertTrue(result["engines"]["paddleocr"]["ready"])
        self.assertFalse(result["engines"]["ppstructure"]["ready"])
        self.assertEqual(result["engines"]["ppstructure"]["reason"], "RuntimeError")
        self.assertEqual(result["readyEngines"], ["paddleocr"])

    def test_ready_engines_matches_successful_engines(self):
        with mock.patch("importlib.util.find_spec", side_effect=self._fake_find_spec(True)), \
                mock.patch.object(self.module.DOCUMENT, "create_paddle_ocr", mock.Mock()), \
                mock.patch.object(self.module.DOCUMENT, "create_ppstructure", mock.Mock()):
            result = self.module.probe_capabilities()

        expected_ready = sorted(name for name, info in result["engines"].items() if info["ready"])
        self.assertEqual(sorted(result["readyEngines"]), expected_ready)
        self.assertEqual(sorted(result["readyEngines"]), ["paddleocr", "ppstructure"])

    def test_probe_result_is_cached_across_calls(self):
        paddle_factory = mock.Mock()
        ppstructure_factory = mock.Mock()
        with mock.patch("importlib.util.find_spec", side_effect=self._fake_find_spec(True)), \
                mock.patch.object(self.module.DOCUMENT, "create_paddle_ocr", paddle_factory), \
                mock.patch.object(self.module.DOCUMENT, "create_ppstructure", ppstructure_factory):
            first = self.module.probe_capabilities()
            second = self.module.probe_capabilities()

        self.assertIs(first, second)  # 동일 캐시 객체 재사용
        # 재호출 시 엔진 초기화를 다시 실행하지 않는다.
        self.assertEqual(paddle_factory.call_count, 1)
        self.assertEqual(ppstructure_factory.call_count, 1)


if __name__ == "__main__":
    unittest.main()

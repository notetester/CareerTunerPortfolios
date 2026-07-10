import importlib.util
import sys
import unittest
from pathlib import Path
from unittest import mock


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "23_run_worker_docker_smoke.py"


def load_script():
    spec = importlib.util.spec_from_file_location("worker_docker_smoke", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class WorkerDockerSmokeTest(unittest.TestCase):
    def test_reports_missing_docker_cli(self):
        module = load_script()

        with mock.patch.object(module.shutil, "which", return_value=None):
            summary = module.run_smoke(
                Path.cwd(),
                image="worker:test",
                container_name="worker-test",
                host_port=8091,
                install_ocr=False,
                skip_build=False,
                attempts=1,
                delay_seconds=0,
            )

        self.assertFalse(summary["ok"])
        self.assertEqual(summary["error"], "docker CLI not found")

    def test_command_result_handles_missing_streams(self):
        module = load_script()

        payload = module.CommandResult(["docker", "build"], 1, None, None).to_dict()

        self.assertEqual(payload["stdout"], "")
        self.assertEqual(payload["stderr"], "")
        self.assertFalse(payload["ok"])

    def test_runs_build_container_health_and_extraction_checks(self):
        module = load_script()

        def fake_run_command(command, **kwargs):
            return module.CommandResult(command=list(command), returncode=0, stdout="ok", stderr="")

        def fake_request_json(url, payload=None, timeout=5.0):
            if url.endswith("/health"):
                return {"status": "UP"}
            return {"text": "Company Acme", "meta": {"qualityStatus": "PASS"}}

        with mock.patch.object(module.shutil, "which", return_value="docker"), \
                mock.patch.object(module, "run_command", side_effect=fake_run_command), \
                mock.patch.object(module, "request_json", side_effect=fake_request_json):
            summary = module.run_smoke(
                Path.cwd(),
                image="worker:test",
                container_name="worker-test",
                host_port=8091,
                install_ocr=True,
                skip_build=False,
                attempts=1,
                delay_seconds=0,
            )

        self.assertTrue(summary["ok"])
        names = [check["name"] for check in summary["checks"]]
        self.assertIn("docker build", names)
        self.assertIn("docker run", names)
        self.assertIn("health endpoint", names)
        self.assertIn("text extraction endpoint", names)
        build_command = summary["checks"][0]["command"]
        self.assertIn("PYTHON_VERSION=3.12", build_command)
        self.assertIn("INSTALL_OCR=true", build_command)


if __name__ == "__main__":
    unittest.main()

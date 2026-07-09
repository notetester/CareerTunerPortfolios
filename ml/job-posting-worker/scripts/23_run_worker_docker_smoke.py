"""Build and smoke-test the job-posting worker Docker image, emitting JSON evidence."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[2]
DEFAULT_TEXT = (
    "Company: Acme. Role: Backend Engineer. Responsibilities: build Spring APIs "
    "and operate batch workers. Qualifications: Java, Spring Boot, MySQL, testing, "
    "and production debugging. Skills: Java Spring Docker monitoring. Employment: "
    "full-time Seoul hybrid role with benefits. Apply: submit resume before the "
    "deadline. Deadline: 2026-07-31. Commercial service operation and API "
    "improvement experience required. This posting includes enough detail for "
    "automated extraction validation and release readiness checks."
)


@dataclass(frozen=True)
class CommandResult:
    command: list[str]
    returncode: int
    stdout: str | None
    stderr: str | None

    @property
    def ok(self) -> bool:
        return self.returncode == 0

    def to_dict(self) -> dict[str, Any]:
        return {
            "command": self.command,
            "returncode": self.returncode,
            "stdout": (self.stdout or "")[-4000:],
            "stderr": (self.stderr or "")[-4000:],
            "ok": self.ok,
        }


def run_command(command: list[str], *, cwd: Path | None = None, timeout: int = 300) -> CommandResult:
    completed = subprocess.run(
        command,
        cwd=str(cwd) if cwd else None,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )
    return CommandResult(command, completed.returncode, completed.stdout, completed.stderr)


def request_json(url: str, payload: dict[str, Any] | None = None, timeout: float = 5.0) -> dict[str, Any]:
    data = None
    headers = {}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method="POST" if payload else "GET")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        body = response.read().decode("utf-8")
    return json.loads(body) if body else {}


def wait_for_health(base_url: str, attempts: int, delay_seconds: float) -> tuple[bool, dict[str, Any] | None, str | None]:
    last_error: str | None = None
    for _ in range(attempts):
        try:
            payload = request_json(f"{base_url}/health")
            return True, payload, None
        except (OSError, urllib.error.URLError, json.JSONDecodeError) as exc:
            last_error = str(exc)
            time.sleep(delay_seconds)
    return False, None, last_error


def cleanup_container(container_name: str) -> CommandResult | None:
    if shutil.which("docker") is None:
        return None
    return run_command(["docker", "rm", "-f", container_name], timeout=60)


def run_smoke(
    repo_root: Path,
    *,
    image: str,
    container_name: str,
    host_port: int,
    install_ocr: bool,
    skip_build: bool,
    attempts: int,
    delay_seconds: float,
) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    worker_root = repo_root / "ml" / "job-posting-worker"
    docker_path = shutil.which("docker")
    if docker_path is None:
        return {
            "ok": False,
            "error": "docker CLI not found",
            "repoRoot": str(repo_root),
            "image": image,
            "containerName": container_name,
            "checks": [],
        }

    checks: list[dict[str, Any]] = []
    if not skip_build:
        build_command = ["docker", "build", "-t", image]
        if install_ocr:
            build_command.extend(["--build-arg", "INSTALL_OCR=true"])
        build_command.append(str(worker_root))
        build = run_command(build_command, cwd=repo_root, timeout=1800)
        checks.append({"name": "docker build", **build.to_dict()})
        if not build.ok:
            return {
                "ok": False,
                "repoRoot": str(repo_root),
                "image": image,
                "containerName": container_name,
                "checks": checks,
            }

    cleanup_container(container_name)
    base_url = f"http://127.0.0.1:{host_port}"
    run = run_command(
        [
            "docker",
            "run",
            "-d",
            "--rm",
            "--name",
            container_name,
            "-p",
            f"127.0.0.1:{host_port}:8091",
            image,
        ],
        timeout=120,
    )
    checks.append({"name": "docker run", **run.to_dict()})
    try:
        if not run.ok:
            return {
                "ok": False,
                "repoRoot": str(repo_root),
                "image": image,
                "containerName": container_name,
                "checks": checks,
            }
        health_ok, health_payload, health_error = wait_for_health(base_url, attempts, delay_seconds)
        checks.append({
            "name": "health endpoint",
            "ok": health_ok,
            "payload": health_payload,
            "error": health_error,
        })
        extraction_payload: dict[str, Any] | None = None
        extraction_error: str | None = None
        extraction_ok = False
        if health_ok:
            try:
                extraction_payload = request_json(
                    f"{base_url}/extract/job-posting",
                    {"sourceType": "TEXT", "text": DEFAULT_TEXT},
                )
                meta = extraction_payload.get("meta", {}) if isinstance(extraction_payload, dict) else {}
                extraction_ok = bool(meta.get("qualityStatus") == "PASS" and extraction_payload.get("text"))
            except (OSError, urllib.error.URLError, json.JSONDecodeError) as exc:
                extraction_error = str(exc)
        checks.append({
            "name": "text extraction endpoint",
            "ok": extraction_ok,
            "payload": extraction_payload,
            "error": extraction_error,
        })
        return {
            "ok": all(bool(check.get("ok")) for check in checks),
            "repoRoot": str(repo_root),
            "image": image,
            "containerName": container_name,
            "checks": checks,
        }
    finally:
        cleanup = cleanup_container(container_name)
        if cleanup is not None:
            checks.append({"name": "docker cleanup", **cleanup.to_dict()})


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=DEFAULT_REPO_ROOT)
    parser.add_argument("--image", default="careertuner-job-posting-worker:smoke")
    parser.add_argument("--container-name", default="careertuner-job-posting-worker-smoke")
    parser.add_argument("--host-port", type=int, default=8091)
    parser.add_argument("--install-ocr", action="store_true")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--attempts", type=int, default=30)
    parser.add_argument("--delay-seconds", type=float, default=1.0)
    parser.add_argument("--output", type=Path, default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = run_smoke(
        args.repo_root,
        image=args.image,
        container_name=args.container_name,
        host_port=args.host_port,
        install_ocr=args.install_ocr,
        skip_build=args.skip_build,
        attempts=args.attempts,
        delay_seconds=args.delay_seconds,
    )
    encoded = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    raise SystemExit(0 if summary["ok"] else 1)


if __name__ == "__main__":
    main()

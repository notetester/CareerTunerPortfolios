"""Run local operational drills for the job posting worker."""

from __future__ import annotations

import argparse
import http.client
import importlib.util
import json
import sys
import threading
from http.server import ThreadingHTTPServer
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
WORKER_SCRIPT = SCRIPT_DIR / "15_job_posting_worker_api.py"


def load_worker_module():
    spec = importlib.util.spec_from_file_location("job_posting_worker_drill_api", WORKER_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


WORKER = load_worker_module()
QUALITY_STATUSES = {"PASS", "REVIEW_REQUIRED", "FAILED"}
CONTRACT_FIELD_TYPES = {
    "text": str,
    "strategy": str,
    "qualityScore": int,
    "qualityStatus": str,
    "metrics": dict,
    "warnings": list,
    "sectionHints": list,
    "modelVersions": dict,
    "fallbackEligible": bool,
    "generatedAt": str,
}
META_FIELD_TYPES = {key: value for key, value in CONTRACT_FIELD_TYPES.items() if key != "text"}


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


def request_json(port: int, method: str, path: str, body: str | None = None) -> tuple[int, dict[str, Any]]:
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    headers = {"Content-Type": "application/json"} if body is not None else {}
    connection.request(method, path, body=body, headers=headers)
    response = connection.getresponse()
    raw = response.read().decode("utf-8")
    connection.close()
    return response.status, json.loads(raw)


def request_oversized(port: int) -> tuple[int, dict[str, Any]]:
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    connection.putrequest("POST", "/extract/job-posting")
    connection.putheader("Content-Type", "application/json")
    connection.putheader("Content-Length", str(WORKER.MAX_REQUEST_BODY_BYTES + 1))
    connection.endheaders()
    response = connection.getresponse()
    raw = response.read().decode("utf-8")
    connection.close()
    return response.status, json.loads(raw)


def check(condition: bool, message: str) -> dict[str, Any]:
    return {"passed": condition, "message": message}


def contract_errors(payload: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if not isinstance(payload, dict):
        return ["payload is not an object"]
    meta = payload.get("meta")
    if not isinstance(meta, dict):
        errors.append("meta is missing or not an object")
        meta = {}

    for field, expected_type in CONTRACT_FIELD_TYPES.items():
        value = payload.get(field)
        if not isinstance(value, expected_type):
            errors.append(f"{field} must be {expected_type.__name__}")
    for field, expected_type in META_FIELD_TYPES.items():
        value = meta.get(field)
        if not isinstance(value, expected_type):
            errors.append(f"meta.{field} must be {expected_type.__name__}")
        elif field in payload and payload[field] != value:
            errors.append(f"meta.{field} must match top-level {field}")

    quality_status = payload.get("qualityStatus")
    if quality_status not in QUALITY_STATUSES:
        errors.append("qualityStatus must be PASS, REVIEW_REQUIRED, or FAILED")
    quality_score = payload.get("qualityScore")
    if isinstance(quality_score, int) and not 0 <= quality_score <= 100:
        errors.append("qualityScore must be between 0 and 100")
    return errors


def contract_check(payload: dict[str, Any], message: str) -> dict[str, Any]:
    errors = contract_errors(payload)
    if not errors:
        return check(True, message)
    return check(False, f"{message}: {', '.join(errors)}")


def run_drills(output_path: Path | None = None) -> dict[str, Any]:
    server = ThreadingHTTPServer(("127.0.0.1", 0), WORKER.WorkerHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    port = server.server_address[1]
    results: list[dict[str, Any]] = []
    try:
        status, payload = request_json(port, "GET", "/health")
        results.append(check(status == 200 and payload.get("status") == "ok", "health endpoint"))

        status, payload = request_json(
            port,
            "POST",
            "/extract/job-posting",
            json.dumps({"sourceType": "TEXT", "text": passing_text()}),
        )
        results.append(contract_check(payload, "valid text response satisfies worker JSON contract"))
        results.append(check(
            status == 200
            and payload.get("qualityStatus") == "PASS"
            and payload.get("meta", {}).get("fallbackEligible") is False,
            "valid text extraction stays on self-hosted path",
        ))

        status, payload = request_json(port, "POST", "/extract/job-posting", "{broken-json")
        results.append(contract_check(payload, "malformed JSON response satisfies worker JSON contract"))
        results.append(check(
            status == 500
            and payload.get("qualityStatus") == "FAILED"
            and payload.get("strategy") == "WORKER_ERROR",
            "malformed JSON returns FAILED contract",
        ))

        status, payload = request_oversized(port)
        results.append(contract_check(payload, "oversized request response satisfies worker JSON contract"))
        results.append(check(
            status == 500
            and payload.get("qualityStatus") == "FAILED"
            and payload.get("strategy") == "WORKER_ERROR",
            "oversized request returns FAILED contract",
        ))

        status, payload = request_json(
            port,
            "POST",
            "/extract/job-posting",
            json.dumps({"sourceType": "IMAGE"}),
        )
        results.append(contract_check(payload, "missing image response satisfies worker JSON contract"))
        results.append(check(
            status == 200
            and payload.get("qualityStatus") == "FAILED"
            and payload.get("strategy") == "IMAGE_OCR"
            and payload.get("meta", {}).get("fallbackEligible") is True,
            "missing image file path is explicit fallback-eligible FAILED result",
        ))
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)

    summary = {
        "total": len(results),
        "passed": sum(1 for result in results if result["passed"]),
        "failed": sum(1 for result in results if not result["passed"]),
        "results": results,
    }
    summary["ok"] = summary["failed"] == 0
    if output_path is not None:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = run_drills(args.output)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    raise SystemExit(0 if summary["ok"] else 1)


if __name__ == "__main__":
    main()

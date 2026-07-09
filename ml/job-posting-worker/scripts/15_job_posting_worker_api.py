"""Internal job posting extraction worker API.

Spring calls ``POST /extract/job-posting`` and receives extracted text plus the
stable quality-gate metadata contract. The worker does not call OpenAI.
"""

from __future__ import annotations

import argparse
import base64
import importlib.util
import json
import sys
import tempfile
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DOCUMENT_SCRIPT = SCRIPT_DIR / "14_extract_document_text.py"
SUPPORTED_SOURCE_TYPES = {"TEXT", "MANUAL", "URL", "HTML", "PDF", "IMAGE"}
# 기본(filePath/text) 요청은 작지만, sendBytes(파일 base64 동봉) 모드는 20MB 파일 → ~27MB base64.
# 파일경로 공유(co-location) 없는 배포를 위해 상한을 32MB 로 둔다(업로드 실효 한도 ≤20MB + base64 33% + 여유).
MAX_REQUEST_BODY_BYTES = 32 * 1024 * 1024


def load_document_module():
    spec = importlib.util.spec_from_file_location("document_text_extraction_worker", DOCUMENT_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


DOCUMENT = load_document_module()


def normalize_source_type(value: Any) -> str:
    source_type = str(value or "").strip().upper()
    if source_type not in SUPPORTED_SOURCE_TYPES:
        return "TEXT"
    return source_type


def string_value(payload: dict[str, Any], *keys: str) -> str:
    for key in keys:
        value = payload.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def quality_response(
    *,
    text: str,
    strategy: str,
    text_source: str,
    warnings: list[str] | None = None,
    input_file: str | None = None,
    input_suffix: str = "",
) -> dict[str, Any]:
    normalized_text = DOCUMENT.normalize_text(text)
    quality = DOCUMENT.analyze_quality(normalized_text, warnings)
    meta = {
        "inputFile": input_file or "",
        "inputSuffix": input_suffix,
        "strategy": strategy,
        "textSource": text_source,
        "qualityScore": quality["qualityScore"],
        "qualityStatus": quality["qualityStatus"],
        "metrics": quality["metrics"],
        "warnings": quality["warnings"],
        "sectionHints": quality["sectionHints"],
        "modelVersions": DOCUMENT.model_versions(),
        "fallbackEligible": DOCUMENT.fallback_eligible(strategy),
        "generatedAt": DOCUMENT.now_iso(),
    }
    return {"text": normalized_text, "meta": meta, **meta}


def error_response(exc: Exception) -> dict[str, Any]:
    return quality_response(
        text="",
        strategy="WORKER_ERROR",
        text_source="WORKER_ERROR",
        warnings=[f"worker_error:{exc.__class__.__name__}"],
    )


def extract_from_file(payload: dict[str, Any], source_type: str) -> dict[str, Any]:
    file_path_text = string_value(payload, "filePath", "path")
    if not file_path_text:
        return quality_response(
            text="",
            strategy="IMAGE_PDF_OCR" if source_type == "PDF" else "IMAGE_OCR",
            text_source="MISSING_FILE_PATH",
            warnings=["file_path_missing"],
        )

    file_path = Path(file_path_text)
    if not file_path.exists() or not file_path.is_file():
        return quality_response(
            text="",
            strategy="IMAGE_PDF_OCR" if source_type == "PDF" else "IMAGE_OCR",
            text_source="FILE_NOT_FOUND",
            warnings=["file_not_found"],
            input_file=file_path.name,
            input_suffix=file_path.suffix.lower(),
        )

    existing_ocr_dir_text = string_value(payload, "existingOcrDir")
    existing_ocr_dir = Path(existing_ocr_dir_text) if existing_ocr_dir_text else None
    with tempfile.TemporaryDirectory(prefix="ct_job_posting_worker_") as tmp:
        output_dir = Path(tmp)
        meta = DOCUMENT.extract_document(
            input_path=file_path,
            output_dir=output_dir,
            existing_ocr_dir=existing_ocr_dir,
        )
        text_path, _ = DOCUMENT.output_paths(file_path, output_dir)
        text = text_path.read_text(encoding="utf-8", errors="replace") if text_path.exists() else ""
        return {"text": DOCUMENT.normalize_text(text), "meta": meta, **meta}


def extract_from_bytes(payload: dict[str, Any], source_type: str) -> dict[str, Any]:
    """fileBase64(백엔드가 동봉한 파일 바이트)를 디코드해 임시파일로 OCR 한다.

    파일경로 공유(co-location)가 없어도 되는 경로. 실패 시 결정론 에러 응답을 돌려준다.
    """
    b64 = string_value(payload, "fileBase64")
    try:
        data = base64.b64decode(b64, validate=True)
    except Exception:
        return quality_response(
            text="",
            strategy="IMAGE_PDF_OCR" if source_type == "PDF" else "IMAGE_OCR",
            text_source="INVALID_BASE64",
            warnings=["invalid_base64"],
        )
    file_name = string_value(payload, "fileName") or "upload"
    suffix = Path(file_name).suffix or (".pdf" if source_type == "PDF" else ".png")
    with tempfile.TemporaryDirectory(prefix="ct_job_posting_worker_bytes_") as tmp:
        input_path = Path(tmp) / f"input{suffix}"
        input_path.write_bytes(data)
        output_dir = Path(tmp)
        meta = DOCUMENT.extract_document(
            input_path=input_path,
            output_dir=output_dir,
            existing_ocr_dir=None,
        )
        text_path, _ = DOCUMENT.output_paths(input_path, output_dir)
        text = text_path.read_text(encoding="utf-8", errors="replace") if text_path.exists() else ""
        return {"text": DOCUMENT.normalize_text(text), "meta": meta, **meta}


def extract_job_posting(payload: dict[str, Any]) -> dict[str, Any]:
    source_type = normalize_source_type(payload.get("sourceType"))
    # 우선순위: fileBase64(바이트 동봉 · co-location 불요) > filePath(기존) > text/기타.
    if string_value(payload, "fileBase64"):
        return extract_from_bytes(payload, source_type)
    file_path = string_value(payload, "filePath", "path")
    if file_path:
        return extract_from_file(payload, source_type)
    if source_type in {"TEXT", "MANUAL"}:
        return quality_response(
            text=string_value(payload, "text", "extractedText", "originalText"),
            strategy="TEXT_DIRECT",
            text_source="REQUEST_TEXT",
        )
    if source_type in {"URL", "HTML"}:
        raw_html = string_value(payload, "html")
        text = DOCUMENT.strip_html_text(raw_html) if raw_html else string_value(payload, "text", "extractedText")
        warnings = [] if text else ["url_fetch_not_enabled"]
        return quality_response(
            text=text,
            strategy="HTML_TEXT",
            text_source="REQUEST_HTML" if raw_html else "REQUEST_TEXT",
            warnings=warnings,
            input_file=string_value(payload, "uploadedFileUrl", "url"),
            input_suffix=".html" if raw_html else "",
        )
    return extract_from_file(payload, source_type)


class WorkerHandler(BaseHTTPRequestHandler):
    server_version = "CareerTunerJobPostingWorker/1.0"

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path == "/health":
            self.write_json({"status": "ok"})
            return
        self.send_error(HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path != "/extract/job-posting":
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        try:
            result = extract_job_posting(self.read_json())
            self.write_json(result)
        except Exception as exc:  # noqa: BLE001 - convert worker errors to JSON diagnostics.
            self.write_json(error_response(exc), status=HTTPStatus.INTERNAL_SERVER_ERROR)

    def read_json(self) -> dict[str, Any]:
        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length > MAX_REQUEST_BODY_BYTES:
            raise ValueError("request_body_too_large")
        raw_body = self.rfile.read(content_length)
        if not raw_body:
            return {}
        parsed = json.loads(raw_body.decode("utf-8"))
        if not isinstance(parsed, dict):
            raise ValueError("request_body_must_be_json_object")
        return parsed

    def write_json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A002 - BaseHTTPRequestHandler API
        sys.stderr.write("[job-posting-worker] " + (format % args) + "\n")


def warmup_ocr() -> None:
    DOCUMENT.configure_ocr_cache_env()
    # 1순위 엔진(PPStructureV3)을 먼저 예열한다. 첫 요청의 모델 로딩 지연/타임아웃을 줄인다.
    try:
        DOCUMENT.create_ppstructure("korean")
        print("PPStructureV3 warmed up (korean)", flush=True)
    except Exception as exc:
        print(f"PPStructureV3 warmup skipped: {exc}", flush=True)
    try:
        DOCUMENT.create_paddle_ocr("korean")
        print("PaddleOCR warmed up (korean)", flush=True)
    except Exception as exc:
        print(f"PaddleOCR warmup skipped: {exc}", flush=True)


def run_server(host: str, port: int) -> None:
    warmup_ocr()
    server = HTTPServer((host, port), WorkerHandler)
    print(f"job-posting-worker listening on http://{host}:{port}", flush=True)
    server.serve_forever()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8091)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    run_server(args.host, args.port)


if __name__ == "__main__":
    main()

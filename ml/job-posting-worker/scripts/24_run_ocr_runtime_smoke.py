"""Smoke-test the optional local OCR runtime and emit JSON evidence."""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import tempfile
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[2]
EXTRACTION_SCRIPT = SCRIPT_DIR / "14_extract_document_text.py"
SAMPLE_TEXT = (
    "Company: Acme\n"
    "Role: Backend Engineer\n"
    "Responsibilities: build APIs and operate worker services\n"
    "Qualifications: Java Spring MySQL Docker testing\n"
    "Skills: Java Spring MySQL Docker Python monitoring\n"
    "Employment: full-time hybrid\n"
    "Apply before 2026-07-31\n"
)


def has_module(module_name: str) -> bool:
    return importlib.util.find_spec(module_name) is not None


def load_extraction_module():
    spec = importlib.util.spec_from_file_location("document_text_extraction_for_ocr_smoke", EXTRACTION_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def create_sample_image(path: Path) -> None:
    try:
        from PIL import Image, ImageDraw
    except ImportError as exc:
        raise RuntimeError("Pillow is required to create the OCR smoke image.") from exc
    image = Image.new("RGB", (1200, 760), "white")
    draw = ImageDraw.Draw(image)
    y = 40
    for line in SAMPLE_TEXT.splitlines():
        draw.text((40, y), line, fill="black")
        y += 70
    image.save(path)


def create_sample_pdf(path: Path) -> None:
    try:
        from PIL import Image, ImageDraw
    except ImportError as exc:
        raise RuntimeError("Pillow is required to create the OCR smoke PDF.") from exc
    image = Image.new("RGB", (1200, 760), "white")
    draw = ImageDraw.Draw(image)
    y = 40
    for line in SAMPLE_TEXT.splitlines():
        draw.text((40, y), line, fill="black")
        y += 70
    image.save(path, "PDF", resolution=150.0)


def extraction_ok(result: dict[str, Any], extracted_text: str) -> bool:
    return bool(
        result.get("textSource") == "PADDLE_OCR"
        and result.get("qualityStatus") in {"PASS", "REVIEW_REQUIRED"}
        and len(extracted_text.strip()) >= 100
    )


def run_smoke(repo_root: Path, *, lang: str, output_dir: Path | None = None) -> dict[str, Any]:
    checks: list[dict[str, Any]] = [
        {"name": "paddleocr module", "ok": has_module("paddleocr")},
        {"name": "paddle module", "ok": has_module("paddle")},
        {"name": "Pillow module", "ok": has_module("PIL")},
        {"name": "PyMuPDF module", "ok": has_module("fitz")},
    ]
    if not all(check["ok"] for check in checks):
        return {
            "ok": False,
            "repoRoot": str(repo_root.resolve()),
            "language": lang,
            "checks": checks,
            "error": "required OCR runtime modules are not installed",
        }

    try:
        module = load_extraction_module()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            image_path = root / "ocr-smoke.png"
            pdf_path = root / "ocr-smoke-pdf.pdf"
            result_dir = output_dir or root / "out"
            create_sample_image(image_path)
            image_result = module.extract_document(
                input_path=image_path,
                output_dir=result_dir,
                existing_ocr_dir=None,
                options=module.ExtractionOptions(enable_paddle_ocr=True, paddle_ocr_lang=lang),
            )
            image_text = (result_dir / "ocr-smoke.txt").read_text(encoding="utf-8", errors="replace")
            create_sample_pdf(pdf_path)
            pdf_result = module.extract_document(
                input_path=pdf_path,
                output_dir=result_dir,
                existing_ocr_dir=None,
                options=module.ExtractionOptions(enable_paddle_ocr=True, paddle_ocr_lang=lang),
            )
            pdf_text = (result_dir / "ocr-smoke-pdf.txt").read_text(encoding="utf-8", errors="replace")
        checks.append({
            "name": "PaddleOCR image extraction",
            "ok": extraction_ok(image_result, image_text),
            "result": image_result,
            "textPreview": image_text[:500],
        })
        checks.append({
            "name": "PaddleOCR image PDF extraction",
            "ok": extraction_ok(pdf_result, pdf_text),
            "result": pdf_result,
            "textPreview": pdf_text[:500],
        })
        return {
            "ok": all(bool(check.get("ok")) for check in checks),
            "repoRoot": str(repo_root.resolve()),
            "language": lang,
            "checks": checks,
        }
    except Exception as exc:  # noqa: BLE001 - this is a diagnostic smoke script.
        checks.append({"name": "PaddleOCR extraction", "ok": False, "error": str(exc)})
        return {
            "ok": False,
            "repoRoot": str(repo_root.resolve()),
            "language": lang,
            "checks": checks,
            "error": str(exc),
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=DEFAULT_REPO_ROOT)
    parser.add_argument("--lang", default="en")
    parser.add_argument("--smoke-output-dir", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = run_smoke(args.repo_root, lang=args.lang, output_dir=args.smoke_output_dir)
    encoded = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    raise SystemExit(0 if summary["ok"] else 1)


if __name__ == "__main__":
    main()

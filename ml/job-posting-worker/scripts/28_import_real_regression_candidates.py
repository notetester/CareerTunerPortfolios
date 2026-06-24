"""Import real job-posting files and backfill OCR text for regression gates."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import shutil
import sys
import tempfile
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[2]
INVENTORY_SCRIPT = SCRIPT_DIR / "20_audit_real_regression_inventory.py"
EXTRACTION_SCRIPT = SCRIPT_DIR / "14_extract_document_text.py"
OCR_REQUIRED_SUFFIXES = {".pdf", ".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tif", ".tiff"}
TEXT_SUFFIXES = {".txt", ".html", ".htm"}
ACCEPTED_OCR_STATUSES = {"PASS", "REVIEW_REQUIRED"}
GENERATED_SOURCE_DIRS = {
    "__pycache__",
    "cleaned",
    "labeled",
    "models",
    "ocr_improvement",
    "outputs",
    "reports",
    "segmented",
    "synthetic",
}
DERIVED_SOURCE_DIR_PREFIXES = ("ocr_postings",)
SYNTHETIC_NAME_PREFIXES = ("syn-company-",)
SYNTHETIC_NAME_MARKERS = ("synthetic",)
SYNTHETIC_TEXT_MARKERS = (
    "SYNTHETIC",
    "SYNTHETIC_JOB_POSTING",
    "syn-company-",
    "synthetic gold",
    "가상기업",
    "합성공고",
    "실험데이터",
)


def load_script(module_name: str, path: Path):
    spec = importlib.util.spec_from_file_location(module_name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


INVENTORY = load_script("real_regression_inventory_for_import", INVENTORY_SCRIPT)
EXTRACTOR = load_script("document_text_extraction_for_import", EXTRACTION_SCRIPT)
SUPPORTED_SUFFIXES = set(INVENTORY.SUPPORTED_SUFFIXES) | OCR_REQUIRED_SUFFIXES


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def existing_raw_hashes(raw_dir: Path) -> set[str]:
    if not raw_dir.exists():
        return set()
    return {
        sha256(path)
        for path in raw_dir.iterdir()
        if path.is_file() and path.suffix.lower() in SUPPORTED_SUFFIXES
    }


def discover_source_files(source_dirs: list[Path], *, recursive: bool) -> list[Path]:
    files: list[Path] = []
    for source_dir in source_dirs:
        if source_dir.is_file():
            candidates = [source_dir]
        elif recursive:
            candidates = [path for path in source_dir.rglob("*") if path.is_file()]
        else:
            candidates = [path for path in source_dir.iterdir() if path.is_file()] if source_dir.exists() else []
        files.extend(
            path for path in candidates
            if path.suffix.lower() in SUPPORTED_SUFFIXES
            and not is_generated_or_synthetic_file(path)
        )
    return sorted(files, key=lambda path: str(path).lower())


def plan_raw_import(source: Path, raw_dir: Path, known_hashes: set[str]) -> tuple[Path | None, str, str | None]:
    if is_generated_or_synthetic_file(source):
        return None, "synthetic_or_generated_fixture", None
    if INVENTORY.is_non_job_file(source):
        return None, "non_job_reference_document", None
    file_hash = sha256(source)
    if file_hash in known_hashes:
        return None, "duplicate_content", None
    destination = raw_dir / source.name
    if destination.exists():
        return None, "name_conflict", None
    return destination, "imported", file_hash


def is_generated_or_synthetic_file(path: Path) -> bool:
    lower_name = path.name.lower()
    if lower_name.startswith(SYNTHETIC_NAME_PREFIXES):
        return True
    if any(marker in lower_name for marker in SYNTHETIC_NAME_MARKERS):
        return True
    if is_under_generated_dir(path):
        return True
    if has_synthetic_source_index(path):
        return True
    if path.suffix.lower() in TEXT_SUFFIXES and has_synthetic_text_marker(path):
        return True
    return False


def is_under_generated_dir(path: Path) -> bool:
    for part in path.parts:
        lowered = part.lower()
        if lowered in GENERATED_SOURCE_DIRS:
            return True
        if lowered.startswith(DERIVED_SOURCE_DIR_PREFIXES):
            return True
    return False


def has_synthetic_source_index(path: Path) -> bool:
    source_index = path.parent / "source_index.md"
    if not source_index.exists():
        return False
    try:
        text = source_index.read_text(encoding="utf-8", errors="replace").lower()
    except OSError:
        return False
    lower_name = path.name.lower()
    lower_stem = path.stem.lower()
    return (
        (lower_name in text or lower_stem in text)
        and ("synthetic" in text or "legacy_synthetic_pdf" in text)
    )


def has_synthetic_text_marker(path: Path) -> bool:
    try:
        sample = path.read_text(encoding="utf-8", errors="replace")[:64_000]
    except OSError:
        return False
    lowered_sample = sample.lower()
    return any(marker.lower() in lowered_sample for marker in SYNTHETIC_TEXT_MARKERS)


def copy_raw(source: Path, raw_dir: Path, known_hashes: set[str], *, dry_run: bool) -> tuple[Path | None, str]:
    destination, status, file_hash = plan_raw_import(source, raw_dir, known_hashes)
    if destination is None:
        return None, status
    if not dry_run:
        commit_raw_import(source, destination, known_hashes, file_hash)
    return destination, "imported"


def commit_raw_import(source: Path, destination: Path, known_hashes: set[str], file_hash: str | None) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    if file_hash is not None:
        known_hashes.add(file_hash)


def ocr_required(path: Path) -> bool:
    return path.suffix.lower() in OCR_REQUIRED_SUFFIXES


def text_ready_without_ocr(path: Path) -> bool:
    return path.suffix.lower() in TEXT_SUFFIXES


def extract_accepted_ocr_text(input_file: Path) -> tuple[dict[str, Any], str | None]:
    with tempfile.TemporaryDirectory(prefix="ct_real_regression_import_") as tmp:
        output_dir = Path(tmp)
        result = EXTRACTOR.extract_document(
            input_path=input_file,
            output_dir=output_dir,
            existing_ocr_dir=None,
        )
        text_path, _ = EXTRACTOR.output_paths(input_file, output_dir)
        text = text_path.read_text(encoding="utf-8", errors="replace") if text_path.exists() else ""
        quality_status = str(result.get("qualityStatus", ""))
        if quality_status not in ACCEPTED_OCR_STATUSES or not text.strip():
            return ({
                    "required": True,
                    "status": "failed_quality_gate",
                    "qualityStatus": quality_status,
                    "qualityScore": result.get("qualityScore"),
                    "warnings": result.get("warnings", []),
                }, None)
        return ({
            "required": True,
            "status": "backfilled",
            "qualityStatus": quality_status,
            "qualityScore": result.get("qualityScore"),
            "textSource": result.get("textSource"),
            "textLength": len(text),
        }, text)


def write_ocr_text(raw_file: Path, ocr_dir: Path, text: str) -> Path:
    ocr_destination = ocr_dir / f"{raw_file.stem}.txt"
    ocr_dir.mkdir(parents=True, exist_ok=True)
    ocr_destination.write_text(text + ("\n" if not text.endswith("\n") else ""), encoding="utf-8")
    return ocr_destination


def backfill_ocr(raw_file: Path, ocr_dir: Path, *, dry_run: bool) -> dict[str, Any]:
    if text_ready_without_ocr(raw_file):
        return {"required": False, "status": "not_required"}
    if not ocr_required(raw_file):
        return {"required": False, "status": "unsupported_for_ocr"}
    if INVENTORY.has_ocr_text(raw_file, ocr_dir):
        return {"required": True, "status": "already_present"}

    ocr_result, text = extract_accepted_ocr_text(raw_file)
    if text is None:
        return ocr_result
    ocr_destination = ocr_dir / f"{raw_file.stem}.txt"
    if not dry_run:
        ocr_destination = write_ocr_text(raw_file, ocr_dir, text)
    return {**ocr_result, "ocrPath": str(ocr_destination)}


def import_candidates(
    source_dirs: list[Path],
    raw_dir: Path,
    ocr_dir: Path,
    *,
    recursive: bool,
    dry_run: bool,
    limit: int | None = None,
    target_count: int = INVENTORY.DEFAULT_PRODUCTION_TARGET_COUNT,
) -> dict[str, Any]:
    raw_dir = raw_dir.resolve()
    ocr_dir = ocr_dir.resolve()
    known_hashes = existing_raw_hashes(raw_dir)
    source_files = discover_source_files(source_dirs, recursive=recursive)
    if limit is not None:
        source_files = source_files[:max(0, limit)]

    items: list[dict[str, Any]] = []
    for source in source_files:
        raw_file, import_status, file_hash = plan_raw_import(source, raw_dir, known_hashes)
        ocr_result: dict[str, Any] | None = None
        if raw_file is not None:
            if dry_run:
                ocr_result = {
                    "required": ocr_required(source),
                    "status": "dry_run",
                }
            elif ocr_required(source):
                ocr_result, text = extract_accepted_ocr_text(source)
                if text is None:
                    import_status = "ocr_quality_rejected"
                    raw_file = None
                else:
                    commit_raw_import(source, raw_file, known_hashes, file_hash)
                    ocr_destination = write_ocr_text(raw_file, ocr_dir, text)
                    ocr_result = {**ocr_result, "ocrPath": str(ocr_destination)}
            else:
                commit_raw_import(source, raw_file, known_hashes, file_hash)
                ocr_result = backfill_ocr(raw_file, ocr_dir, dry_run=False)
        items.append({
            "sourcePath": str(source.resolve()),
            "fileName": source.name,
            "importStatus": import_status,
            "rawPath": str(raw_file) if raw_file is not None else None,
            "ocr": ocr_result,
        })

    imported = [item for item in items if item["importStatus"] == "imported"]
    backfilled = [
        item for item in imported
        if item.get("ocr") and item["ocr"].get("status") in {"backfilled", "not_required", "already_present"}
    ]
    inventory = INVENTORY.audit_inventory(raw_dir, ocr_dir, target_count=target_count) if not dry_run else None
    return {
        "ok": all(
            item["importStatus"] != "imported"
            or item.get("ocr") is None
            or item["ocr"].get("status") in {"backfilled", "not_required", "already_present", "dry_run"}
            for item in items
        ),
        "dryRun": dry_run,
        "rawDir": str(raw_dir),
        "ocrDir": str(ocr_dir),
        "sourceCount": len(source_files),
        "importedCount": len(imported),
        "readyImportedCount": len(backfilled),
        "skippedCount": len(items) - len(imported),
        "items": items,
        "inventory": inventory,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, action="append", required=True)
    parser.add_argument("--raw-dir", type=Path, default=INVENTORY.DEFAULT_RAW_DIR)
    parser.add_argument("--ocr-dir", type=Path, default=INVENTORY.DEFAULT_OCR_DIR)
    parser.add_argument("--recursive", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--target-count", type=int, default=INVENTORY.DEFAULT_PRODUCTION_TARGET_COUNT)
    parser.add_argument("--output", type=Path, default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = import_candidates(
        args.source_dir,
        args.raw_dir,
        args.ocr_dir,
        recursive=args.recursive,
        dry_run=args.dry_run,
        limit=args.limit,
        target_count=args.target_count,
    )
    encoded = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    raise SystemExit(0 if summary["ok"] else 1)


if __name__ == "__main__":
    main()

"""Prepare a reproducible real-file regression set from audited ready postings."""

from __future__ import annotations

import argparse
import importlib.util
import json
import shutil
import sys
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[2]
INVENTORY_SCRIPT = SCRIPT_DIR / "20_audit_real_regression_inventory.py"


def load_inventory_module():
    spec = importlib.util.spec_from_file_location("real_regression_inventory_for_prepare", INVENTORY_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


INVENTORY = load_inventory_module()


def ocr_candidates(raw_file: Path, ocr_dir: Path) -> list[Path]:
    return [
        ocr_dir / f"{raw_file.stem}.txt",
        ocr_dir / raw_file.with_suffix(".txt").name,
    ]


def find_ocr_text(raw_file: Path, ocr_dir: Path) -> Path | None:
    for candidate in ocr_candidates(raw_file, ocr_dir):
        if candidate.exists() and candidate.stat().st_size > 0:
            return candidate
    return None


def copy_file(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def clean_output_dirs(raw_output_dir: Path, ocr_output_dir: Path) -> None:
    for path in (raw_output_dir, ocr_output_dir):
        if path.exists():
            shutil.rmtree(path)
        path.mkdir(parents=True, exist_ok=True)


def stabilization_command(
    repo_root: Path,
    raw_output_dir: Path,
    ocr_output_dir: Path,
    min_files: int,
) -> str:
    def display(path: Path) -> str:
        try:
            return str(path.relative_to(repo_root))
        except ValueError:
            return str(path)

    return (
        "python ml\\job-posting-worker\\scripts\\16_run_stabilization_check.py "
        f"--input-dir {display(raw_output_dir)} "
        f"--existing-ocr-dir {display(ocr_output_dir)} "
        "--output-dir .tmp\\job_posting_production_stabilization "
        "--report .tmp\\job_posting_production_stabilization\\document_pipeline_stabilization.md "
        f"--min-files {min_files}"
    )


def prepare_regression_set(
    repo_root: Path,
    raw_dir: Path,
    ocr_dir: Path,
    raw_output_dir: Path,
    ocr_output_dir: Path,
    *,
    target_count: int,
    clean: bool,
) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    raw_dir = raw_dir.resolve()
    ocr_dir = ocr_dir.resolve()
    raw_output_dir = raw_output_dir.resolve()
    ocr_output_dir = ocr_output_dir.resolve()
    inventory = INVENTORY.audit_inventory(raw_dir, ocr_dir, target_count)
    ready_items = [item for item in inventory["items"] if item["jobPostingCandidate"] and item["reason"] == "ready"]
    selected: list[dict[str, Any]] = []
    seen_fingerprints: set[str] = set()
    duplicate_skipped: list[str] = []
    for item in ready_items:
        fingerprint = item.get("ocrFingerprint") or item["fileName"]
        if fingerprint in seen_fingerprints:
            duplicate_skipped.append(item["fileName"])
            continue
        seen_fingerprints.add(fingerprint)
        selected.append(item)
        if len(selected) >= target_count:
            break

    if clean:
        clean_output_dirs(raw_output_dir, ocr_output_dir)
    else:
        raw_output_dir.mkdir(parents=True, exist_ok=True)
        ocr_output_dir.mkdir(parents=True, exist_ok=True)

    copied: list[dict[str, Any]] = []
    for item in selected:
        raw_file = raw_dir / item["fileName"]
        raw_destination = raw_output_dir / item["fileName"]
        copy_file(raw_file, raw_destination)
        ocr_source = find_ocr_text(raw_file, ocr_dir)
        ocr_destination: Path | None = None
        if ocr_source is not None:
            ocr_destination = ocr_output_dir / f"{raw_file.stem}.txt"
            copy_file(ocr_source, ocr_destination)
        copied.append({
            "fileName": item["fileName"],
            "rawPath": str(raw_destination),
            "ocrPath": str(ocr_destination) if ocr_destination else None,
            "suffix": item["suffix"],
        })

    ok = len(copied) >= target_count
    return {
        "ok": ok,
        "targetCount": target_count,
        "selectedCount": len(copied),
        "duplicateSkippedCount": len(duplicate_skipped),
        "duplicateSkipped": duplicate_skipped,
        "additionalReadyNeeded": max(0, target_count - len(copied)),
        "rawDir": str(raw_dir),
        "ocrDir": str(ocr_dir),
        "rawOutputDir": str(raw_output_dir),
        "ocrOutputDir": str(ocr_output_dir),
        "copied": copied,
        "inventory": {
            "rawFileCount": inventory["rawFileCount"],
            "jobPostingCandidateCount": inventory["jobPostingCandidateCount"],
            "readyJobPostingCount": inventory["readyJobPostingCount"],
            "uniqueReadyJobPostingCount": inventory["uniqueReadyJobPostingCount"],
            "duplicateReadyGroupCount": inventory["duplicateReadyGroupCount"],
            "duplicateReadyExtraCount": inventory["duplicateReadyExtraCount"],
            "duplicateReadyGroups": inventory["duplicateReadyGroups"],
            "missingOcrJobPostingCount": inventory["missingOcrJobPostingCount"],
            "nonJobReferenceCount": inventory["nonJobReferenceCount"],
            "ocrBackfillNeeded": inventory["ocrBackfillNeeded"],
            "nonJobReferences": inventory["nonJobReferences"],
        },
        "stabilizationCommand": stabilization_command(repo_root, raw_output_dir, ocr_output_dir, target_count),
    }


def parse_args() -> argparse.Namespace:
    tmp = DEFAULT_REPO_ROOT / ".tmp" / "job_posting_real_regression_set"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=DEFAULT_REPO_ROOT)
    parser.add_argument("--raw-dir", type=Path, default=INVENTORY.DEFAULT_RAW_DIR)
    parser.add_argument("--ocr-dir", type=Path, default=INVENTORY.DEFAULT_OCR_DIR)
    parser.add_argument("--raw-output-dir", type=Path, default=tmp / "raw")
    parser.add_argument("--ocr-output-dir", type=Path, default=tmp / "ocr")
    parser.add_argument("--target-count", type=int, default=INVENTORY.DEFAULT_PRODUCTION_TARGET_COUNT)
    parser.add_argument("--no-clean", action="store_true")
    parser.add_argument("--output", type=Path, default=tmp / "manifest.json")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = prepare_regression_set(
        repo_root=args.repo_root,
        raw_dir=args.raw_dir,
        ocr_dir=args.ocr_dir,
        raw_output_dir=args.raw_output_dir,
        ocr_output_dir=args.ocr_output_dir,
        target_count=args.target_count,
        clean=not args.no_clean,
    )
    encoded = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    raise SystemExit(0 if summary["ok"] else 1)


if __name__ == "__main__":
    main()

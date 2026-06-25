"""Audit real job-posting regression inventory and OCR coverage."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[2]
DEFAULT_REAL_VALIDATION = DEFAULT_REPO_ROOT / "personal" / "experiments" / "b_hybrid_ai" / "data" / "real_validation"
DEFAULT_RAW_DIR = DEFAULT_REAL_VALIDATION / "raw_ocr_inputs"
DEFAULT_OCR_DIR = DEFAULT_REAL_VALIDATION / "ocr_postings_improved_20"
DEFAULT_PRODUCTION_TARGET_COUNT = 43

NON_JOB_KEYWORDS = ("기업정보", "취업 tip", "취업 TIP", "company info")
SUPPORTED_SUFFIXES = {".pdf", ".png", ".jpg", ".jpeg", ".txt", ".html", ".htm"}


@dataclass(frozen=True)
class InventoryItem:
    file_name: str
    suffix: str
    job_posting_candidate: bool
    has_ocr_text: bool
    reason: str
    ocr_fingerprint: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "fileName": self.file_name,
            "suffix": self.suffix,
            "jobPostingCandidate": self.job_posting_candidate,
            "hasOcrText": self.has_ocr_text,
            "reason": self.reason,
            "ocrFingerprint": self.ocr_fingerprint,
        }


def is_non_job_file(path: Path) -> bool:
    lowered = path.stem.lower()
    return any(keyword.lower() in lowered for keyword in NON_JOB_KEYWORDS)


def has_ocr_text(path: Path, ocr_dir: Path) -> bool:
    return find_ocr_text(path, ocr_dir) is not None


def find_ocr_text(path: Path, ocr_dir: Path) -> Path | None:
    candidates = [
        ocr_dir / f"{path.stem}.txt",
        ocr_dir / path.with_suffix(".txt").name,
    ]
    for candidate in candidates:
        if candidate.exists() and candidate.stat().st_size > 0:
            return candidate
    return None


def read_ready_text(path: Path, ocr_dir: Path) -> str:
    if path.suffix.lower() in {".txt", ".html", ".htm"}:
        return path.read_text(encoding="utf-8", errors="replace")
    ocr_text = find_ocr_text(path, ocr_dir)
    if ocr_text is None:
        return ""
    return ocr_text.read_text(encoding="utf-8", errors="replace")


def normalize_text_for_duplicate_check(text: str) -> str:
    lowered = text.lower()
    lowered = re.sub(r"\s+", "", lowered)
    return re.sub(r"[^\w가-힣]", "", lowered)


def fingerprint_text(text: str) -> str | None:
    normalized = normalize_text_for_duplicate_check(text)
    if not normalized:
        return None
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def discover_raw_files(raw_dir: Path) -> list[Path]:
    if not raw_dir.exists():
        return []
    return sorted(
        [
            path
            for path in raw_dir.iterdir()
            if path.is_file() and path.suffix.lower() in SUPPORTED_SUFFIXES
        ],
        key=lambda path: path.name.lower(),
    )


def duplicate_groups(items: list[InventoryItem]) -> list[dict[str, Any]]:
    by_fingerprint: dict[str, list[str]] = {}
    for item in items:
        if item.reason != "ready" or not item.ocr_fingerprint:
            continue
        by_fingerprint.setdefault(item.ocr_fingerprint, []).append(item.file_name)
    return [
        {"fingerprint": fingerprint, "files": files, "duplicateExtraCount": len(files) - 1}
        for fingerprint, files in sorted(by_fingerprint.items())
        if len(files) > 1
    ]


def audit_inventory(raw_dir: Path, ocr_dir: Path, target_count: int) -> dict[str, Any]:
    items: list[InventoryItem] = []
    for path in discover_raw_files(raw_dir):
        non_job = is_non_job_file(path)
        ocr_present = has_ocr_text(path, ocr_dir)
        if non_job:
            reason = "non_job_reference_document"
        elif not ocr_present and path.suffix.lower() not in {".txt", ".html", ".htm"}:
            reason = "ocr_text_missing"
        else:
            reason = "ready"
        fingerprint = fingerprint_text(read_ready_text(path, ocr_dir)) if reason == "ready" else None
        items.append(InventoryItem(
            file_name=path.name,
            suffix=path.suffix.lower(),
            job_posting_candidate=not non_job,
            has_ocr_text=ocr_present,
            reason=reason,
            ocr_fingerprint=fingerprint,
        ))

    candidates = [item for item in items if item.job_posting_candidate]
    ready = [item for item in candidates if item.reason == "ready"]
    missing_ocr = [item for item in candidates if item.reason == "ocr_text_missing"]
    non_job = [item for item in items if not item.job_posting_candidate]
    duplicate_ready_groups = duplicate_groups(ready)
    duplicate_ready_extra_count = sum(group["duplicateExtraCount"] for group in duplicate_ready_groups)
    unique_ready = max(0, len(ready) - duplicate_ready_extra_count)
    return {
        "rawDir": str(raw_dir),
        "ocrDir": str(ocr_dir),
        "targetCount": target_count,
        "rawFileCount": len(items),
        "jobPostingCandidateCount": len(candidates),
        "readyJobPostingCount": len(ready),
        "uniqueReadyJobPostingCount": unique_ready,
        "duplicateReadyGroupCount": len(duplicate_ready_groups),
        "duplicateReadyExtraCount": duplicate_ready_extra_count,
        "duplicateReadyGroups": duplicate_ready_groups,
        "missingOcrJobPostingCount": len(missing_ocr),
        "nonJobReferenceCount": len(non_job),
        "additionalReadyNeeded": max(0, target_count - unique_ready),
        "ocrBackfillNeeded": [item.file_name for item in missing_ocr],
        "nonJobReferences": [item.file_name for item in non_job],
        "items": [item.to_dict() for item in items],
        "targetSatisfied": unique_ready >= target_count,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raw-dir", type=Path, default=DEFAULT_RAW_DIR)
    parser.add_argument("--ocr-dir", type=Path, default=DEFAULT_OCR_DIR)
    parser.add_argument("--target-count", type=int, default=DEFAULT_PRODUCTION_TARGET_COUNT)
    parser.add_argument("--output", type=Path, default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = audit_inventory(args.raw_dir, args.ocr_dir, args.target_count)
    encoded = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    raise SystemExit(0)


if __name__ == "__main__":
    main()

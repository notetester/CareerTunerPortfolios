"""Merge raw correction JSONL files with deterministic deduplication."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any

from dataset_contract import compact_json, length_bucket, sample_fingerprint


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no}: JSON parse failed: {exc}") from exc
            if not isinstance(value, dict):
                raise ValueError(f"{path}:{line_no}: row must be an object")
            rows.append(value)
    return rows


def merge(paths: list[Path]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    by_id: dict[str, dict[str, Any]] = {}
    by_fingerprint: dict[str, str] = {}
    duplicate_ids = 0
    duplicate_texts = 0
    source_counts: dict[str, int] = {}

    for path in paths:
        rows = read_jsonl(path)
        source_counts[str(path)] = len(rows)
        for row in rows:
            sample_id = row.get("id")
            if not isinstance(sample_id, str) or not sample_id.strip():
                raise ValueError(f"{path}: row has missing or invalid id")
            previous = by_id.get(sample_id)
            if previous is not None:
                if compact_json(previous) != compact_json(row):
                    raise ValueError(f"Conflicting rows use the same id: {sample_id}")
                duplicate_ids += 1
                continue

            fingerprint = sample_fingerprint(row)
            previous_id = by_fingerprint.get(fingerprint)
            if previous_id is not None:
                duplicate_texts += 1
                continue
            by_id[sample_id] = row
            by_fingerprint[fingerprint] = sample_id

    rows = sorted(by_id.values(), key=lambda row: str(row["id"]))
    task_counts: Counter[str] = Counter()
    bucket_counts: Counter[str] = Counter()
    for row in rows:
        task_type = str(row.get("task_type", "unknown"))
        input_obj = row.get("input") if isinstance(row.get("input"), dict) else {}
        original = input_obj.get("original_text")
        input_length = len(original) if isinstance(original, str) else 0
        task_counts[task_type] += 1
        bucket_counts[f"{task_type}:{length_bucket(task_type, input_length)}"] += 1

    summary = {
        "source_counts": source_counts,
        "merged_count": len(rows),
        "duplicate_ids_skipped": duplicate_ids,
        "duplicate_texts_skipped": duplicate_texts,
        "task_counts": dict(sorted(task_counts.items())),
        "task_length_buckets": dict(sorted(bucket_counts.items())),
    }
    return rows, summary


def write_jsonl(path: Path, rows: list[dict[str, Any]], *, overwrite: bool) -> None:
    if path.exists() and not overwrite:
        raise FileExistsError(f"Output already exists. Pass --overwrite: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(compact_json(row) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", nargs="+", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--summary-out", default=None)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    rows, summary = merge([Path(value) for value in args.input])
    write_jsonl(Path(args.output), rows, overwrite=args.overwrite)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if args.summary_out:
        summary_path = Path(args.summary_out)
        summary_path.parent.mkdir(parents=True, exist_ok=True)
        summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()

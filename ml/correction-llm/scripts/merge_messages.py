"""Merge canonical and unified-v2 SFT message JSONL files without data loss."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any

from dataset_contract import compact_json, correction_fingerprint, length_bucket


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no}: JSON parse failed: {exc}") from exc
            if not isinstance(row, dict):
                raise ValueError(f"{path}:{line_no}: row must be an object")
            _payload(row, path=path, line_no=line_no)
            rows.append(row)
    return rows


def _payload(row: dict[str, Any], *, path: Path | None = None, line_no: int | None = None) -> dict[str, Any]:
    label = f"{path}:{line_no}" if path is not None else "message row"
    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) < 3:
        raise ValueError(f"{label}: messages must contain system, user, and assistant entries")
    user = messages[1]
    if not isinstance(user, dict) or not isinstance(user.get("content"), str):
        raise ValueError(f"{label}: user message content is invalid")
    try:
        payload = json.loads(user["content"])
    except json.JSONDecodeError as exc:
        raise ValueError(f"{label}: user message content is not JSON") from exc
    if not isinstance(payload, dict) or not isinstance(payload.get("id"), str):
        raise ValueError(f"{label}: user payload id is invalid")
    return payload


def merge(paths: list[Path]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    by_id: dict[str, dict[str, Any]] = {}
    prior_source_fingerprints: set[str] = set()
    duplicate_ids = 0
    duplicate_texts = 0
    baseline_duplicate_texts_preserved = 0
    source_counts: dict[str, int] = {}

    for source_index, path in enumerate(paths):
        rows = read_jsonl(path)
        source_counts[str(path)] = len(rows)
        source_fingerprints: set[str] = set()
        for row in rows:
            payload = _payload(row)
            sample_id = payload["id"]
            previous = by_id.get(sample_id)
            if previous is not None:
                if compact_json(previous) != compact_json(row):
                    raise ValueError(f"Conflicting message rows use the same id: {sample_id}")
                duplicate_ids += 1
                continue
            task_type = str(payload.get("task_type", ""))
            input_obj = payload.get("input") if isinstance(payload.get("input"), dict) else {}
            original = str(input_obj.get("original_text", ""))
            fingerprint = correction_fingerprint(task_type, original)
            if source_index > 0 and (
                fingerprint in prior_source_fingerprints or fingerprint in source_fingerprints
            ):
                duplicate_texts += 1
                continue
            if source_index == 0 and fingerprint in source_fingerprints:
                baseline_duplicate_texts_preserved += 1
            by_id[sample_id] = row
            source_fingerprints.add(fingerprint)
        prior_source_fingerprints.update(source_fingerprints)

    rows = sorted(by_id.values(), key=lambda row: str(_payload(row)["id"]))
    task_counts: Counter[str] = Counter()
    bucket_counts: Counter[str] = Counter()
    for row in rows:
        payload = _payload(row)
        task_type = str(payload.get("task_type", "unknown"))
        input_obj = payload.get("input") if isinstance(payload.get("input"), dict) else {}
        original = str(input_obj.get("original_text", ""))
        task_counts[task_type] += 1
        bucket_counts[f"{task_type}:{length_bucket(task_type, len(original))}"] += 1

    return rows, {
        "source_counts": source_counts,
        "merged_count": len(rows),
        "duplicate_ids_skipped": duplicate_ids,
        "duplicate_texts_skipped": duplicate_texts,
        "baseline_duplicate_texts_preserved": baseline_duplicate_texts_preserved,
        "task_counts": dict(sorted(task_counts.items())),
        "task_length_buckets": dict(sorted(bucket_counts.items())),
    }


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

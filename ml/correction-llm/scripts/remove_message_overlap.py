"""Remove train rows whose correction text fingerprints appear in validation."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from dataset_contract import compact_json, correction_fingerprint
from merge_messages import read_jsonl


def payload(row: dict[str, Any]) -> dict[str, Any]:
    messages = row.get("messages")
    if not isinstance(messages, list):
        raise ValueError("messages must be an array")
    user = next(
        (message for message in messages if isinstance(message, dict) and message.get("role") == "user"),
        None,
    )
    if not isinstance(user, dict) or not isinstance(user.get("content"), str):
        raise ValueError("user message content is invalid")
    value = json.loads(user["content"])
    if not isinstance(value, dict):
        raise ValueError("user message content must be a JSON object")
    return value


def row_identity(row: dict[str, Any]) -> tuple[str, str]:
    value = payload(row)
    sample_id = value.get("id")
    task_type = value.get("task_type")
    input_obj = value.get("input")
    if not isinstance(sample_id, str) or not isinstance(task_type, str):
        raise ValueError("user payload id and task_type must be strings")
    if not isinstance(input_obj, dict) or not isinstance(input_obj.get("original_text"), str):
        raise ValueError("user payload input.original_text must be a string")
    return sample_id, correction_fingerprint(task_type, input_obj["original_text"])


def remove_overlap(
    train_rows: list[dict[str, Any]], val_rows: list[dict[str, Any]]
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    val_ids: set[str] = set()
    val_fingerprints: set[str] = set()
    for row in val_rows:
        sample_id, fingerprint = row_identity(row)
        val_ids.add(sample_id)
        val_fingerprints.add(fingerprint)

    kept: list[dict[str, Any]] = []
    removed: list[dict[str, str]] = []
    for row in train_rows:
        sample_id, fingerprint = row_identity(row)
        reasons = []
        if sample_id in val_ids:
            reasons.append("id")
        if fingerprint in val_fingerprints:
            reasons.append("fingerprint")
        if reasons:
            removed.append(
                {"id": sample_id, "fingerprint": fingerprint, "reason": "+".join(reasons)}
            )
        else:
            kept.append(row)

    kept_ids: set[str] = set()
    kept_fingerprints: set[str] = set()
    for row in kept:
        sample_id, fingerprint = row_identity(row)
        kept_ids.add(sample_id)
        kept_fingerprints.add(fingerprint)

    summary = {
        "train_input_count": len(train_rows),
        "validation_count": len(val_rows),
        "train_output_count": len(kept),
        "removed_count": len(removed),
        "removed": removed,
        "remaining_id_overlap": len(kept_ids & val_ids),
        "remaining_fingerprint_overlap": len(kept_fingerprints & val_fingerprints),
    }
    return kept, summary


def write_jsonl(path: Path, rows: list[dict[str, Any]], *, overwrite: bool) -> None:
    if path.exists() and not overwrite:
        raise FileExistsError(f"Output already exists. Pass --overwrite: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(compact_json(row) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--train", required=True)
    parser.add_argument("--val", required=True)
    parser.add_argument("--train-out", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    cleaned, summary = remove_overlap(read_jsonl(Path(args.train)), read_jsonl(Path(args.val)))
    write_jsonl(Path(args.train_out), cleaned, overwrite=args.overwrite)
    summary_path = Path(args.summary_out)
    if summary_path.exists() and not args.overwrite:
        raise FileExistsError(f"Summary already exists. Pass --overwrite: {summary_path}")
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

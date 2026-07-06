"""Merge independently generated raw lanes and assign canonical IDs."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any

from dataset_contract import compact_json, sample_fingerprint, validate_sample


TASK_CODES = {
    "SELF_INTRO_CORRECTION": "self-intro",
    "INTERVIEW_ANSWER_CORRECTION": "interview",
    "RESUME_EXPRESSION_IMPROVEMENT": "resume",
    "PORTFOLIO_DESCRIPTION_IMPROVEMENT": "portfolio",
}


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, 1):
            if not line.strip():
                continue
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError(f"{path}:{line_no}: row must be an object")
            errors, _, _ = validate_sample(value, unified_contract=True)
            if errors:
                raise ValueError(f"{path}:{line_no}: invalid sample: {errors[:3]}")
            rows.append(value)
    return rows


def merge(paths: list[Path], per_task: int | None) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    by_fingerprint: dict[str, dict[str, Any]] = {}
    source_counts: dict[str, int] = {}
    duplicate_texts = 0
    for path in paths:
        rows = read_jsonl(path)
        source_counts[str(path)] = len(rows)
        for row in rows:
            fingerprint = sample_fingerprint(row)
            if fingerprint in by_fingerprint:
                duplicate_texts += 1
                continue
            by_fingerprint[fingerprint] = row

    grouped: dict[str, list[dict[str, Any]]] = {task: [] for task in TASK_CODES}
    for row in by_fingerprint.values():
        task = row.get("task_type")
        if task not in grouped:
            raise ValueError(f"unknown task_type: {task}")
        grouped[task].append(row)

    output: list[dict[str, Any]] = []
    task_counts: Counter[str] = Counter()
    for task, rows in grouped.items():
        selected = rows[:per_task] if per_task is not None else rows
        for index, row in enumerate(selected, 1):
            output.append({**row, "id": f"e-stage100-{TASK_CODES[task]}-{index:04d}"})
            task_counts[task] += 1

    output.sort(key=lambda row: str(row["id"]))
    return output, {
        "source_counts": source_counts,
        "source_count": sum(source_counts.values()),
        "merged_count": len(output),
        "duplicate_texts_skipped": duplicate_texts,
        "task_counts": dict(sorted(task_counts.items())),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", nargs="+", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--per-task", type=int, default=None)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    output_path = Path(args.output)
    if output_path.exists() and not args.overwrite:
        raise FileExistsError(f"Output already exists. Pass --overwrite: {output_path}")
    rows, summary = merge([Path(value) for value in args.input], args.per_task)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(compact_json(row) + "\n")
    summary_path = Path(args.summary_out)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

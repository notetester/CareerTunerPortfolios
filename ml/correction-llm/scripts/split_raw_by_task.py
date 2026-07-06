"""Split a raw correction JSONL file into one resumable file per task type."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

from dataset_contract import TASK_RULES, compact_json
from generate_unified_data import TASK_CODES


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
            if not isinstance(row, dict) or row.get("task_type") not in TASK_RULES:
                raise ValueError(f"{path}:{line_no}: invalid task_type")
            rows.append(row)
    return rows


def split(rows: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[str(row["task_type"])].append(row)
    return {task: sorted(values, key=lambda item: str(item.get("id", ""))) for task, values in grouped.items()}


def write_task_files(output_dir: Path, grouped: dict[str, list[dict[str, Any]]], *, overwrite: bool) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for task_type, rows in sorted(grouped.items()):
        path = output_dir / f"raw.{TASK_CODES[task_type]}.jsonl"
        if path.exists() and not overwrite:
            raise FileExistsError(f"Output already exists. Pass --overwrite: {path}")
        with path.open("w", encoding="utf-8", newline="\n") as handle:
            for row in rows:
                handle.write(compact_json(row) + "\n")
        print(f"{task_type}: {len(rows)} -> {path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    write_task_files(Path(args.output_dir), split(read_jsonl(Path(args.input))), overwrite=args.overwrite)


if __name__ == "__main__":
    main()

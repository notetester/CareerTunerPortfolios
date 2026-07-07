"""Mix multiple message datasets with per-source repeat weights."""

from __future__ import annotations

import argparse
import random
from pathlib import Path
from typing import Any

from followup_pipeline_common import read_jsonl, write_json, write_jsonl


def parse_source_spec(value: str) -> tuple[str, Path, int]:
    if "=" not in value:
        raise ValueError(f"invalid source spec: {value}")
    label, remainder = value.split("=", 1)
    repeat = 1
    path_text = remainder
    if "@" in remainder:
        path_text, repeat_text = remainder.rsplit("@", 1)
        repeat = int(repeat_text)
    if repeat <= 0:
        raise ValueError(f"repeat must be positive: {value}")
    return label.strip(), Path(path_text.strip()), repeat


def build_dataset(specs: list[str], seed: int) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    rng = random.Random(seed)
    rows: list[dict[str, Any]] = []
    summary_sources: dict[str, Any] = {}
    for spec in specs:
        label, path, repeat = parse_source_spec(spec)
        source_rows = read_jsonl(path)
        for _ in range(repeat):
            rows.extend(source_rows)
        summary_sources[label] = {
            "path": str(path),
            "repeat": repeat,
            "source_count": len(source_rows),
            "expanded_count": len(source_rows) * repeat,
        }
    rng.shuffle(rows)
    return rows, summary_sources


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--train-source", action="append", default=[], required=True)
    parser.add_argument("--val-source", action="append", default=[])
    parser.add_argument("--train-out", required=True)
    parser.add_argument("--val-out", default=None)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    train_rows, train_sources = build_dataset(args.train_source, args.seed)
    write_jsonl(Path(args.train_out), train_rows)

    summary: dict[str, Any] = {
        "train_count": len(train_rows),
        "train_sources": train_sources,
        "seed": args.seed,
    }
    if args.val_source:
        val_rows, val_sources = build_dataset(args.val_source, args.seed + 1)
        if not args.val_out:
            raise ValueError("--val-out is required when --val-source is provided")
        write_jsonl(Path(args.val_out), val_rows)
        summary["val_count"] = len(val_rows)
        summary["val_sources"] = val_sources

    write_json(Path(args.summary_out), summary)
    print(f"train={summary['train_count']}")


if __name__ == "__main__":
    main()

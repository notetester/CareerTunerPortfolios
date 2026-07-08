"""Build a small gate-free curriculum for expedited corrected_text tuning."""

from __future__ import annotations

import argparse
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from dataset_contract import compact_json


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(compact_json(row) + "\n")


def payload(row: dict[str, Any]) -> dict[str, Any]:
    value = json.loads(row["messages"][1]["content"])
    if not isinstance(value, dict):
        raise ValueError("message payload must be an object")
    return value


def sample_id(row: dict[str, Any]) -> str:
    value = payload(row).get("id")
    if not isinstance(value, str) or not value:
        raise ValueError("message row id is missing")
    return value


def task_type(row: dict[str, Any]) -> str:
    value = payload(row).get("task_type")
    if not isinstance(value, str) or not value:
        raise ValueError("message row task_type is missing")
    return value


def direct_message_to_raw(row: dict[str, Any]) -> dict[str, Any]:
    row_payload = payload(row)
    output = json.loads(row["messages"][-1]["content"])
    if not isinstance(output, dict):
        raise ValueError("assistant output must be an object")
    return {
        "id": row_payload["id"],
        "task_type": row_payload["task_type"],
        "input": row_payload["input"],
        "output": output,
    }


def unique_by_id(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    seen: set[str] = set()
    for row in rows:
        row_id = sample_id(row)
        if row_id in seen:
            continue
        seen.add(row_id)
        selected.append(row)
    return selected


def balanced_anchors(
    rows: list[dict[str, Any]],
    excluded_ids: set[str],
    per_task: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    if per_task == 0:
        return []
    buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in unique_by_id(rows):
        if sample_id(row) not in excluded_ids:
            buckets[task_type(row)].append(row)

    selected: list[dict[str, Any]] = []
    for task in sorted(buckets):
        rng.shuffle(buckets[task])
        if len(buckets[task]) < per_task:
            raise ValueError(f"not enough anchor rows for {task}: {len(buckets[task])} < {per_task}")
        selected.extend(buckets[task][:per_task])
    return selected


def boundary_rows(
    rows: list[dict[str, Any]],
    excluded_ids: set[str],
    limit: int,
    max_per_id: int,
    rng: random.Random,
    min_output_length: int = 0,
    max_output_length: int = 0,
) -> list[dict[str, Any]]:
    if limit == 0:
        return []
    candidates: list[dict[str, Any]] = []
    for row in rows:
        if sample_id(row) in excluded_ids:
            continue
        output = json.loads(row["messages"][-1]["content"])
        corrected_text = output.get("corrected_text", "")
        length = len(corrected_text) if isinstance(corrected_text, str) else 0
        if min_output_length and length < min_output_length:
            continue
        if max_output_length and length > max_output_length:
            continue
        candidates.append(row)
    rng.shuffle(candidates)
    counts: Counter[str] = Counter()
    selected: list[dict[str, Any]] = []
    for row in candidates:
        row_id = sample_id(row)
        if counts[row_id] >= max_per_id:
            continue
        selected.append(row)
        counts[row_id] += 1
        if len(selected) >= limit:
            break
    if len(selected) < limit:
        raise ValueError(f"not enough boundary rows: {len(selected)} < {limit}")
    return selected


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-train", required=True)
    parser.add_argument("--base-val", required=True)
    parser.add_argument("--boundary-direct", required=True)
    parser.add_argument("--boundary-repair", required=True)
    parser.add_argument("--gate-raw", required=True)
    parser.add_argument("--exclude-raw", action="append", default=[])
    parser.add_argument("--train-out", required=True)
    parser.add_argument("--val-out", required=True)
    parser.add_argument("--val-raw-out", default=None)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--anchor-per-task", type=int, default=32)
    parser.add_argument("--boundary-direct-count", type=int, default=24)
    parser.add_argument("--boundary-repair-count", type=int, default=8)
    parser.add_argument("--boundary-max-per-id", type=int, default=2)
    parser.add_argument("--boundary-min-output-length", type=int, default=0)
    parser.add_argument("--boundary-max-output-length", type=int, default=0)
    parser.add_argument("--val-per-task", type=int, default=6)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    base_train = read_jsonl(Path(args.base_train))
    base_val = read_jsonl(Path(args.base_val))
    gate_ids = {row["id"] for row in read_jsonl(Path(args.gate_raw))}
    extra_excluded_ids = {
        row["id"]
        for raw_path in args.exclude_raw
        for row in read_jsonl(Path(raw_path))
    }
    val_ids = {sample_id(row) for row in base_val}
    train_excluded = gate_ids | val_ids

    anchors = balanced_anchors(base_train, train_excluded, args.anchor_per_task, rng)
    boundary_excluded = train_excluded
    direct = boundary_rows(
        read_jsonl(Path(args.boundary_direct)),
        boundary_excluded,
        args.boundary_direct_count,
        args.boundary_max_per_id,
        rng,
        args.boundary_min_output_length,
        args.boundary_max_output_length,
    )
    repair = boundary_rows(
        read_jsonl(Path(args.boundary_repair)),
        boundary_excluded,
        args.boundary_repair_count,
        args.boundary_max_per_id,
        rng,
        args.boundary_min_output_length,
        args.boundary_max_output_length,
    )

    train_rows = anchors + direct + repair
    rng.shuffle(train_rows)
    validation = balanced_anchors(base_val, gate_ids | extra_excluded_ids, args.val_per_task, rng)
    train_ids = {sample_id(row) for row in train_rows}
    validation_ids = {sample_id(row) for row in validation}
    overlap = sorted(train_ids & validation_ids)
    gate_overlap = sorted(train_ids & gate_ids)
    if overlap or gate_overlap:
        raise ValueError(f"dataset leakage detected: train_val={overlap}, train_gate={gate_overlap}")

    write_jsonl(Path(args.train_out), train_rows)
    write_jsonl(Path(args.val_out), validation)
    if args.val_raw_out:
        write_jsonl(Path(args.val_raw_out), [direct_message_to_raw(row) for row in validation])
    summary = {
        "seed": args.seed,
        "train_count": len(train_rows),
        "train_unique_ids": len(train_ids),
        "train_task_counts": dict(sorted(Counter(task_type(row) for row in train_rows).items())),
        "anchor_count": len(anchors),
        "boundary_direct_count": len(direct),
        "boundary_repair_count": len(repair),
        "validation_count": len(validation),
        "validation_task_counts": dict(sorted(Counter(task_type(row) for row in validation).items())),
        "train_val_id_overlap": overlap,
        "train_gate_id_overlap": gate_overlap,
        "gate_ids": sorted(gate_ids),
    }
    Path(args.summary_out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.summary_out).write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

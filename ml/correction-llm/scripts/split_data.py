"""Create deterministic stratified train/val split for messages JSONL."""

from __future__ import annotations

import argparse
import json
import random
from collections import defaultdict
from pathlib import Path

from dataset_contract import length_bucket


def read_jsonl(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def sample_id(row: dict) -> str:
    user_content = row["messages"][1]["content"]
    return json.loads(user_content)["id"]


def stratum(row: dict) -> str:
    user_content = row["messages"][1]["content"]
    payload = json.loads(user_content)
    task = payload["task_type"]
    original = payload.get("input", {}).get("original_text", "")
    return f"{task}:{length_bucket(task, len(original))}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--train", required=True)
    parser.add_argument("--val", required=True)
    parser.add_argument("--val-ratio", type=float, default=0.15)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    rows = read_jsonl(Path(args.input))
    grouped: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        grouped[stratum(row)].append(row)

    rng = random.Random(args.seed)
    train: list[dict] = []
    val: list[dict] = []
    for _, group_rows in sorted(grouped.items()):
        shuffled = list(group_rows)
        rng.shuffle(shuffled)
        val_count = max(1, round(len(shuffled) * args.val_ratio)) if len(shuffled) > 2 else 0
        val.extend(shuffled[:val_count])
        train.extend(shuffled[val_count:])

    train.sort(key=sample_id)
    val.sort(key=sample_id)
    write_jsonl(Path(args.train), train)
    write_jsonl(Path(args.val), val)

    summary = {
        "input_count": len(rows),
        "train_count": len(train),
        "val_count": len(val),
        "strata": {key: len(value) for key, value in sorted(grouped.items())},
        "val_ids": [sample_id(row) for row in val],
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

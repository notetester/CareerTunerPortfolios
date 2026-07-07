"""Build task-balanced repair examples whose corrected text satisfies length constraints."""

from __future__ import annotations

import argparse
import json
import random
from collections import defaultdict
from pathlib import Path


def read_jsonl(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def qualifying_task(row: dict) -> str | None:
    messages = row.get("messages", [])
    if len(messages) != 4 or messages[2].get("role") != "user":
        return None
    user = json.loads(messages[1]["content"])
    output = json.loads(messages[3]["content"])
    constraints = user.get("input", {}).get("constraints", {})
    corrected_text = output.get("corrected_text")
    if not isinstance(corrected_text, str):
        return None
    min_chars = int(constraints.get("min_chars", 0) or 0)
    max_chars = int(constraints.get("max_chars", 0) or 0)
    if min_chars and len(corrected_text) < min_chars:
        return None
    if max_chars and len(corrected_text) > max_chars:
        return None
    return str(user["task_type"])


def build(rows: list[dict], per_task: int, seed: int) -> tuple[list[dict], dict]:
    groups: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        task = qualifying_task(row)
        if task:
            groups[task].append(row)

    rng = random.Random(seed)
    selected: list[dict] = []
    task_counts: dict[str, int] = {}
    for task, candidates in sorted(groups.items()):
        if len(candidates) < per_task:
            raise ValueError(f"{task} has only {len(candidates)} qualifying repair rows; need {per_task}")
        picked = rng.sample(candidates, per_task)
        selected.extend(picked)
        task_counts[task] = len(picked)
    rng.shuffle(selected)
    return selected, {
        "source_count": len(rows),
        "qualifying_count": sum(len(values) for values in groups.values()),
        "selected_count": len(selected),
        "per_task": per_task,
        "seed": seed,
        "task_counts": task_counts,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--per-task", type=int, default=20)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    selected, summary = build(read_jsonl(Path(args.input)), args.per_task, args.seed)
    write_jsonl(Path(args.output), selected)
    summary_path = Path(args.summary_out)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

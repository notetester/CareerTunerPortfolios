"""Build a task-balanced direct-output dataset for risk flag calibration."""

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


def classify(row: dict) -> tuple[str, bool]:
    messages = row.get("messages", [])
    if len(messages) != 3 or messages[-1].get("role") != "assistant":
        raise ValueError("risk calibration accepts direct three-message rows only")
    user = json.loads(messages[1]["content"])
    output = json.loads(messages[2]["content"])
    return str(user["task_type"]), bool(output.get("risk_flags"))


def build(
    rows: list[dict],
    per_task: int,
    seed: int,
    class_mode: str = "balanced",
) -> tuple[list[dict], dict]:
    groups: dict[tuple[str, bool], list[dict]] = defaultdict(list)
    for row in rows:
        groups[classify(row)].append(row)

    rng = random.Random(seed)
    selected: list[dict] = []
    counts: dict[str, dict[str, int]] = {}
    tasks = sorted({task for task, _ in groups})
    for task in tasks:
        counts[task] = {}
        classes = (True,) if class_mode == "risky-only" else (False, True)
        for risky in classes:
            candidates = groups[(task, risky)]
            if len(candidates) < per_task:
                label = "risky" if risky else "clean"
                raise ValueError(f"{task} has only {len(candidates)} {label} rows; need {per_task}")
            picked = rng.sample(candidates, per_task)
            selected.extend(picked)
            counts[task]["risky" if risky else "clean"] = len(picked)

    rng.shuffle(selected)
    summary = {
        "source_count": len(rows),
        "selected_count": len(selected),
        "per_task_per_class": per_task,
        "seed": seed,
        "class_mode": class_mode,
        "task_counts": counts,
    }
    return selected, summary


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--per-task", type=int, default=40)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--class-mode", choices=["balanced", "risky-only"], default="balanced")
    args = parser.parse_args()

    selected, summary = build(
        read_jsonl(Path(args.input)),
        args.per_task,
        args.seed,
        args.class_mode,
    )
    write_jsonl(Path(args.output), selected)
    summary_path = Path(args.summary_out)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

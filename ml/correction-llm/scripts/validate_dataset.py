"""Validate E correction raw JSONL samples."""

from __future__ import annotations

import argparse
import json
import statistics
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from dataset_contract import validate_sample


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, 1):
            line = line.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no}: JSON parse failed: {exc}") from exc
            if not isinstance(value, dict):
                raise ValueError(f"{path}:{line_no}: each JSONL row must be an object")
            rows.append(value)
    return rows


def validate(
    rows: list[dict[str, Any]],
    *,
    unified_contract: bool,
) -> tuple[dict[str, Any], list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    ids: list[str] = []
    task_counts: Counter[str] = Counter()
    metrics_by_task: dict[str, list[dict[str, Any]]] = defaultdict(list)

    for index, row in enumerate(rows, 1):
        sample_id = row.get("id") if isinstance(row.get("id"), str) else f"line-{index}"
        ids.append(sample_id)
        task_type = row.get("task_type")
        if isinstance(task_type, str):
            task_counts[task_type] += 1

        row_errors, row_warnings, metrics = validate_sample(
            row,
            unified_contract=unified_contract,
        )
        errors.extend(f"{sample_id}: {message}" for message in row_errors)
        warnings.extend(f"{sample_id}: {message}" for message in row_warnings)
        if metrics and isinstance(task_type, str):
            metrics_by_task[task_type].append(metrics)

    for sample_id, count in Counter(ids).items():
        if count > 1:
            errors.append(f"{sample_id}: duplicated id count={count}")

    summary = {
        "count": len(rows),
        "contract": "unified-v2" if unified_contract else "legacy",
        "task_counts": dict(sorted(task_counts.items())),
        "task_metrics": {
            task_type: _summarize_metrics(values)
            for task_type, values in sorted(metrics_by_task.items())
        },
        "errors": len(errors),
        "warnings": len(warnings),
    }
    return summary, errors, warnings


def _summarize_metrics(values: list[dict[str, Any]]) -> dict[str, Any]:
    input_lengths = [int(item["input_length"]) for item in values]
    output_lengths = [int(item["output_length"]) for item in values]
    ratios = [float(item["output_ratio"]) for item in values]
    buckets = Counter(str(item["length_bucket"]) for item in values)
    return {
        "count": len(values),
        "input_length": _number_stats(input_lengths),
        "output_length": _number_stats(output_lengths),
        "output_ratio": _number_stats(ratios, digits=3),
        "length_buckets": dict(sorted(buckets.items())),
    }


def _number_stats(values: list[float], *, digits: int = 1) -> dict[str, float]:
    return {
        "min": round(min(values), digits),
        "median": round(statistics.median(values), digits),
        "mean": round(statistics.fmean(values), digits),
        "max": round(max(values), digits),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--summary-out", default=None)
    parser.add_argument("--contract", choices=["legacy", "unified-v2"], default="legacy")
    parser.add_argument("--fail-on-warnings", action="store_true")
    args = parser.parse_args()

    rows = load_jsonl(Path(args.input))
    summary, errors, warnings = validate(
        rows,
        unified_contract=args.contract == "unified-v2",
    )

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if warnings:
        print("\nWARNINGS")
        for warning in warnings:
            print(f"- {warning}")
    if errors:
        print("\nERRORS")
        for error in errors:
            print(f"- {error}")

    if args.summary_out:
        output = {"summary": summary, "errors": errors, "warnings": warnings}
        Path(args.summary_out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.summary_out).write_text(
            json.dumps(output, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    if errors or (args.fail_on_warnings and warnings):
        raise SystemExit(1)


if __name__ == "__main__":
    main()

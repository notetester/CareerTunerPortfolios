"""Build direct-message boundary sets for resume rows near the max length contract."""

from __future__ import annotations

import argparse
import copy
from pathlib import Path
from typing import Any

from build_messages import SYSTEM_PROMPT
from dataset_contract import validate_sample
from followup_pipeline_common import build_direct_message, read_jsonl, write_json, write_jsonl


def parse_int_list(value: str) -> list[int]:
    values: list[int] = []
    for item in value.split(","):
        stripped = item.strip()
        if stripped:
            values.append(int(stripped))
    if not values:
        raise ValueError("expected at least one integer value")
    return values


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--sample-id", action="append", default=[])
    parser.add_argument("--task-type", default="RESUME_EXPRESSION_IMPROVEMENT")
    parser.add_argument("--offsets", default="0,5,10")
    parser.add_argument("--caps", default=None)
    return parser.parse_args()


def build_variants(sample: dict[str, Any], offsets: list[int], explicit_caps: list[int] | None) -> list[dict[str, Any]]:
    output = sample.get("output")
    constraints = sample.get("input", {}).get("constraints", {})
    if not isinstance(output, dict) or not isinstance(constraints, dict):
        return []
    corrected = output.get("corrected_text")
    if not isinstance(corrected, str):
        return []
    corrected_length = len(corrected)
    existing_min = int(constraints.get("min_chars", 0) or 0)
    existing_target = int(constraints.get("target_chars", corrected_length) or corrected_length)
    caps = explicit_caps or [corrected_length + offset for offset in offsets]

    variants: list[dict[str, Any]] = []
    seen_caps: set[int] = set()
    for cap in sorted(caps):
        if cap < corrected_length or cap in seen_caps:
            continue
        clone = copy.deepcopy(sample)
        clone_constraints = clone["input"]["constraints"]
        clone_constraints["max_chars"] = cap
        clone_constraints["target_chars"] = min(cap, max(corrected_length, existing_target))
        clone_constraints["min_chars"] = min(existing_min, corrected_length)
        errors, _, _ = validate_sample(clone, unified_contract=True)
        if not errors:
            variants.append(clone)
            seen_caps.add(cap)
    return variants


def main() -> None:
    args = build_args()
    rows = read_jsonl(Path(args.raw))
    sample_ids = set(args.sample_id)
    offsets = parse_int_list(args.offsets)
    explicit_caps = parse_int_list(args.caps) if args.caps else None

    selected = [
        row
        for row in rows
        if row.get("task_type") == args.task_type and (not sample_ids or row.get("id") in sample_ids)
    ]

    messages: list[dict[str, Any]] = []
    summary_rows: list[dict[str, Any]] = []
    for row in selected:
        variants = build_variants(row, offsets, explicit_caps)
        for variant in variants:
            messages.append(build_direct_message(SYSTEM_PROMPT, variant))
            summary_rows.append(
                {
                    "id": variant["id"],
                    "max_chars": variant["input"]["constraints"]["max_chars"],
                    "target_chars": variant["input"]["constraints"]["target_chars"],
                    "corrected_length": len(variant["output"]["corrected_text"]),
                }
            )

    write_jsonl(Path(args.output), messages)
    write_json(
        Path(args.summary_out),
        {
            "source_count": len(selected),
            "message_count": len(messages),
            "rows": summary_rows,
        },
    )
    print(f"resume boundary messages={len(messages)}")


if __name__ == "__main__":
    main()

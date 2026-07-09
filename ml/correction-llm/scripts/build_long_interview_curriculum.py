"""Build gate-free long interview direct and repair training messages."""

from __future__ import annotations

import argparse
import copy
import json
import random
from pathlib import Path
from typing import Any

from build_messages import SYSTEM_PROMPT
from build_p1_curriculum import REPAIR_TEMPLATE
from dataset_contract import compact_json
from followup_pipeline_common import build_repair_message, read_jsonl, write_json, write_jsonl


def payload(row: dict[str, Any]) -> dict[str, Any]:
    return json.loads(row["messages"][1]["content"])


def output(row: dict[str, Any]) -> dict[str, Any]:
    return json.loads(row["messages"][-1]["content"])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--messages", required=True)
    parser.add_argument("--exclude-raw", action="append", default=[])
    parser.add_argument("--direct-out", required=True)
    parser.add_argument("--repair-out", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--count", type=int, default=24)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    excluded_ids = {
        row["id"]
        for raw_path in args.exclude_raw
        for row in read_jsonl(Path(raw_path))
    }
    candidates: list[dict[str, Any]] = []
    seen: set[str] = set()
    for row in read_jsonl(Path(args.messages)):
        request = payload(row)
        expected = output(row)
        row_id = request["id"]
        original = request["input"].get("original_text", "")
        corrected = expected.get("corrected_text", "")
        if (
            row_id in excluded_ids
            or row_id in seen
            or request["task_type"] != "INTERVIEW_ANSWER_CORRECTION"
            or len(original) < 300
            or len(corrected) < max(300, int(len(original) * 0.9))
            or expected.get("preserved_meaning") is not True
            or expected.get("added_facts")
            or len(expected.get("changes", [])) < 3
        ):
            continue
        seen.add(row_id)
        candidates.append(row)

    rng = random.Random(args.seed)
    rng.shuffle(candidates)
    if len(candidates) < args.count:
        raise ValueError(f"not enough long interview rows: {len(candidates)} < {args.count}")
    selected = candidates[: args.count]

    repairs: list[dict[str, Any]] = []
    for row in selected:
        request = payload(row)
        expected = output(row)
        invalid = copy.deepcopy(expected)
        corrected = invalid["corrected_text"]
        invalid["corrected_text"] = corrected[: max(1, int(len(corrected) * 0.7))].rstrip()
        sample = {
            "id": request["id"],
            "task_type": request["task_type"],
            "input": request["input"],
            "output": expected,
        }
        min_chars = request["input"].get("constraints", {}).get("min_chars")
        repairs.append(
            build_repair_message(
                SYSTEM_PROMPT,
                REPAIR_TEMPLATE,
                sample,
                previous_output=compact_json(invalid),
                validation_error=f"contract:output.corrected_text length is below min_chars {min_chars}",
            )
        )

    write_jsonl(Path(args.direct_out), selected)
    write_jsonl(Path(args.repair_out), repairs)
    write_json(
        Path(args.summary_out),
        {
            "direct_count": len(selected),
            "repair_count": len(repairs),
            "unique_source_ids": len(selected),
            "excluded_id_count": len(excluded_ids),
            "selected_ids": sorted(payload(row)["id"] for row in selected),
        },
    )
    print(f"long interview direct={len(selected)} repair={len(repairs)}")


if __name__ == "__main__":
    main()

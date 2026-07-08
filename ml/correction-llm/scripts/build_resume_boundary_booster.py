"""Build direct and repair boosters from resume rows already near max length limits."""

from __future__ import annotations

import argparse
import copy
import json
from collections import Counter
from pathlib import Path
from typing import Any

from build_messages import SYSTEM_PROMPT
from build_p1_curriculum import REPAIR_TEMPLATE
from dataset_contract import validate_sample
from followup_pipeline_common import (
    build_direct_message,
    build_repair_message,
    load_message_payload,
    read_jsonl,
    write_json,
    write_jsonl,
)


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--messages", action="append", required=True)
    parser.add_argument("--direct-out", required=True)
    parser.add_argument("--repair-out", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--task-type", default="RESUME_EXPRESSION_IMPROVEMENT")
    parser.add_argument("--max-headroom", type=int, default=25)
    parser.add_argument("--cap-step", type=int, default=5)
    parser.add_argument("--max-variants", type=int, default=6)
    return parser.parse_args()


def load_assistant_output(row: dict[str, Any]) -> dict[str, Any]:
    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) != 3:
        raise ValueError("expected direct 3-message row")
    content = messages[-1].get("content")
    if not isinstance(content, str):
        raise ValueError("assistant content is invalid")
    output = json.loads(content)
    if not isinstance(output, dict):
        raise ValueError("assistant content must be an object")
    return output


def build_sample(payload: dict[str, Any], output: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": payload["id"],
        "task_type": payload["task_type"],
        "input": copy.deepcopy(payload["input"]),
        "output": copy.deepcopy(output),
    }


def eligible_caps(corrected_length: int, max_chars: int, cap_step: int, max_variants: int) -> list[int]:
    values = {corrected_length, max_chars}
    candidate = corrected_length + cap_step
    while candidate < max_chars and len(values) < max_variants:
        values.add(candidate)
        candidate += cap_step
    return sorted(value for value in values if corrected_length <= value <= max_chars)[:max_variants]


def overflow_previous_output(sample: dict[str, Any]) -> tuple[str, str]:
    original_text = str(sample["input"]["original_text"])
    max_chars = int(sample["input"]["constraints"]["max_chars"])
    invalid_output = copy.deepcopy(sample["output"])
    invalid_output["corrected_text"] = original_text
    validation_error = f"contract:output.corrected_text length {len(original_text)} exceeds max_chars {max_chars}"
    return json.dumps(invalid_output, ensure_ascii=False, separators=(",", ":")), validation_error


def clone_with_cap(sample: dict[str, Any], cap: int) -> dict[str, Any] | None:
    clone = copy.deepcopy(sample)
    constraints = clone["input"]["constraints"]
    corrected_length = len(clone["output"]["corrected_text"])
    constraints["max_chars"] = cap
    constraints["target_chars"] = min(cap, max(corrected_length, int(constraints.get("target_chars", corrected_length))))
    constraints["min_chars"] = min(int(constraints.get("min_chars", corrected_length)), corrected_length)
    errors, _, _ = validate_sample(clone, unified_contract=True)
    if errors:
        return None
    return clone


def main() -> None:
    args = build_args()
    direct_rows: list[dict[str, Any]] = []
    repair_rows: list[dict[str, Any]] = []
    selected_ids: list[str] = []
    task_counter: Counter[str] = Counter()
    source_counter: Counter[str] = Counter()
    variant_counter: Counter[str] = Counter()

    seen_pairs: set[tuple[str, int]] = set()
    for message_path in args.messages:
        for row in read_jsonl(Path(message_path)):
            messages = row.get("messages")
            if not isinstance(messages, list) or len(messages) != 3:
                continue
            payload = load_message_payload(row)
            if payload.get("task_type") != args.task_type:
                continue
            output = load_assistant_output(row)
            corrected_text = output.get("corrected_text")
            constraints = payload.get("input", {}).get("constraints", {})
            if not isinstance(corrected_text, str) or not isinstance(constraints, dict):
                continue
            max_chars = int(constraints.get("max_chars", 0) or 0)
            if max_chars <= 0:
                continue
            corrected_length = len(corrected_text)
            headroom = max_chars - corrected_length
            if corrected_length > max_chars or headroom < 0 or headroom > args.max_headroom:
                continue

            source_counter[Path(message_path).name] += 1
            base_sample = build_sample(payload, output)
            for cap in eligible_caps(corrected_length, max_chars, args.cap_step, args.max_variants):
                key = (str(base_sample["id"]), cap)
                if key in seen_pairs:
                    continue
                clone = clone_with_cap(base_sample, cap)
                if clone is None:
                    continue
                seen_pairs.add(key)
                direct_rows.append(build_direct_message(SYSTEM_PROMPT, clone))
                previous_output, validation_error = overflow_previous_output(clone)
                repair_rows.append(
                    build_repair_message(
                        SYSTEM_PROMPT,
                        REPAIR_TEMPLATE,
                        clone,
                        previous_output=previous_output,
                        validation_error=validation_error,
                    )
                )
                selected_ids.append(str(clone["id"]))
                task_counter[str(clone["task_type"])] += 1
                variant_counter[str(cap)] += 1

    write_jsonl(Path(args.direct_out), direct_rows)
    write_jsonl(Path(args.repair_out), repair_rows)
    write_json(
        Path(args.summary_out),
        {
            "direct_count": len(direct_rows),
            "repair_count": len(repair_rows),
            "unique_ids": len(set(selected_ids)),
            "task_counts": dict(sorted(task_counter.items())),
            "source_counts": dict(sorted(source_counter.items())),
            "cap_counts": dict(sorted(variant_counter.items(), key=lambda item: int(item[0]))),
            "max_headroom": args.max_headroom,
            "cap_step": args.cap_step,
            "max_variants": args.max_variants,
        },
    )
    print(f"direct={len(direct_rows)} repair={len(repair_rows)}")


if __name__ == "__main__":
    main()

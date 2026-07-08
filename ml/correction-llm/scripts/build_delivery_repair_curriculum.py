"""Build gate-free repair examples for the final E correction delivery candidate."""

from __future__ import annotations

import argparse
import copy
import json
import random
import re
from collections import Counter
from pathlib import Path
from typing import Any, Callable

from build_messages import SYSTEM_PROMPT
from build_p1_curriculum import REPAIR_TEMPLATE
from dataset_contract import compact_json
from followup_pipeline_common import build_repair_message, read_jsonl, write_json, write_jsonl


def payload(row: dict[str, Any]) -> dict[str, Any]:
    return json.loads(row["messages"][1]["content"])


def output(row: dict[str, Any]) -> dict[str, Any]:
    return json.loads(row["messages"][-1]["content"])


def sample(row: dict[str, Any]) -> dict[str, Any]:
    row_payload = payload(row)
    return {
        "id": row_payload["id"],
        "task_type": row_payload["task_type"],
        "input": row_payload["input"],
        "output": output(row),
    }


def paragraph_count(text: str) -> int:
    value = text.strip()
    return len(re.split(r"\n\s*\n", value)) if value else 0


def is_valid_target(row: dict[str, Any]) -> bool:
    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) != 3:
        return False
    value = output(row)
    return (
        value.get("preserved_meaning") is True
        and not value.get("added_facts")
        and len(value.get("changes", [])) >= 3
    )


def select_rows(
    rows: list[dict[str, Any]],
    excluded_ids: set[str],
    count: int,
    predicate: Callable[[dict[str, Any]], bool],
    rng: random.Random,
) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    seen: set[str] = set()
    for row in rows:
        row_id = payload(row)["id"]
        if row_id in excluded_ids or row_id in seen or not is_valid_target(row) or not predicate(row):
            continue
        candidates.append(row)
        seen.add(row_id)
    rng.shuffle(candidates)
    if len(candidates) < count:
        raise ValueError(f"not enough delivery curriculum rows: {len(candidates)} < {count}")
    return candidates[:count]


def paragraph_repair(row: dict[str, Any]) -> dict[str, Any]:
    target = sample(row)
    invalid = copy.deepcopy(target["output"])
    invalid["corrected_text"] = re.sub(r"\n\s*\n", " ", invalid["corrected_text"])
    expected = paragraph_count(target["input"]["original_text"])
    return build_repair_message(
        SYSTEM_PROMPT,
        REPAIR_TEMPLATE,
        target,
        previous_output=compact_json(invalid),
        validation_error=f"contract:output paragraphs 1 do not preserve source paragraphs {expected}",
    )


def cjk_repair(row: dict[str, Any]) -> dict[str, Any]:
    target = sample(row)
    invalid = copy.deepcopy(target["output"])
    invalid["changes"][0]["reason"] += " 的同时"
    return build_repair_message(
        SYSTEM_PROMPT,
        REPAIR_TEMPLATE,
        target,
        previous_output=compact_json(invalid),
        validation_error="CJK_LEAK",
    )


def changes_repair(row: dict[str, Any]) -> dict[str, Any]:
    target = sample(row)
    invalid = copy.deepcopy(target["output"])
    invalid["changes"] = invalid["changes"][:1]
    return build_repair_message(
        SYSTEM_PROMPT,
        REPAIR_TEMPLATE,
        target,
        previous_output=compact_json(invalid),
        validation_error="contract:output.changes must contain at least 3 items",
    )


def json_repair(row: dict[str, Any]) -> dict[str, Any]:
    target = sample(row)
    invalid = compact_json(target["output"])
    return build_repair_message(
        SYSTEM_PROMPT,
        REPAIR_TEMPLATE,
        target,
        previous_output=invalid[: max(1, len(invalid) // 2)],
        validation_error="JSON_PARSE_FAIL",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--messages", required=True)
    parser.add_argument("--exclude-raw", action="append", default=[])
    parser.add_argument("--output", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--paragraph-per-task", type=int, default=12)
    parser.add_argument("--cjk-count", type=int, default=12)
    parser.add_argument("--changes-count", type=int, default=12)
    parser.add_argument("--json-count", type=int, default=12)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    rows = read_jsonl(Path(args.messages))
    excluded_ids = {
        row["id"]
        for raw_path in args.exclude_raw
        for row in read_jsonl(Path(raw_path))
    }

    selected_ids: set[str] = set()

    def take(count: int, predicate: Callable[[dict[str, Any]], bool]) -> list[dict[str, Any]]:
        selected = select_rows(rows, excluded_ids | selected_ids, count, predicate, rng)
        selected_ids.update(payload(row)["id"] for row in selected)
        return selected

    paragraph_rows: list[dict[str, Any]] = []
    for task in ("PORTFOLIO_DESCRIPTION_IMPROVEMENT", "SELF_INTRO_CORRECTION"):
        paragraph_rows.extend(
            take(
                args.paragraph_per_task,
                lambda row, task=task: payload(row)["task_type"] == task
                and bool(payload(row)["input"].get("constraints", {}).get("preserve_paragraphs"))
                and paragraph_count(payload(row)["input"].get("original_text", "")) >= 3
                and paragraph_count(output(row).get("corrected_text", ""))
                == paragraph_count(payload(row)["input"].get("original_text", "")),
            )
        )

    cjk_rows = take(args.cjk_count, lambda row: True)
    changes_rows = take(
        args.changes_count,
        lambda row: payload(row)["task_type"] == "RESUME_EXPRESSION_IMPROVEMENT",
    )
    json_rows = take(args.json_count, lambda row: True)

    curriculum = (
        [paragraph_repair(row) for row in paragraph_rows]
        + [cjk_repair(row) for row in cjk_rows]
        + [changes_repair(row) for row in changes_rows]
        + [json_repair(row) for row in json_rows]
    )
    rng.shuffle(curriculum)
    write_jsonl(Path(args.output), curriculum)
    write_json(
        Path(args.summary_out),
        {
            "count": len(curriculum),
            "unique_source_ids": len(selected_ids),
            "excluded_id_count": len(excluded_ids),
            "category_counts": {
                "paragraph_contract": len(paragraph_rows),
                "cjk_leak": len(cjk_rows),
                "changes_contract": len(changes_rows),
                "json_parse_fail": len(json_rows),
            },
            "task_counts": dict(sorted(Counter(payload(row)["task_type"] for row in paragraph_rows + cjk_rows + changes_rows + json_rows).items())),
        },
    )
    print(f"delivery repair messages={len(curriculum)}")


if __name__ == "__main__":
    main()

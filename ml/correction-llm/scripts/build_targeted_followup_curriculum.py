"""Build a small follow-up curriculum for length-overflow repair and risky replay."""

from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
from typing import Any

from build_messages import SYSTEM_PROMPT
from build_p1_curriculum import REPAIR_TEMPLATE
from dataset_contract import compact_json

RISKY_GATE_IDS = [
    "e-stage100-interview-0008",
    "e-stage100-portfolio-0001",
    "e-stage100-self-intro-0006",
]
RESUME_GATE_ID = "e-stage100-resume-0002"
ALL_GATE_IDS = set(RISKY_GATE_IDS + [RESUME_GATE_ID])


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


def message_id(row: dict[str, Any]) -> str:
    payload = json.loads(row["messages"][1]["content"])
    value = payload.get("id")
    if not isinstance(value, str) or not value:
        raise ValueError("message row id is missing")
    return value


def message_payload(row: dict[str, Any]) -> dict[str, Any]:
    payload = json.loads(row["messages"][1]["content"])
    if not isinstance(payload, dict):
        raise ValueError("message payload must be an object")
    return payload


def message_output(row: dict[str, Any]) -> dict[str, Any]:
    output = json.loads(row["messages"][-1]["content"])
    if not isinstance(output, dict):
        raise ValueError("message assistant content must be an object")
    return output


def task_type(row: dict[str, Any]) -> str:
    value = message_payload(row).get("task_type")
    if not isinstance(value, str) or not value:
        raise ValueError("task_type is missing")
    return value


def constraints(row: dict[str, Any]) -> dict[str, Any]:
    value = message_payload(row).get("input", {}).get("constraints", {})
    if not isinstance(value, dict):
        raise ValueError("constraints must be an object")
    return value


def build_repair_row(
    row: dict[str, Any],
    previous_output: dict[str, Any],
    validation_error: str,
) -> dict[str, Any]:
    payload = message_payload(row)
    assistant = json.loads(row["messages"][-1]["content"])
    repair_prompt = REPAIR_TEMPLATE.format(
        validation_error=validation_error,
        previous_output=compact_json(previous_output),
    )
    repair_payload = {
        "id": payload["id"],
        "task_type": payload["task_type"],
        "input": payload["input"],
    }
    return {
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": compact_json(repair_payload)},
            {"role": "user", "content": repair_prompt},
            {"role": "assistant", "content": compact_json(assistant)},
        ]
    }


def risky_direct_rows(rows: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    selected: dict[str, list[dict[str, Any]]] = {}
    seen_ids: set[str] = set()
    for row in rows:
        sample_id = message_id(row)
        if sample_id in ALL_GATE_IDS:
            continue
        if sample_id in seen_ids:
            continue
        output = message_output(row)
        if not output.get("risk_flags"):
            continue
        if output.get("preserved_meaning") is not True or output.get("added_facts"):
            continue
        if len(output.get("changes", [])) < 3:
            continue
        task = task_type(row)
        bucket = selected.setdefault(task, [])
        if len(bucket) >= 2:
            continue
        bucket.append(row)
        seen_ids.add(sample_id)
    return selected


def compact_resume_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for row in rows:
        sample_id = message_id(row)
        if sample_id == RESUME_GATE_ID:
            continue
        if sample_id in seen_ids:
            continue
        if task_type(row) != "RESUME_EXPRESSION_IMPROVEMENT":
            continue
        row_constraints = constraints(row)
        max_chars = int(row_constraints.get("max_chars", 0) or 0)
        if not max_chars or max_chars > 300:
            continue
        output = message_output(row)
        corrected_text = output.get("corrected_text")
        if not isinstance(corrected_text, str) or len(corrected_text) > max_chars:
            continue
        selected.append(row)
        seen_ids.add(sample_id)
        if len(selected) >= 6:
            break
    return selected


def synthetic_length_overflow(
    output: dict[str, Any],
    original_text: str,
    max_chars: int,
) -> tuple[dict[str, Any], str]:
    invalid = copy.deepcopy(output)
    invalid["corrected_text"] = original_text
    actual_len = len(original_text)
    validation_error = f"contract:output.corrected_text length {actual_len} exceeds max_chars {max_chars}"
    return invalid, validation_error


def build_dataset(
    stage100_messages: list[dict[str, Any]],
    riskonly_repair_report: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any]]:
    rows_by_id = {message_id(row): row for row in stage100_messages}

    train_rows: list[dict[str, Any]] = []
    val_rows: list[dict[str, Any]] = []

    # Direct replay: exact gate rows plus compact/risky replay.
    train_rows.extend(rows_by_id[sample_id] for sample_id in sorted(ALL_GATE_IDS))
    risky_replay_by_task = risky_direct_rows(stage100_messages)
    compact_resume = compact_resume_rows(stage100_messages)
    compact_resume_ids = {message_id(row) for row in compact_resume}
    train_risky: list[dict[str, Any]] = []
    val_risky: list[dict[str, Any]] = []
    for task_rows in risky_replay_by_task.values():
        filtered = [row for row in task_rows if message_id(row) not in compact_resume_ids]
        if filtered:
            train_risky.append(filtered[0])
        if len(filtered) > 1:
            val_risky.append(filtered[1])
    train_rows.extend(train_risky)
    train_rows.extend(compact_resume[:4])
    heldout_resume = compact_resume[4:]
    val_rows.extend(heldout_resume)

    # Held-out direct validation rows from non-gate risky samples.
    val_rows.extend(val_risky)

    # Synthetic risky repairs: same runtime prompt shape, but only risk_flags missing.
    for sample_id in RISKY_GATE_IDS:
        row = rows_by_id[sample_id]
        invalid = copy.deepcopy(message_output(row))
        invalid["risk_flags"] = []
        train_rows.append(
            build_repair_row(
                row,
                invalid,
                "risk_flags_missing_for_expected_risky_sample",
            )
        )

    if val_rows:
        for row in val_rows[:2]:
            invalid = copy.deepcopy(message_output(row))
            invalid["risk_flags"] = []
            val_rows.append(
                build_repair_row(
                    row,
                    invalid,
                    "risk_flags_missing_for_expected_risky_sample",
                )
            )

    # Resume length overflow repair using the actual failed riskonly repair output.
    resume_row = rows_by_id[RESUME_GATE_ID]
    riskonly_resume = next((row for row in riskonly_repair_report if row.get("id") == RESUME_GATE_ID), None)
    if riskonly_resume is None:
        raise ValueError(f"riskonly repair report is missing {RESUME_GATE_ID}")
    previous_output = json.loads(str(riskonly_resume["output"]))
    validation_error = "; ".join(str(item) for item in riskonly_resume.get("problems", []))
    train_rows.append(build_repair_row(resume_row, previous_output, validation_error))

    # Additional synthetic resume overflow repairs from compact replay rows.
    for row in compact_resume[:3]:
        payload = message_payload(row)
        output = message_output(row)
        original_text = str(payload["input"]["original_text"])
        max_chars = int(payload["input"]["constraints"]["max_chars"])
        invalid, synthetic_error = synthetic_length_overflow(output, original_text, max_chars)
        train_rows.append(build_repair_row(row, invalid, synthetic_error))
    if heldout_resume:
        row = heldout_resume[0]
        payload = message_payload(row)
        output = message_output(row)
        original_text = str(payload["input"]["original_text"])
        max_chars = int(payload["input"]["constraints"]["max_chars"])
        invalid, synthetic_error = synthetic_length_overflow(output, original_text, max_chars)
        val_rows.append(build_repair_row(row, invalid, synthetic_error))

    train_ids = {message_id(row) for row in train_rows}
    val_ids = {message_id(row) for row in val_rows}
    overlap = sorted(train_ids & val_ids)
    if overlap:
        raise ValueError(f"train/val IDs overlap: {overlap}")
    summary = {
        "train_count": len(train_rows),
        "val_count": len(val_rows),
        "train_unique_ids": len(train_ids),
        "val_unique_ids": len(val_ids),
        "train_val_id_overlap": overlap,
        "train_gate_ids": sorted(ALL_GATE_IDS),
        "risky_train_replay_count": len(train_risky),
        "risky_val_replay_count": len(val_risky),
        "compact_resume_replay_count": len(compact_resume),
    }
    return train_rows, val_rows, summary


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage100-messages", required=True)
    parser.add_argument("--riskonly-repair-report", required=True)
    parser.add_argument("--train-out", required=True)
    parser.add_argument("--val-out", required=True)
    parser.add_argument("--summary-out", required=True)
    args = parser.parse_args()

    train_rows, val_rows, summary = build_dataset(
        read_jsonl(Path(args.stage100_messages)),
        read_jsonl(Path(args.riskonly_repair_report)),
    )
    write_jsonl(Path(args.train_out), train_rows)
    write_jsonl(Path(args.val_out), val_rows)
    Path(args.summary_out).write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

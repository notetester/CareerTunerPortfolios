"""Validate E correction raw JSONL samples."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

ALLOWED_TASKS = {
    "SELF_INTRO_CORRECTION",
    "INTERVIEW_ANSWER_CORRECTION",
    "RESUME_EXPRESSION_IMPROVEMENT",
    "PORTFOLIO_DESCRIPTION_IMPROVEMENT",
}
ALLOWED_EVIDENCE = {"original_text", "user_profile_facts", "job_context"}

REQUIRED_TOP = {"id", "task_type", "input", "output"}
REQUIRED_INPUT = {"original_text", "target_role", "job_context", "user_profile_facts", "constraints"}
REQUIRED_OUTPUT = {
    "status",
    "task_type",
    "corrected_text",
    "summary",
    "changes",
    "risk_flags",
    "preserved_meaning",
    "added_facts",
    "recommended_keywords",
    "confidence",
}
REQUIRED_CHANGE = {"before", "after", "reason", "evidence_source"}


def load_jsonl(path: Path) -> list[dict]:
    rows: list[dict] = []
    with path.open("r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no}: JSON parse failed: {exc}") from exc
            row["_line_no"] = line_no
            rows.append(row)
    return rows


def validate(rows: list[dict]) -> tuple[dict, list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    ids: list[str] = []
    task_counts: Counter[str] = Counter()
    risk_count = 0
    hardcase_count = 0

    for row in rows:
        line_no = row.get("_line_no", "?")
        sample_id = row.get("id", f"line-{line_no}")
        ids.append(sample_id)

        missing_top = REQUIRED_TOP - set(row)
        if missing_top:
            errors.append(f"{sample_id}: missing top keys {sorted(missing_top)}")
            continue

        task_type = row["task_type"]
        task_counts[task_type] += 1
        if task_type not in ALLOWED_TASKS:
            errors.append(f"{sample_id}: invalid task_type {task_type}")

        input_obj = row["input"]
        output_obj = row["output"]
        missing_input = REQUIRED_INPUT - set(input_obj)
        missing_output = REQUIRED_OUTPUT - set(output_obj)
        if missing_input:
            errors.append(f"{sample_id}: missing input keys {sorted(missing_input)}")
        if missing_output:
            errors.append(f"{sample_id}: missing output keys {sorted(missing_output)}")

        if task_type != output_obj.get("task_type"):
            errors.append(f"{sample_id}: output.task_type mismatch")
        if output_obj.get("status") != "ok":
            warnings.append(f"{sample_id}: output.status is not ok")

        confidence = output_obj.get("confidence")
        if not isinstance(confidence, (int, float)) or not 0 <= confidence <= 1:
            errors.append(f"{sample_id}: confidence must be 0..1")

        if output_obj.get("risk_flags"):
            risk_count += 1
        if output_obj.get("preserved_meaning") is not True:
            hardcase_count += 1
            warnings.append(f"{sample_id}: preserved_meaning is not true; keep out of first train split")
        if output_obj.get("added_facts"):
            warnings.append(f"{sample_id}: added_facts is non-empty; inspect before training")

        changes = output_obj.get("changes")
        if not isinstance(changes, list) or not changes:
            errors.append(f"{sample_id}: changes must be non-empty list")
            continue
        for idx, change in enumerate(changes, 1):
            missing_change = REQUIRED_CHANGE - set(change)
            if missing_change:
                errors.append(f"{sample_id}: change {idx} missing keys {sorted(missing_change)}")
            evidence = change.get("evidence_source")
            if evidence not in ALLOWED_EVIDENCE:
                errors.append(f"{sample_id}: change {idx} invalid evidence_source {evidence}")

    for sample_id, count in Counter(ids).items():
        if count > 1:
            errors.append(f"{sample_id}: duplicated id count={count}")

    summary = {
        "count": len(rows),
        "task_counts": dict(task_counts),
        "risk_flags_nonempty": risk_count,
        "hardcase_count": hardcase_count,
        "errors": len(errors),
        "warnings": len(warnings),
    }
    return summary, errors, warnings


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--summary-out", default=None)
    parser.add_argument("--fail-on-warnings", action="store_true")
    args = parser.parse_args()

    rows = load_jsonl(Path(args.input))
    for row in rows:
        row.pop("_line_no", None)
    summary, errors, warnings = validate(rows)

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
        Path(args.summary_out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.summary_out).write_text(
            json.dumps({"summary": summary, "errors": errors, "warnings": warnings}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    if errors or (args.fail_on_warnings and warnings):
        raise SystemExit(1)


if __name__ == "__main__":
    main()

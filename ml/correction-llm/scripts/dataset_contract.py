"""Shared schema and quality contract for E correction datasets and evals."""

from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class TaskRule:
    input_min: int
    input_max: int
    output_ratio_min: float
    output_ratio_max: float
    preserve_paragraphs: bool = False


TASK_RULES: dict[str, TaskRule] = {
    "SELF_INTRO_CORRECTION": TaskRule(500, 1500, 0.85, 1.10, True),
    "INTERVIEW_ANSWER_CORRECTION": TaskRule(250, 700, 0.90, 1.25),
    "RESUME_EXPRESSION_IMPROVEMENT": TaskRule(150, 600, 0.80, 1.15),
    "PORTFOLIO_DESCRIPTION_IMPROVEMENT": TaskRule(300, 1000, 0.85, 1.15, True),
}

ALLOWED_TASKS = set(TASK_RULES)
ALLOWED_EVIDENCE = {"original_text", "user_profile_facts", "job_context"}
REQUIRED_TOP = {"id", "task_type", "input", "output"}
REQUIRED_INPUT = {"original_text", "target_role", "job_context", "user_profile_facts", "constraints"}
REQUIRED_CONSTRAINTS = {
    "tone",
    "min_chars",
    "target_chars",
    "max_chars",
    "preserve_paragraphs",
    "preserve_facts_only",
}
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

_WHITESPACE = re.compile(r"\s+")
_PARAGRAPH_BREAK = re.compile(r"\n\s*\n")


def normalized_text(value: str) -> str:
    return _WHITESPACE.sub(" ", value.strip()).lower()


def sample_fingerprint(row: dict[str, Any]) -> str:
    task_type = str(row.get("task_type", ""))
    input_obj = row.get("input") if isinstance(row.get("input"), dict) else {}
    original = str(input_obj.get("original_text", ""))
    return correction_fingerprint(task_type, original)


def correction_fingerprint(task_type: str, original: str) -> str:
    payload = f"{task_type}\n{normalized_text(original)}".encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def paragraph_count(value: str) -> int:
    text = value.strip()
    if not text:
        return 0
    return len([part for part in _PARAGRAPH_BREAK.split(text) if part.strip()])


def length_bucket(task_type: str, input_length: int) -> str:
    rule = TASK_RULES.get(task_type)
    if rule is None:
        return "unknown"
    if input_length < rule.input_min:
        return "short"
    if input_length <= rule.input_max:
        midpoint = rule.input_min + ((rule.input_max - rule.input_min) // 2)
        return "target-low" if input_length <= midpoint else "target-high"
    return "overlong"


def validate_sample(
    row: dict[str, Any],
    *,
    unified_contract: bool,
) -> tuple[list[str], list[str], dict[str, Any]]:
    errors: list[str] = []
    warnings: list[str] = []
    metrics: dict[str, Any] = {}

    _require_exact_keys(row, REQUIRED_TOP, "top", errors)
    task_type = row.get("task_type")
    if task_type not in ALLOWED_TASKS:
        errors.append(f"invalid task_type {task_type}")
        return errors, warnings, metrics

    input_obj = row.get("input")
    output_obj = row.get("output")
    if not isinstance(input_obj, dict):
        errors.append("input must be an object")
        return errors, warnings, metrics
    if not isinstance(output_obj, dict):
        errors.append("output must be an object")
        return errors, warnings, metrics

    _require_exact_keys(input_obj, REQUIRED_INPUT, "input", errors)
    _require_exact_keys(output_obj, REQUIRED_OUTPUT, "output", errors)

    original = _required_text(input_obj, "original_text", errors)
    _required_text(input_obj, "target_role", errors)
    if not isinstance(input_obj.get("job_context"), dict):
        errors.append("input.job_context must be an object")
    _string_list(input_obj.get("user_profile_facts"), "input.user_profile_facts", errors)

    constraints = input_obj.get("constraints")
    if not isinstance(constraints, dict):
        errors.append("input.constraints must be an object")
        constraints = {}
    elif unified_contract:
        _require_exact_keys(constraints, REQUIRED_CONSTRAINTS, "input.constraints", errors)
        _validate_constraints(constraints, errors)

    if output_obj.get("status") != "ok":
        errors.append("output.status must be ok")
    if output_obj.get("task_type") != task_type:
        errors.append("output.task_type must match task_type")
    corrected = _required_text(output_obj, "corrected_text", errors)
    _required_text(output_obj, "summary", errors)

    changes = output_obj.get("changes")
    minimum_changes = 3 if unified_contract else 1
    if not isinstance(changes, list) or len(changes) < minimum_changes:
        errors.append(f"output.changes must contain at least {minimum_changes} items")
    elif isinstance(changes, list):
        for index, change in enumerate(changes, 1):
            if not isinstance(change, dict):
                errors.append(f"output.changes[{index}] must be an object")
                continue
            _require_exact_keys(change, REQUIRED_CHANGE, f"output.changes[{index}]", errors)
            for key in ("before", "after", "reason"):
                _required_text(change, key, errors, f"output.changes[{index}]")
            if change.get("evidence_source") not in ALLOWED_EVIDENCE:
                errors.append(f"output.changes[{index}].evidence_source is invalid")

    for key in ("risk_flags", "added_facts", "recommended_keywords"):
        _string_list(output_obj.get(key), f"output.{key}", errors)

    preserved = output_obj.get("preserved_meaning")
    if preserved is not True:
        message = "output.preserved_meaning must be true"
        (errors if unified_contract else warnings).append(message)
    added_facts = output_obj.get("added_facts")
    if isinstance(added_facts, list) and added_facts:
        message = "output.added_facts must be empty"
        (errors if unified_contract else warnings).append(message)

    confidence = output_obj.get("confidence")
    if isinstance(confidence, bool) or not isinstance(confidence, (int, float)) or not 0 <= confidence <= 1:
        errors.append("output.confidence must be a number between 0 and 1")

    if original and corrected:
        original_length = len(original)
        corrected_length = len(corrected)
        ratio = corrected_length / original_length
        metrics.update(
            {
                "input_length": original_length,
                "output_length": corrected_length,
                "output_ratio": ratio,
                "input_paragraphs": paragraph_count(original),
                "output_paragraphs": paragraph_count(corrected),
                "length_bucket": length_bucket(task_type, original_length),
            }
        )
        if unified_contract:
            _validate_lengths(task_type, original, corrected, constraints, errors)

    return errors, warnings, metrics


def _validate_constraints(constraints: dict[str, Any], errors: list[str]) -> None:
    if not isinstance(constraints.get("tone"), str) or not constraints.get("tone", "").strip():
        errors.append("input.constraints.tone must be a non-empty string")
    values = []
    for key in ("min_chars", "target_chars", "max_chars"):
        value = constraints.get(key)
        if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
            errors.append(f"input.constraints.{key} must be a positive integer")
        values.append(value)
    if all(isinstance(value, int) and not isinstance(value, bool) for value in values):
        if not values[0] <= values[1] <= values[2]:
            errors.append("input.constraints lengths must satisfy min_chars <= target_chars <= max_chars")
    for key in ("preserve_paragraphs", "preserve_facts_only"):
        if not isinstance(constraints.get(key), bool):
            errors.append(f"input.constraints.{key} must be boolean")
    if constraints.get("preserve_facts_only") is not True:
        errors.append("input.constraints.preserve_facts_only must be true")


def _validate_lengths(
    task_type: str,
    original: str,
    corrected: str,
    constraints: dict[str, Any],
    errors: list[str],
) -> None:
    rule = TASK_RULES[task_type]
    input_length = len(original)
    output_length = len(corrected)
    ratio = output_length / input_length
    if not rule.input_min <= input_length <= rule.input_max:
        errors.append(
            f"input.original_text length {input_length} is outside {rule.input_min}..{rule.input_max}"
        )
    if not rule.output_ratio_min <= ratio <= rule.output_ratio_max:
        errors.append(
            f"output.corrected_text ratio {ratio:.3f} is outside "
            f"{rule.output_ratio_min:.2f}..{rule.output_ratio_max:.2f}"
        )
    min_chars = constraints.get("min_chars")
    max_chars = constraints.get("max_chars")
    if isinstance(min_chars, int) and output_length < min_chars:
        errors.append(f"output.corrected_text length {output_length} is below min_chars {min_chars}")
    if isinstance(max_chars, int) and output_length > max_chars:
        errors.append(f"output.corrected_text length {output_length} exceeds max_chars {max_chars}")

    preserve_requested = constraints.get("preserve_paragraphs") is True
    if rule.preserve_paragraphs and not preserve_requested:
        errors.append("input.constraints.preserve_paragraphs must be true for this task")
    if preserve_requested:
        source_paragraphs = paragraph_count(original)
        result_paragraphs = paragraph_count(corrected)
        if source_paragraphs >= 2 and result_paragraphs < source_paragraphs:
            errors.append(
                f"output paragraphs {result_paragraphs} do not preserve source paragraphs {source_paragraphs}"
            )


def _require_exact_keys(
    value: dict[str, Any],
    required: set[str],
    label: str,
    errors: list[str],
) -> None:
    keys = set(value)
    missing = required - keys
    extra = keys - required
    if missing:
        errors.append(f"{label} is missing keys {sorted(missing)}")
    if extra:
        errors.append(f"{label} has extra keys {sorted(extra)}")


def _required_text(
    value: dict[str, Any],
    key: str,
    errors: list[str],
    prefix: str | None = None,
) -> str:
    item = value.get(key)
    label = f"{prefix}.{key}" if prefix else key
    if not isinstance(item, str) or not item.strip():
        errors.append(f"{label} must be a non-empty string")
        return ""
    return item.strip()


def _string_list(value: Any, label: str, errors: list[str]) -> list[str]:
    if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
        errors.append(f"{label} must be an array of strings")
        return []
    return value


def compact_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))

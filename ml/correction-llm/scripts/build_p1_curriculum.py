"""Build P1 long-form and contract-repair SFT curriculum datasets."""

from __future__ import annotations

import argparse
import copy
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from build_messages import SYSTEM_PROMPT
from dataset_contract import compact_json

REPAIR_TEMPLATE = """이전 응답이 출력 계약 검증에 실패했다. 첨삭을 새로 요약하거나 설명하지 말고,
원문과 이전 corrected_text의 핵심 내용 및 분량을 유지하면서 완전한 JSON 객체 하나를 다시 작성한다.

검증 실패 사유: {validation_error}

이전 응답:
<invalid_output>
{previous_output}
</invalid_output>

필수 조건:
- status, task_type, corrected_text, summary, changes, risk_flags, preserved_meaning,
  added_facts, recommended_keywords, confidence의 10개 키를 모두 포함한다.
- changes는 3개 이상이어야 하며 각 항목은 before, after, reason, evidence_source를 모두 포함한다.
- preserved_meaning은 true, added_facts는 빈 배열로 작성한다.
- 부분 수정본이나 설명문이 아니라 전체 JSON 객체만 반환한다."""

REPAIR_VARIANTS = {
    "missing_changes": "Correction self LLM output validation failed: root has missing or extra keys.",
    "preserved_false": "Correction self LLM output validation failed: preserved_meaning must be true.",
}


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no}: JSON parse failed: {exc}") from exc
            if not isinstance(row, dict):
                raise ValueError(f"{path}:{line_no}: row must be an object")
            rows.append(row)
    return rows


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(compact_json(row) + "\n")


def raw_id(row: dict[str, Any]) -> str:
    value = row.get("id")
    if not isinstance(value, str) or not value:
        raise ValueError("raw row id is missing")
    return value


def raw_task(row: dict[str, Any]) -> str:
    value = row.get("task_type")
    if not isinstance(value, str) or not value:
        raise ValueError(f"{raw_id(row)}: task_type is missing")
    return value


def message_payload(row: dict[str, Any]) -> dict[str, Any]:
    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) < 3:
        raise ValueError("message row must contain system, user, and assistant messages")
    user = messages[1]
    if not isinstance(user, dict) or not isinstance(user.get("content"), str):
        raise ValueError("message row user content is invalid")
    try:
        payload = json.loads(user["content"])
    except json.JSONDecodeError as exc:
        raise ValueError("message row user content is not JSON") from exc
    if not isinstance(payload, dict):
        raise ValueError("message row user payload must be an object")
    return payload


def message_id(row: dict[str, Any]) -> str:
    value = message_payload(row).get("id")
    if not isinstance(value, str) or not value:
        raise ValueError("message row id is missing")
    return value


def invalid_output(output: dict[str, Any], variant: str) -> dict[str, Any]:
    value = copy.deepcopy(output)
    if variant == "missing_changes":
        value.pop("changes", None)
    elif variant == "preserved_false":
        value["preserved_meaning"] = False
    else:
        raise ValueError(f"unsupported repair variant: {variant}")
    return value


def repair_message(row: dict[str, Any], variant: str) -> dict[str, Any]:
    sample_id = raw_id(row)
    task_type = raw_task(row)
    input_obj = row.get("input")
    output = row.get("output")
    if not isinstance(input_obj, dict) or not isinstance(output, dict):
        raise ValueError(f"{sample_id}: input and output must be objects")
    if output.get("preserved_meaning") is not True or output.get("added_facts"):
        raise ValueError(f"{sample_id}: repair target must preserve meaning without added facts")

    payload = {"id": sample_id, "task_type": task_type, "input": input_obj}
    previous = invalid_output(output, variant)
    repair = REPAIR_TEMPLATE.format(
        validation_error=REPAIR_VARIANTS[variant],
        previous_output=compact_json(previous),
    )
    return {
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": compact_json(payload)},
            {"role": "user", "content": repair},
            {"role": "assistant", "content": compact_json(output)},
        ]
    }


def select_per_task(
    rows: list[dict[str, Any]],
    limit: int,
    *,
    exclude_ids: set[str] | None = None,
) -> list[dict[str, Any]]:
    excluded = exclude_ids or set()
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        if raw_id(row) in excluded:
            continue
        output = row.get("output")
        if not isinstance(output, dict):
            continue
        if output.get("preserved_meaning") is not True or output.get("added_facts"):
            continue
        changes = output.get("changes")
        if not isinstance(changes, list) or len(changes) < 3:
            continue
        grouped[raw_task(row)].append(row)
    selected: list[dict[str, Any]] = []
    for task_type in sorted(grouped):
        candidates = sorted(grouped[task_type], key=raw_id)
        if len(candidates) < limit:
            raise ValueError(
                f"{task_type}: only {len(candidates)} valid repair targets, requested {limit}"
            )
        selected.extend(candidates[:limit])
    return selected


def build_curriculum(
    repair_train_raw: list[dict[str, Any]],
    repair_val_raw: list[dict[str, Any]],
    long_train: list[dict[str, Any]],
    long_val: list[dict[str, Any]],
    *,
    repair_train_per_task: int,
    repair_val_per_task: int,
    seed: int,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any]]:
    long_train_ids = {message_id(row) for row in long_train}
    long_val_ids = {message_id(row) for row in long_val}
    long_overlap = long_train_ids & long_val_ids
    if long_overlap:
        raise ValueError(f"long train/val IDs overlap: {sorted(long_overlap)}")

    val_raw = select_per_task(
        repair_val_raw,
        repair_val_per_task,
        exclude_ids=long_train_ids,
    )
    val_ids = {raw_id(row) for row in val_raw}
    train_raw = select_per_task(
        repair_train_raw,
        repair_train_per_task,
        exclude_ids=val_ids | long_val_ids,
    )
    train_ids = {raw_id(row) for row in train_raw}
    overlap = train_ids & val_ids
    if overlap:
        raise ValueError(f"repair train/val IDs overlap: {sorted(overlap)}")

    all_train_ids = train_ids | long_train_ids
    all_val_ids = val_ids | long_val_ids
    all_overlap = all_train_ids & all_val_ids
    if all_overlap:
        raise ValueError(f"curriculum train/val IDs overlap: {sorted(all_overlap)}")

    train_tagged: list[tuple[str, str, dict[str, Any]]] = []
    for row in train_raw:
        for variant in REPAIR_VARIANTS:
            train_tagged.append((f"repair:{variant}", raw_task(row), repair_message(row, variant)))
    for row in long_train:
        task_type = str(message_payload(row).get("task_type", "unknown"))
        train_tagged.append(("long", task_type, row))
        if task_type in {"SELF_INTRO_CORRECTION", "INTERVIEW_ANSWER_CORRECTION"}:
            train_tagged.append(("long:targeted-repeat", task_type, copy.deepcopy(row)))

    val_tagged: list[tuple[str, str, dict[str, Any]]] = []
    for row in val_raw:
        for variant in REPAIR_VARIANTS:
            val_tagged.append((f"repair:{variant}", raw_task(row), repair_message(row, variant)))
    for row in long_val:
        task_type = str(message_payload(row).get("task_type", "unknown"))
        val_tagged.append(("long", task_type, row))

    rng = random.Random(seed)
    rng.shuffle(train_tagged)
    rng.shuffle(val_tagged)

    summary = {
        "train_count": len(train_tagged),
        "val_count": len(val_tagged),
        "train_kind_counts": dict(sorted(Counter(kind for kind, _, _ in train_tagged).items())),
        "val_kind_counts": dict(sorted(Counter(kind for kind, _, _ in val_tagged).items())),
        "train_task_counts": dict(sorted(Counter(task for _, task, _ in train_tagged).items())),
        "val_task_counts": dict(sorted(Counter(task for _, task, _ in val_tagged).items())),
        "repair_train_source_count": len(train_raw),
        "repair_val_source_count": len(val_raw),
        "train_unique_id_count": len(all_train_ids),
        "val_unique_id_count": len(all_val_ids),
        "train_val_id_overlap_count": 0,
        "seed": seed,
    }
    return (
        [row for _, _, row in train_tagged],
        [row for _, _, row in val_tagged],
        summary,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repair-train-raw", required=True)
    parser.add_argument("--repair-val-raw", required=True)
    parser.add_argument("--long-train-messages", required=True)
    parser.add_argument("--long-val-messages", required=True)
    parser.add_argument("--train-out", required=True)
    parser.add_argument("--val-out", required=True)
    parser.add_argument("--summary-out", default=None)
    parser.add_argument("--repair-train-per-task", type=int, default=20)
    parser.add_argument("--repair-val-per-task", type=int, default=10)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    train, val, summary = build_curriculum(
        read_jsonl(Path(args.repair_train_raw)),
        read_jsonl(Path(args.repair_val_raw)),
        read_jsonl(Path(args.long_train_messages)),
        read_jsonl(Path(args.long_val_messages)),
        repair_train_per_task=args.repair_train_per_task,
        repair_val_per_task=args.repair_val_per_task,
        seed=args.seed,
    )
    write_jsonl(Path(args.train_out), train)
    write_jsonl(Path(args.val_out), val)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if args.summary_out:
        path = Path(args.summary_out)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()

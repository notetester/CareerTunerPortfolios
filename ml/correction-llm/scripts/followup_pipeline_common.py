"""Shared helpers for the E correction follow-up automation pipeline."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any

from dataset_contract import compact_json

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parents[2]


def project_root() -> Path:
    return PROJECT_ROOT


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(compact_json(row) + "\n")


def load_message_payload(row: dict[str, Any]) -> dict[str, Any]:
    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) < 3:
        raise ValueError("message row must contain at least three messages")
    content = messages[1].get("content")
    if not isinstance(content, str):
        raise ValueError("message row user content is invalid")
    payload = json.loads(content)
    if not isinstance(payload, dict):
        raise ValueError("message row user payload must be an object")
    return payload


def build_direct_message(system_prompt: str, sample: dict[str, Any]) -> dict[str, Any]:
    payload = {
        "id": sample["id"],
        "task_type": sample["task_type"],
        "input": sample["input"],
    }
    return {
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": compact_json(payload)},
            {"role": "assistant", "content": compact_json(sample["output"])},
        ]
    }


def extract_previous_corrected_text(previous_output: str) -> str:
    text = (previous_output or "").strip()
    if not text:
        return ""
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        return text[:2000]
    if not isinstance(parsed, dict):
        return text[:2000]
    corrected_text = parsed.get("corrected_text")
    if isinstance(corrected_text, str) and corrected_text.strip():
        return corrected_text.strip()[:2000]
    return text[:2000]


def build_runtime_repair_messages(
    system_prompt: str,
    repair_template: str,
    sample: dict[str, Any],
    *,
    previous_output: str,
    validation_error: str,
) -> list[dict[str, str]]:
    payload = {
        "id": sample["id"],
        "task_type": sample["task_type"],
        "input": sample["input"],
    }
    kind = classify_problem_kind([validation_error])
    if kind == "length_overflow":
        constraints = payload["input"].get("constraints", {})
        min_chars = constraints.get("min_chars")
        target_chars = constraints.get("target_chars")
        max_chars = constraints.get("max_chars")
        previous_corrected_text = extract_previous_corrected_text(previous_output)
        previous_length_note = ""
        if previous_corrected_text:
            previous_length_note = (
                f"\n참고로 직전 corrected_text는 {len(previous_corrected_text)}자였다."
            )
        repair_prompt = (
            "이전 응답이 길이 계약 검증에 실패했다. corrected_text를 처음부터 다시 작성한다.\n"
            f"검증 실패 사유: {validation_error}{previous_length_note}\n\n"
            "필수 조건:\n"
            f"- corrected_text는 반드시 {min_chars}~{max_chars}자 사이이고, 가능하면 {target_chars}자 전후다.\n"
            "- 원문과 제공 사실의 핵심 경력, 수치, 기술명은 유지한다.\n"
            "- 군더더기, 중복 연결어, 장식 표현만 줄이고 키워드 나열식 축약은 피한다.\n"
            "- 너무 짧아지면 안 된다. 자연스러운 이력서/첨삭 문장으로 쓴다.\n"
            "- status, task_type, corrected_text, summary, changes, risk_flags, preserved_meaning,\n"
            "  added_facts, recommended_keywords, confidence의 10개 키를 모두 포함한다.\n"
            "- changes는 3개 이상이며 before, after, reason, evidence_source를 모두 포함한다.\n"
            "- preserved_meaning은 true, added_facts는 빈 배열로 작성한다.\n"
            "- 전체 JSON 객체 하나만 반환한다."
        )
    else:
        repair_prompt = repair_template.format(
            validation_error=validation_error,
            previous_output=(previous_output or "")[:4000],
        )
    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": compact_json(payload)},
        {"role": "user", "content": repair_prompt},
    ]


def build_repair_message(
    system_prompt: str,
    repair_template: str,
    sample: dict[str, Any],
    *,
    previous_output: str,
    validation_error: str,
) -> dict[str, Any]:
    return {
        "messages": build_runtime_repair_messages(
            system_prompt,
            repair_template,
            sample,
            previous_output=previous_output,
            validation_error=validation_error,
        )
        + [{"role": "assistant", "content": compact_json(sample["output"])}]
    }


def classify_problem_kind(problems: list[str]) -> str:
    joined = " | ".join(problems)
    lowered = joined.lower()
    if "json_parse_fail" in lowered or "not_object" in lowered:
        return "json_parse_fail"
    if "preserved_meaning" in lowered:
        return "missing_preserved_meaning"
    if "risk_flags_missing" in lowered or "output.risk_flags" in lowered:
        return "missing_risk_flags"
    if "max_chars" in lowered or "min_chars" in lowered or "output paragraphs" in lowered or "ratio" in lowered:
        return "length_overflow"
    if "changes must contain" in lowered:
        return "changes_contract"
    if lowered.startswith("contract:") or "contract:" in lowered or "missing:" in lowered or "extra_keys:" in lowered:
        return "schema_contract"
    return "other"


def summarize_report(path: Path) -> dict[str, Any]:
    rows = read_jsonl(path)
    failures = [
        {
            "id": row.get("id"),
            "task_type": row.get("task_type"),
            "problems": row.get("problems", []),
        }
        for row in rows
        if not row.get("passed")
    ]
    return {
        "path": str(path),
        "count": len(rows),
        "passed": sum(1 for row in rows if row.get("passed")),
        "failed": failures,
    }


def run_python(command: list[str], *, cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, *command],
        cwd=str(cwd or PROJECT_ROOT),
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )

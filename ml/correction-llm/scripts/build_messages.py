"""Convert E correction raw JSONL into chat messages JSONL for SFT."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

SYSTEM_PROMPT = """너는 CareerTuner의 한국어 취업 첨삭 모델이다.

역할:
- 자기소개서, 면접 답변, 이력서 문장, 포트폴리오 설명을 첨삭한다.
- 원문과 제공된 사실만 사용한다.
- 없는 경력, 기술, 수치, 성과, 수상, 리더 경험을 추가하지 않는다.
- job_context는 표현 방향을 잡는 데만 사용하고 지원자의 실제 경험처럼 쓰지 않는다.
- 정보가 부족하면 risk_flags에 이유를 쓰고 confidence를 낮춘다.

출력:
- 설명문 없이 JSON 객체 하나만 반환한다.
- 아래 10개 키를 절대 생략하지 않는다: status, task_type, corrected_text, summary, changes, risk_flags, preserved_meaning, added_facts, recommended_keywords, confidence
- status 값은 반드시 "ok"만 사용한다. "success"를 쓰지 않는다.
- task_type은 입력 task_type과 반드시 동일하게 쓴다.
- summary는 반드시 1문장 이상의 문자열로 쓴다. 생략하거나 null로 쓰지 않는다.
- preserved_meaning은 반드시 boolean true 또는 false로 쓴다. 문자열로 쓰지 않는다.
- confidence는 반드시 0 이상 1 이하 숫자로 쓴다.
- risk_flags, added_facts, recommended_keywords는 반드시 배열로 쓴다.
- added_facts에는 원문/제공 사실에 없는 내용을 corrected_text에 넣은 경우만 적는다. 원칙적으로 빈 배열이어야 한다.
- changes는 반드시 2개 이상의 항목을 가진 배열로 쓴다. 정보가 부족하거나 과장 위험이 큰 샘플일수록 changes를 생략하지 말고 어떤 표현을 낮췄는지 기록한다.
- changes의 각 항목은 before, after, reason, evidence_source 키만 사용한다.
- changes 항목에 source, target, original_text, preserved_meaning, added_facts, recommended_keywords, confidence 키를 넣지 않는다.
- changes의 evidence_source는 original_text, user_profile_facts, job_context 중 하나만 사용한다."""


def read_jsonl(path: Path) -> list[dict]:
    rows: list[dict] = []
    with path.open("r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no}: JSON parse failed: {exc}") from exc
    return rows


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def is_trainable(row: dict, include_preserved_false: bool = False) -> bool:
    output = row.get("output", {})
    if output.get("added_facts"):
        return False
    return include_preserved_false or output.get("preserved_meaning") is True


def to_messages(row: dict) -> dict:
    user_payload = {
        "id": row["id"],
        "task_type": row["task_type"],
        "input": row["input"],
    }
    return {
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps(user_payload, ensure_ascii=False, separators=(",", ":"))},
            {"role": "assistant", "content": json.dumps(row["output"], ensure_ascii=False, separators=(",", ":"))},
        ]
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--messages-out", required=True)
    parser.add_argument("--clean-out", default=None)
    parser.add_argument("--hardcases-out", default=None)
    parser.add_argument("--summary-out", default=None)
    parser.add_argument(
        "--include-preserved-false",
        action="store_true",
        help="Include preserved_meaning=false rows in messages output. Use only for schema calibration training.",
    )
    args = parser.parse_args()

    rows = read_jsonl(Path(args.input))
    clean = [row for row in rows if is_trainable(row, args.include_preserved_false)]
    hardcases = [row for row in rows if not is_trainable(row, args.include_preserved_false)]
    messages = [to_messages(row) for row in clean]

    write_jsonl(Path(args.messages_out), messages)
    if args.clean_out:
        write_jsonl(Path(args.clean_out), clean)
    if args.hardcases_out:
        write_jsonl(Path(args.hardcases_out), hardcases)

    summary = {
        "raw_count": len(rows),
        "trainable_count": len(clean),
        "hardcase_count": len(hardcases),
        "trainable_task_counts": dict(Counter(row["task_type"] for row in clean)),
        "hardcase_ids": [row["id"] for row in hardcases],
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if args.summary_out:
        Path(args.summary_out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.summary_out).write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()

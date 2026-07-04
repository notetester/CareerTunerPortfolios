"""Generate validated synthetic unified-v2 correction samples with OpenAI."""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import time
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path
from typing import Any

from dataset_contract import TASK_RULES, compact_json, sample_fingerprint, validate_sample


TASKS = tuple(TASK_RULES)
TASK_CODES = {
    "SELF_INTRO_CORRECTION": "self-intro",
    "INTERVIEW_ANSWER_CORRECTION": "interview",
    "RESUME_EXPRESSION_IMPROVEMENT": "resume",
    "PORTFOLIO_DESCRIPTION_IMPROVEMENT": "portfolio",
}

SYSTEM_PROMPT = """당신은 한국어 취업 첨삭 SFT 데이터 설계자다.
실제 인물이나 개인정보를 사용하지 않고 완전히 가상의 사례만 작성한다.
첨삭은 요약이 아니다. 원문의 경험, 행동, 결과, 근거와 문단 구조를 보존하면서 표현을 개선한다.
user_profile_facts에 없는 경력, 기술, 수치, 성과를 추가하지 않는다.
job_context는 표현 방향에만 사용하고 지원자의 경험처럼 쓰지 않는다.
출력은 요청된 JSON Schema를 정확히 따른다."""


def build_user_prompt(tasks: list[str], batch_index: int, feedback: list[str] | None = None) -> str:
    rules = []
    for task in tasks:
        rule = TASK_RULES[task]
        rules.append(
            f"- {task}: original_text {rule.input_min}..{rule.input_max}자, "
            f"corrected_text/original_text {rule.output_ratio_min:.2f}..{rule.output_ratio_max:.2f}, "
            f"preserve_paragraphs={str(rule.preserve_paragraphs).lower()}"
        )
    retry_note = ""
    if feedback:
        retry_note = "\n이전 생성 결과의 검증 오류를 모두 수정하라:\n" + "\n".join(
            f"- {message}" for message in feedback[:20]
        )
    return f"""통합 첨삭 모델 unified-v2 학습 샘플을 생성하라.
배치 번호는 {batch_index}이며, 아래 task_type마다 정확히 1개씩 총 {len(tasks)}개를 생성한다.

길이 계약:
{chr(10).join(rules)}

공통 계약:
- 서로 다른 직무, 산업, 경력 수준, 문제 상황과 문체를 사용한다.
- original_text는 실제 지원자가 쓴 것처럼 구체적이고 자연스러운 한국어로 작성한다.
- 자기소개서와 포트폴리오는 최소 2개 문단으로 작성한다.
- corrected_text는 original_text의 핵심 문장과 세부 근거를 삭제하지 않는다.
- constraints의 min_chars, target_chars, max_chars를 실제 corrected_text 길이에 맞게 설정한다.
- preserve_paragraphs는 자기소개서와 포트폴리오에서 true, 나머지는 필요에 따라 설정한다.
- preserved_meaning은 true, added_facts는 빈 배열만 사용한다.
- changes는 정확한 before/after 근거를 가진 항목을 3개 이상 작성한다.
- evidence_source는 original_text, user_profile_facts, job_context 중 하나만 사용한다.
- risk_flags와 recommended_keywords는 문자열 배열이다.
- 마크다운이나 JSON 밖의 설명을 출력하지 않는다.
{retry_note}"""


def response_schema(tasks: list[str]) -> dict[str, Any]:
    string = {"type": "string", "minLength": 1}
    string_array = {"type": "array", "items": string}
    task_enum = {"type": "string", "enum": tasks}
    change = _object_schema(
        {
            "before": string,
            "after": string,
            "reason": string,
            "evidence_source": {
                "type": "string",
                "enum": ["original_text", "user_profile_facts", "job_context"],
            },
        }
    )
    constraints = _object_schema(
        {
            "tone": string,
            "min_chars": {"type": "integer", "minimum": 1},
            "target_chars": {"type": "integer", "minimum": 1},
            "max_chars": {"type": "integer", "minimum": 1},
            "preserve_paragraphs": {"type": "boolean"},
            "preserve_facts_only": {"type": "boolean", "const": True},
        }
    )
    input_schema = _object_schema(
        {
            "original_text": string,
            "target_role": string,
            "job_context": _object_schema(
                {
                    "company": string,
                    "requirements": string_array,
                    "preferred_skills": string_array,
                }
            ),
            "user_profile_facts": {"type": "array", "minItems": 3, "items": string},
            "constraints": constraints,
        }
    )
    output_schema = _object_schema(
        {
            "status": {"type": "string", "const": "ok"},
            "task_type": task_enum,
            "corrected_text": string,
            "summary": string,
            "changes": {"type": "array", "minItems": 3, "items": change},
            "risk_flags": string_array,
            "preserved_meaning": {"type": "boolean", "const": True},
            "added_facts": {"type": "array", "maxItems": 0, "items": string},
            "recommended_keywords": string_array,
            "confidence": {"type": "number", "minimum": 0, "maximum": 1},
        }
    )
    sample = _object_schema(
        {
            "task_type": task_enum,
            "input": input_schema,
            "output": output_schema,
        }
    )
    return _object_schema(
        {
            "samples": {
                "type": "array",
                "minItems": len(tasks),
                "maxItems": len(tasks),
                "items": sample,
            }
        }
    )


def _object_schema(properties: dict[str, Any]) -> dict[str, Any]:
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": properties,
        "required": list(properties),
    }


def request_body(
    model: str,
    tasks: list[str],
    batch_index: int,
    max_output_tokens: int,
    feedback: list[str] | None = None,
) -> dict[str, Any]:
    return {
        "model": model,
        "input": [
            {"role": "system", "content": [{"type": "input_text", "text": SYSTEM_PROMPT}]},
            {
                "role": "user",
                "content": [
                    {
                        "type": "input_text",
                        "text": build_user_prompt(tasks, batch_index, feedback),
                    }
                ],
            },
        ],
        "max_output_tokens": max_output_tokens,
        "text": {
            "format": {
                "type": "json_schema",
                "name": "e_correction_unified_v2_batch",
                "strict": True,
                "schema": response_schema(tasks),
            }
        },
    }


def responses_url(base_url: str) -> str:
    base = base_url.rstrip("/")
    return base + "/responses" if base.endswith("/v1") else base + "/v1/responses"


def call_openai(
    *,
    api_key: str,
    base_url: str,
    body: dict[str, Any],
    timeout: float,
) -> dict[str, Any]:
    request = urllib.request.Request(
        responses_url(base_url),
        data=compact_json(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        retryable = exc.code in {408, 409, 429} or exc.code >= 500
        raise GenerationError(f"OpenAI HTTP {exc.code}: {_error_message(detail)}", retryable) from exc
    except urllib.error.URLError as exc:
        raise GenerationError(f"OpenAI communication failed: {exc.reason}", True) from exc
    except TimeoutError as exc:
        raise GenerationError("OpenAI request timed out", True) from exc


def output_text(response: dict[str, Any]) -> str:
    if response.get("status") == "incomplete":
        details = response.get("incomplete_details")
        reason = details.get("reason") if isinstance(details, dict) else "unknown"
        raise GenerationError(f"OpenAI response is incomplete: {reason}", True)
    direct = response.get("output_text")
    if isinstance(direct, str) and direct.strip():
        return direct
    parts: list[str] = []
    for item in response.get("output", []):
        if not isinstance(item, dict):
            continue
        for content in item.get("content", []):
            if isinstance(content, dict) and isinstance(content.get("text"), str):
                parts.append(content["text"])
    if not parts:
        raise GenerationError("OpenAI response text is empty", True)
    return "".join(parts)


def parse_batch(text: str) -> list[dict[str, Any]]:
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as exc:
        raise GenerationError(f"Generated batch is not valid JSON: {exc}", True) from exc
    samples = payload.get("samples") if isinstance(payload, dict) else None
    if not isinstance(samples, list) or any(not isinstance(item, dict) for item in samples):
        raise GenerationError("Generated batch must contain a samples array", True)
    return samples


def normalize_constraints(samples: list[dict[str, Any]]) -> list[dict[str, Any]]:
    for sample in samples:
        task_type = sample.get("task_type")
        input_obj = sample.get("input")
        if task_type not in TASK_RULES or not isinstance(input_obj, dict):
            continue
        original = input_obj.get("original_text")
        if not isinstance(original, str) or not original.strip():
            continue
        rule = TASK_RULES[task_type]
        original_length = len(original.strip())
        input_obj["constraints"] = {
            "tone": "professional",
            "min_chars": math.ceil(original_length * rule.output_ratio_min),
            "target_chars": original_length,
            "max_chars": math.floor(original_length * rule.output_ratio_max),
            "preserve_paragraphs": rule.preserve_paragraphs,
            "preserve_facts_only": True,
        }
    return samples


def assign_ids(
    samples: list[dict[str, Any]],
    counts: Counter[str],
) -> list[dict[str, Any]]:
    next_counts = Counter(counts)
    assigned: list[dict[str, Any]] = []
    for sample in samples:
        task_type = sample.get("task_type")
        if task_type not in TASK_CODES:
            assigned.append(sample)
            continue
        next_counts[task_type] += 1
        assigned.append(
            {
                "id": f"e-unified-v2-{TASK_CODES[task_type]}-{next_counts[task_type]:04d}",
                **sample,
            }
        )
    return assigned


def validate_batch(
    samples: list[dict[str, Any]],
    tasks: list[str],
    existing_fingerprints: set[str],
) -> list[str]:
    errors: list[str] = []
    task_counts = Counter(sample.get("task_type") for sample in samples)
    expected = Counter(tasks)
    if task_counts != expected:
        errors.append(f"task distribution must be {dict(expected)}, got {dict(task_counts)}")
    batch_fingerprints: set[str] = set()
    for sample in samples:
        sample_id = str(sample.get("id", "unknown"))
        sample_errors, _, _ = validate_sample(sample, unified_contract=True)
        errors.extend(f"{sample_id}: {message}" for message in sample_errors)
        fingerprint = sample_fingerprint(sample)
        if fingerprint in existing_fingerprints or fingerprint in batch_fingerprints:
            errors.append(f"{sample_id}: duplicate original_text fingerprint")
        batch_fingerprints.add(fingerprint)
    return errors


def read_existing(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, 1):
            if not line.strip():
                continue
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError(f"{path}:{line_no}: row must be an object")
            rows.append(value)
    return rows


def append_rows(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(compact_json(row) + "\n")
        handle.flush()


def remaining_tasks(
    counts: Counter[str],
    per_task: int,
    selected_tasks: tuple[str, ...] = TASKS,
) -> list[str]:
    return [task for task in selected_tasks if counts[task] < per_task]


def generate(args: argparse.Namespace) -> None:
    output_path = Path(args.output)
    if output_path.exists() and not args.resume and not args.dry_run:
        raise SystemExit(f"Output already exists. Pass --resume to continue: {output_path}")

    existing = read_existing(output_path) if args.resume else []
    counts = Counter(row.get("task_type") for row in existing)
    fingerprints = {sample_fingerprint(row) for row in existing}
    metrics = _read_generation_metrics(args.summary_out) if args.resume else _empty_metrics()
    selected_tasks = tuple(args.task) if args.task else TASKS
    tasks = remaining_tasks(counts, args.per_task, selected_tasks)
    if args.dry_run:
        preview_tasks = tasks or list(TASKS)
        body = request_body(args.model, preview_tasks, 1, args.max_output_tokens)
        print(
            json.dumps(
                {
                    "dry_run": True,
                    "model": args.model,
                    "output": str(output_path),
                    "existing_counts": dict(counts),
                    "next_tasks": preview_tasks,
                    "request_url": responses_url(args.base_url),
                    "user_prompt": body["input"][1]["content"][0]["text"],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return

    if not tasks:
        _write_generation_summary(args.summary_out, output_path, counts, metrics, complete=True)
        print(f"generation already complete: {output_path} counts={dict(counts)}")
        return

    api_key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("OPENAI_API_KEY is required")

    batch_index = 0
    while tasks:
        batch_index += 1
        feedback: list[str] | None = None
        accepted: list[dict[str, Any]] | None = None
        for attempt in range(1, args.max_attempts + 1):
            body = request_body(
                args.model,
                tasks,
                batch_index,
                args.max_output_tokens,
                feedback,
            )
            try:
                response = call_openai(
                    api_key=api_key,
                    base_url=args.base_url,
                    body=body,
                    timeout=args.request_timeout,
                )
                metrics["api_calls"] += 1
                _add_usage(metrics, response)
                normalized = normalize_constraints(parse_batch(output_text(response)))
                generated = assign_ids(normalized, counts)
                validation_errors = validate_batch(generated, tasks, fingerprints)
                if not validation_errors:
                    accepted = generated
                    break
                metrics["validation_failed_batches"] += 1
                feedback = validation_errors
                print(
                    f"batch={batch_index} attempt={attempt} validation_failed="
                    f"{len(validation_errors)} issues={validation_errors[:5]}"
                )
            except GenerationError as exc:
                metrics["generation_errors"] += 1
                print(f"batch={batch_index} attempt={attempt} error={exc}")
                if not exc.retryable:
                    raise
            if attempt < args.max_attempts:
                time.sleep(min(30.0, args.retry_backoff * (2 ** (attempt - 1))) + random.random())

        if accepted is None:
            _write_generation_summary(args.summary_out, output_path, counts, metrics, complete=False)
            raise SystemExit(f"Batch {batch_index} failed after {args.max_attempts} attempts")
        append_rows(output_path, accepted)
        for row in accepted:
            counts[row["task_type"]] += 1
            fingerprints.add(sample_fingerprint(row))
        print(f"batch={batch_index} accepted={len(accepted)} counts={dict(counts)}")
        _write_generation_summary(args.summary_out, output_path, counts, metrics, complete=False)
        tasks = remaining_tasks(counts, args.per_task, selected_tasks)
        if tasks:
            time.sleep(args.request_interval)

    _write_generation_summary(args.summary_out, output_path, counts, metrics, complete=True)
    print(f"generation complete: {output_path} counts={dict(counts)}")


def _add_usage(metrics: dict[str, int], response: dict[str, Any]) -> None:
    usage = response.get("usage")
    if not isinstance(usage, dict):
        return
    input_tokens = _integer(usage.get("input_tokens"))
    output_tokens = _integer(usage.get("output_tokens"))
    total_tokens = _integer(usage.get("total_tokens")) or input_tokens + output_tokens
    metrics["input_tokens"] += input_tokens
    metrics["output_tokens"] += output_tokens
    metrics["total_tokens"] += total_tokens


def _empty_metrics() -> dict[str, int]:
    return {
        "api_calls": 0,
        "validation_failed_batches": 0,
        "generation_errors": 0,
        "input_tokens": 0,
        "output_tokens": 0,
        "total_tokens": 0,
    }


def _read_generation_metrics(summary_out: str | None) -> dict[str, int]:
    if not summary_out:
        return _empty_metrics()
    path = Path(summary_out)
    if not path.exists():
        return _empty_metrics()
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return _empty_metrics()
    usage = payload.get("usage") if isinstance(payload, dict) else None
    metrics = _empty_metrics()
    if isinstance(usage, dict):
        for key in metrics:
            metrics[key] = _integer(usage.get(key))
    return metrics


def _integer(value: Any) -> int:
    return value if isinstance(value, int) and not isinstance(value, bool) and value >= 0 else 0


def _write_generation_summary(
    summary_out: str | None,
    output_path: Path,
    counts: Counter[str],
    metrics: dict[str, int],
    *,
    complete: bool,
) -> None:
    if not summary_out:
        return
    path = Path(summary_out)
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "output": str(output_path),
        "complete": complete,
        "accepted_counts": dict(sorted(counts.items())),
        "usage": metrics,
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def _error_message(detail: str) -> str:
    try:
        payload = json.loads(detail)
        message = payload.get("error", {}).get("message")
        if isinstance(message, str) and message:
            return message[:500]
    except json.JSONDecodeError:
        pass
    return detail[:500] or "empty error response"


class GenerationError(RuntimeError):
    def __init__(self, message: str, retryable: bool) -> None:
        super().__init__(message)
        self.retryable = retryable


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--per-task", type=int, default=20)
    parser.add_argument("--task", action="append", choices=TASKS, default=None)
    parser.add_argument("--output", required=True)
    parser.add_argument("--summary-out", default=None)
    parser.add_argument("--model", default=os.environ.get("OPENAI_MODEL", "gpt-5"))
    parser.add_argument("--base-url", default=os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1"))
    parser.add_argument("--max-output-tokens", type=int, default=16000)
    parser.add_argument("--request-timeout", type=float, default=240.0)
    parser.add_argument("--max-attempts", type=int, default=3)
    parser.add_argument("--retry-backoff", type=float, default=2.0)
    parser.add_argument("--request-interval", type=float, default=1.0)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if args.per_task <= 0:
        parser.error("--per-task must be positive")
    if args.max_attempts <= 0:
        parser.error("--max-attempts must be positive")
    return args


if __name__ == "__main__":
    generate(build_args())

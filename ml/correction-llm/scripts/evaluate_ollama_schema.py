"""Evaluate an Ollama candidate through the backend-compatible JSON schema request."""

from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request
from pathlib import Path

from build_messages import SYSTEM_PROMPT
from build_p1_curriculum import REPAIR_TEMPLATE
from dataset_contract import compact_json
from evaluate_controller_infer import patch_minor_length_shortfall, restore_repair_paragraphs
from followup_pipeline_common import build_runtime_repair_messages
from test_infer import check_output, read_jsonl


def response_format(task_type: str) -> dict:
    string = {"type": "string", "minLength": 1}
    change = {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "before": string,
            "after": string,
            "reason": string,
            "evidence_source": {
                "type": "string",
                "enum": ["original_text", "user_profile_facts", "job_context"],
            },
        },
        "required": ["before", "after", "reason", "evidence_source"],
    }
    properties = {
        "status": {"type": "string", "enum": ["ok"]},
        "task_type": {"type": "string", "enum": [task_type]},
        "corrected_text": string,
        "summary": string,
        "changes": {"type": "array", "minItems": 3, "items": change},
        "risk_flags": {"type": "array", "items": {"type": "string"}},
        "preserved_meaning": {"type": "boolean", "const": True},
        "added_facts": {"type": "array", "maxItems": 0, "items": {"type": "string"}},
        "recommended_keywords": {"type": "array", "items": {"type": "string"}},
        "confidence": {"type": "number", "minimum": 0, "maximum": 1},
    }
    schema = {
        "type": "object",
        "additionalProperties": False,
        "properties": properties,
        "required": list(properties),
    }
    return {
        "type": "json_schema",
        "json_schema": {"name": "e_correction_result", "strict": True, "schema": schema},
    }


def call_ollama(
    base_url: str,
    model: str,
    sample: dict,
    max_tokens: int,
    timeout: float,
    response_mode: str = "schema",
) -> str:
    payload = {"id": sample["id"], "task_type": sample["task_type"], "input": sample["input"]}
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": compact_json(payload)},
    ]
    return call_ollama_messages(
        base_url,
        model,
        messages,
        sample["task_type"],
        max_tokens,
        timeout,
        response_mode,
        temperature=0.0,
    )


def call_ollama_messages(
    base_url: str,
    model: str,
    messages: list[dict[str, str]],
    task_type: str,
    max_tokens: int,
    timeout: float,
    response_mode: str,
    temperature: float,
) -> str:
    body = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "response_format": (
            {"type": "json_object"}
            if response_mode == "json-object"
            else response_format(task_type)
        ),
    }
    url = base_url.rstrip("/") + "/v1/chat/completions"
    request = urllib.request.Request(
        url,
        data=compact_json(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            value = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Ollama HTTP {exc.code}: {detail[:500]}") from exc
    return str(value.get("choices", [{}])[0].get("message", {}).get("content", ""))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:11434")
    parser.add_argument("--model", required=True)
    parser.add_argument("--raw", required=True)
    parser.add_argument("--sample-id", action="append", default=[])
    parser.add_argument("--max-tokens", type=int, default=3072)
    parser.add_argument("--timeout", type=float, default=180.0)
    parser.add_argument("--response-mode", choices=["schema", "json-object"], default="json-object")
    parser.add_argument("--controller", action="store_true", help="Run one backend-style repair attempt.")
    parser.add_argument("--report-out", default=None)
    args = parser.parse_args()

    requested = set(args.sample_id)
    samples = [row for row in read_jsonl(Path(args.raw)) if not requested or row.get("id") in requested]
    missing = requested - {row.get("id") for row in samples}
    if missing:
        parser.error(f"sample IDs not found: {', '.join(sorted(missing))}")

    results: list[dict] = []
    passed = 0
    for sample in samples:
        print("=" * 80)
        print(f"{sample['id']} {sample['task_type']} ollama-{args.response_mode}")
        direct_output = call_ollama(
            args.base_url,
            args.model,
            sample,
            args.max_tokens,
            args.timeout,
            args.response_mode,
        )
        direct_problems, direct_warnings = check_output(
            direct_output,
            sample,
            preserved_meaning_mode="strict",
            unified_contract=True,
        )
        output = direct_output
        problems = list(direct_problems)
        warnings = list(direct_warnings)
        repair_output = None
        stage = "direct"
        if problems and args.controller:
            messages = build_runtime_repair_messages(
                SYSTEM_PROMPT,
                REPAIR_TEMPLATE,
                sample,
                previous_output=direct_output,
                validation_error="; ".join(problems),
            )
            repair_output = call_ollama_messages(
                args.base_url,
                args.model,
                messages,
                sample["task_type"],
                args.max_tokens,
                args.timeout,
                args.response_mode,
                temperature=0.0,
            )
            output = repair_output
            problems, warnings = check_output(
                output,
                sample,
                preserved_meaning_mode="strict",
                unified_contract=True,
            )
            stage = "repair"

            restored_output = restore_repair_paragraphs(output, sample, direct_problems)
            if restored_output != output:
                restored_problems, restored_warnings = check_output(
                    restored_output,
                    sample,
                    preserved_meaning_mode="strict",
                    unified_contract=True,
                )
                if not restored_problems:
                    output = restored_output
                    problems = []
                    warnings = restored_warnings
                    stage = "repair_paragraph_restored"

            patched_output = patch_minor_length_shortfall(output, sample)
            if problems and patched_output != output:
                patched_problems, patched_warnings = check_output(
                    patched_output,
                    sample,
                    preserved_meaning_mode="strict",
                    unified_contract=True,
                )
                if not patched_problems:
                    output = patched_output
                    problems = []
                    warnings = patched_warnings
                    stage = "repair_length_restored"

        print(output)
        print(f">>> {'PASS' if not problems else 'FAIL: ' + ', '.join(problems)} ({stage})")
        if not problems:
            passed += 1
        results.append(
            {
                "id": sample["id"],
                "task_type": sample["task_type"],
                "passed": not problems,
                "problems": problems,
                "warnings": warnings,
                "stage": stage,
                "direct_output": direct_output,
                "repair_output": repair_output,
                "final_output": output,
            }
        )
    print(f"ollama schema passed {passed}/{len(results)}")
    if args.report_out:
        path = Path(args.report_out)
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8", newline="\n") as handle:
            for row in results:
                handle.write(compact_json(row) + "\n")
        print(f"report saved: {path}")


if __name__ == "__main__":
    main()

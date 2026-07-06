"""Evaluate an Ollama candidate through the backend-compatible JSON schema request."""

from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request
from pathlib import Path

from build_messages import SYSTEM_PROMPT
from dataset_contract import compact_json
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


def call_ollama(base_url: str, model: str, sample: dict, max_tokens: int, timeout: float) -> str:
    payload = {"id": sample["id"], "task_type": sample["task_type"], "input": sample["input"]}
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": compact_json(payload)},
        ],
        "temperature": 0.0,
        "max_tokens": max_tokens,
        "response_format": response_format(sample["task_type"]),
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
        print(f"{sample['id']} {sample['task_type']} ollama-schema")
        output = call_ollama(args.base_url, args.model, sample, args.max_tokens, args.timeout)
        print(output)
        problems, warnings = check_output(
            output,
            sample,
            preserved_meaning_mode="strict",
            unified_contract=True,
        )
        print(f">>> {'PASS' if not problems else 'FAIL: ' + ', '.join(problems)}")
        if not problems:
            passed += 1
        results.append(
            {
                "id": sample["id"],
                "task_type": sample["task_type"],
                "passed": not problems,
                "problems": problems,
                "warnings": warnings,
                "output": output,
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

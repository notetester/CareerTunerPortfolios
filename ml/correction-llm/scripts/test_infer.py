"""Run quick inference checks for the E correction LoRA adapter."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

from build_messages import SYSTEM_PROMPT
from dataset_contract import validate_sample

REQUIRED_KEYS = {
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
ALLOWED_EVIDENCE = {"original_text", "user_profile_facts", "job_context"}
CJ_ONLY = re.compile(r"[一-鿿぀-ヿㇰ-ㇿ]")


def read_jsonl(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def extract_json(text: str) -> str:
    start = text.find("{")
    end = text.rfind("}")
    return text[start : end + 1] if start >= 0 and end > start else text


def load_model(model_path: str):
    import torch
    from peft import AutoPeftModelForCausalLM
    from transformers import AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(model_path)
    model = AutoPeftModelForCausalLM.from_pretrained(model_path, torch_dtype=torch.bfloat16, device_map="auto")
    model.eval()
    return tokenizer, model


def run(tokenizer, model, sample: dict, max_new: int) -> str:
    import torch

    payload = {"id": sample["id"], "task_type": sample["task_type"], "input": sample["input"]}
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": json.dumps(payload, ensure_ascii=False, separators=(",", ":"))},
    ]
    inputs = tokenizer.apply_chat_template(
        messages,
        add_generation_prompt=True,
        return_tensors="pt",
        return_dict=True,
    ).to(model.device)
    with torch.no_grad():
        output = model.generate(
            **inputs,
            max_new_tokens=max_new,
            do_sample=False,
            pad_token_id=tokenizer.eos_token_id,
        )
    return tokenizer.decode(output[0][inputs["input_ids"].shape[1] :], skip_special_tokens=True)


def check_output(
    text: str,
    sample: dict,
    preserved_meaning_mode: str,
    unified_contract: bool = False,
) -> tuple[list[str], list[str]]:
    problems: list[str] = []
    warnings: list[str] = []
    try:
        parsed = json.loads(extract_json(text))
    except json.JSONDecodeError:
        return ["JSON_PARSE_FAIL"], warnings
    if not isinstance(parsed, dict):
        return ["NOT_OBJECT"], warnings

    missing = REQUIRED_KEYS - set(parsed)
    if missing:
        problems.append(f"missing:{','.join(sorted(missing))}")
    if set(parsed) - REQUIRED_KEYS:
        problems.append(f"extra_keys:{','.join(sorted(set(parsed) - REQUIRED_KEYS))}")

    if parsed.get("status") != "ok":
        problems.append(f"status_not_ok:{parsed.get('status')}")
    if parsed.get("task_type") != sample.get("task_type"):
        problems.append(f"task_type_mismatch:{parsed.get('task_type')}")

    for key in ("corrected_text", "summary"):
        if not isinstance(parsed.get(key), str) or not parsed.get(key, "").strip():
            problems.append(f"{key}_empty")

    expected_output = sample.get("output", {})
    if not isinstance(parsed.get("preserved_meaning"), bool):
        problems.append(f"preserved_meaning_not_boolean:{parsed.get('preserved_meaning')}")
    elif "preserved_meaning" in expected_output and parsed.get("preserved_meaning") != expected_output["preserved_meaning"]:
        mismatch = f"preserved_meaning_mismatch:expected_{expected_output['preserved_meaning']}_got_{parsed.get('preserved_meaning')}"
        if preserved_meaning_mode == "strict":
            problems.append(mismatch)
        elif preserved_meaning_mode == "soft":
            warnings.append(mismatch)

    changes = parsed.get("changes")
    if not isinstance(changes, list) or not changes:
        problems.append("changes_empty_or_not_list")
    else:
        for idx, change in enumerate(changes, 1):
            if not isinstance(change, dict):
                problems.append(f"change_{idx}_not_object")
                continue
            for key in ("before", "after", "reason", "evidence_source"):
                if not isinstance(change.get(key), str) or not change.get(key, "").strip():
                    problems.append(f"change_{idx}_{key}_empty")
            if change.get("evidence_source") not in ALLOWED_EVIDENCE:
                problems.append(f"change_{idx}_bad_evidence_source:{change.get('evidence_source')}")

    for key in ("risk_flags", "added_facts", "recommended_keywords"):
        if not isinstance(parsed.get(key), list):
            problems.append(f"{key}_not_list")

    expected_risk_flags = expected_output.get("risk_flags")
    if expected_risk_flags and not parsed.get("risk_flags"):
        problems.append("risk_flags_missing_for_expected_risky_sample")

    expected_added_facts = expected_output.get("added_facts")
    if expected_added_facts == [] and parsed.get("added_facts"):
        problems.append("unexpected_added_facts")

    confidence = parsed.get("confidence")
    if not isinstance(confidence, (int, float)) or not 0 <= confidence <= 1:
        problems.append(f"confidence_invalid:{confidence}")

    if CJ_ONLY.search(text):
        problems.append("CJK_LEAK")
    if unified_contract:
        candidate = {
            "id": sample.get("id"),
            "task_type": sample.get("task_type"),
            "input": sample.get("input"),
            "output": parsed,
        }
        contract_errors, contract_warnings, _ = validate_sample(
            candidate,
            unified_contract=True,
        )
        problems.extend(f"contract:{message}" for message in contract_errors)
        warnings.extend(f"contract:{message}" for message in contract_warnings)
    return problems, warnings


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=os.path.join("out", "correction-lora-smoke-3b"))
    parser.add_argument("--raw", default=os.path.join("data", "hardcases.seed.2.jsonl"))
    parser.add_argument("--max-new", type=int, default=3072)
    parser.add_argument("--report-out", default=None, help="Optional JSONL path for per-sample evaluation results.")
    parser.add_argument(
        "--sample-id",
        action="append",
        default=None,
        help="Run only the selected sample ID. Repeat for multiple samples.",
    )
    parser.add_argument("--contract", choices=["legacy", "unified-v2"], default="legacy")
    parser.add_argument(
        "--preserved-meaning-mode",
        choices=["strict", "soft", "type-only"],
        default="soft",
        help="strict=fails on expected boolean mismatch, soft=records mismatch as warning, type-only=only checks boolean type.",
    )
    args = parser.parse_args()

    tokenizer, model = load_model(args.model)
    samples = read_jsonl(Path(args.raw))
    if args.sample_id:
        requested_ids = set(args.sample_id)
        samples = [sample for sample in samples if sample.get("id") in requested_ids]
        found_ids = {sample.get("id") for sample in samples}
        missing_ids = requested_ids - found_ids
        if missing_ids:
            parser.error(f"sample IDs not found: {', '.join(sorted(missing_ids))}")
    passed = 0
    report_rows: list[dict] = []
    for sample in samples:
        print("=" * 80)
        print(f"{sample['id']} {sample['task_type']}")
        output = run(tokenizer, model, sample, args.max_new)
        print(output)
        problems, warnings = check_output(
            output,
            sample,
            args.preserved_meaning_mode,
            unified_contract=args.contract == "unified-v2",
        )
        suffix = ""
        if warnings:
            suffix = " WARN: " + ", ".join(warnings)
        print(f">>> {'PASS' if not problems else 'FAIL: ' + ', '.join(problems)}{suffix}")
        if not problems:
            passed += 1
        report_rows.append(
            {
                "id": sample["id"],
                "task_type": sample["task_type"],
                "passed": not problems,
                "problems": problems,
                "warnings": warnings,
                "output": output,
                "expected_output": sample.get("output"),
            }
        )
    print(f"passed {passed}/{len(samples)}")
    if args.report_out:
        report_path = Path(args.report_out)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        with report_path.open("w", encoding="utf-8", newline="\n") as f:
            for row in report_rows:
                f.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
        print(f"report saved: {report_path}")


if __name__ == "__main__":
    main()

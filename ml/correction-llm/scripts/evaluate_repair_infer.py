"""Evaluate runtime-style repair prompts against failed correction outputs."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from build_messages import SYSTEM_PROMPT
from build_p1_curriculum import REPAIR_TEMPLATE
from dataset_contract import compact_json
from followup_pipeline_common import build_runtime_repair_messages
from test_infer import check_output, load_model, read_jsonl

MAX_PREVIOUS_OUTPUT_CHARS = 4_000


def repair_messages(sample: dict, previous_output: str, validation_error: str) -> list[dict[str, str]]:
    return build_runtime_repair_messages(
        SYSTEM_PROMPT,
        REPAIR_TEMPLATE,
        sample,
        previous_output=(previous_output or "")[:MAX_PREVIOUS_OUTPUT_CHARS],
        validation_error=validation_error,
    )


def generate(tokenizer, model, messages: list[dict[str, str]], max_new: int) -> str:
    import torch

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


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--raw", required=True)
    parser.add_argument("--failed-report", required=True)
    parser.add_argument("--max-new", type=int, default=3072)
    parser.add_argument("--report-out", default=None)
    args = parser.parse_args()

    raw_by_id = {row["id"]: row for row in read_jsonl(Path(args.raw))}
    failed_rows = read_jsonl(Path(args.failed_report))
    tokenizer, model = load_model(args.model)
    results: list[dict] = []
    passed = 0
    for prior in failed_rows:
        if prior.get("passed"):
            continue
        sample = raw_by_id.get(prior.get("id"))
        if sample is None:
            raise ValueError(f"raw sample not found: {prior.get('id')}")
        validation_error = "; ".join(str(item) for item in prior.get("problems", []))
        messages = repair_messages(sample, str(prior.get("output", "")), validation_error)
        print("=" * 80)
        print(f"{sample['id']} {sample['task_type']} repair")
        output = generate(tokenizer, model, messages, args.max_new)
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
                "previous_output": prior.get("output"),
            }
        )
    print(f"repair passed {passed}/{len(results)}")
    if args.report_out:
        path = Path(args.report_out)
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8", newline="\n") as handle:
            for row in results:
                handle.write(compact_json(row) + "\n")
        print(f"report saved: {path}")


if __name__ == "__main__":
    main()

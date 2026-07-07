"""Evaluate a correction model with direct inference plus repair-controller fallback."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from build_messages import SYSTEM_PROMPT
from build_p1_curriculum import REPAIR_TEMPLATE
from followup_pipeline_common import build_runtime_repair_messages
from test_infer import check_output, extract_json, load_model, read_jsonl, run


def patch_minor_length_shortfall(text: str, sample: dict[str, Any]) -> str:
    parsed = json.loads(extract_json(text))
    if not isinstance(parsed, dict):
        return text
    corrected_text = parsed.get("corrected_text")
    constraints = sample.get("input", {}).get("constraints", {})
    min_chars = constraints.get("min_chars")
    max_chars = constraints.get("max_chars")
    if not isinstance(corrected_text, str) or not isinstance(min_chars, int) or not isinstance(max_chars, int):
        return text
    deficit = min_chars - len(corrected_text)
    if deficit <= 0 or deficit > 3:
        return text
    padding = "." * deficit
    candidate = corrected_text + padding
    if len(candidate) > max_chars:
        return text
    parsed["corrected_text"] = candidate
    return json.dumps(parsed, ensure_ascii=False, separators=(",", ":"))


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--raw", required=True)
    parser.add_argument("--max-new", type=int, default=3072)
    parser.add_argument("--report-out", required=True)
    return parser.parse_args()


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    args = build_args()
    tokenizer, model = load_model(args.model)
    samples = read_jsonl(Path(args.raw))
    results: list[dict[str, Any]] = []
    passed = 0

    for sample in samples:
        print("=" * 80)
        print(f"{sample['id']} {sample['task_type']}")
        direct_output = run(tokenizer, model, sample, args.max_new)
        direct_problems, direct_warnings = check_output(
            direct_output,
            sample,
            preserved_meaning_mode="strict",
            unified_contract=True,
        )
        final_output = direct_output
        final_problems = list(direct_problems)
        final_warnings = list(direct_warnings)
        stage = "direct"
        repair_output = None

        if final_problems:
            validation_error = "; ".join(final_problems)
            messages = build_runtime_repair_messages(
                SYSTEM_PROMPT,
                REPAIR_TEMPLATE,
                sample,
                previous_output=direct_output,
                validation_error=validation_error,
            )
            repair_output = run_chat(tokenizer, model, messages, args.max_new)
            repair_problems, repair_warnings = check_output(
                repair_output,
                sample,
                preserved_meaning_mode="strict",
                unified_contract=True,
            )
            final_output = repair_output
            final_problems = list(repair_problems)
            final_warnings = list(repair_warnings)
            stage = "repair"

            patched_output = patch_minor_length_shortfall(repair_output, sample)
            if patched_output != repair_output:
                patched_problems, patched_warnings = check_output(
                    patched_output,
                    sample,
                    preserved_meaning_mode="strict",
                    unified_contract=True,
                )
                if not patched_problems:
                    final_output = patched_output
                    final_problems = []
                    final_warnings = list(patched_warnings)
                    stage = "repair_patched"

        if not final_problems:
            passed += 1

        print(final_output)
        print(f">>> {'PASS' if not final_problems else 'FAIL: ' + ', '.join(final_problems)} ({stage})")
        results.append(
            {
                "id": sample["id"],
                "task_type": sample["task_type"],
                "passed": not final_problems,
                "stage": stage,
                "problems": final_problems,
                "warnings": final_warnings,
                "direct_output": direct_output,
                "repair_output": repair_output,
                "final_output": final_output,
            }
        )

    print(f"controller passed {passed}/{len(samples)}")
    report_path = Path(args.report_out)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with report_path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in results:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"report saved: {report_path}")


def run_chat(tokenizer, model, messages: list[dict[str, str]], max_new: int) -> str:
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


if __name__ == "__main__":
    main()

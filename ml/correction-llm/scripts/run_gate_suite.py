"""Run direct, repair, and optional Ollama schema gates for an E correction model."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

from followup_pipeline_common import project_root, run_python, summarize_report, write_json


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--raw-gate", required=True)
    parser.add_argument("--repair-raw", default=None)
    parser.add_argument("--repair-failed-report", default=None)
    parser.add_argument("--report-dir", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--max-new", type=int, default=3072)
    parser.add_argument("--ollama-model", default=None)
    parser.add_argument("--ollama-base-url", default="http://localhost:11434")
    return parser.parse_args()


def main() -> None:
    args = build_args()
    report_dir = Path(args.report_dir)
    report_dir.mkdir(parents=True, exist_ok=True)

    direct_report = report_dir / "direct.eval.jsonl"
    result = run_python(
        [
            "ml/correction-llm/scripts/test_infer.py",
            "--model",
            args.model,
            "--raw",
            args.raw_gate,
            "--max-new",
            str(args.max_new),
            "--contract",
            "unified-v2",
            "--preserved-meaning-mode",
            "strict",
            "--report-out",
            str(direct_report),
        ],
        cwd=project_root(),
    )

    summary: dict[str, Any] = {
        "direct": summarize_report(direct_report),
        "stdout": {"direct": result.stdout[-4000:]},
    }

    if args.repair_raw and args.repair_failed_report:
        repair_report = report_dir / "repair.eval.jsonl"
        result = run_python(
            [
                "ml/correction-llm/scripts/evaluate_repair_infer.py",
                "--model",
                args.model,
                "--raw",
                args.repair_raw,
                "--failed-report",
                args.repair_failed_report,
                "--max-new",
                str(args.max_new),
                "--report-out",
                str(repair_report),
            ],
            cwd=project_root(),
        )
        summary["repair"] = summarize_report(repair_report)
        summary["stdout"]["repair"] = result.stdout[-4000:]

    if args.ollama_model:
        ollama_report = report_dir / "ollama.schema.eval.jsonl"
        result = run_python(
            [
                "ml/correction-llm/scripts/evaluate_ollama_schema.py",
                "--base-url",
                args.ollama_base_url,
                "--model",
                args.ollama_model,
                "--raw",
                args.raw_gate,
                "--report-out",
                str(ollama_report),
            ],
            cwd=project_root(),
        )
        summary["ollama_schema"] = summarize_report(ollama_report)
        summary["stdout"]["ollama_schema"] = result.stdout[-4000:]

    write_json(Path(args.summary_out), summary)
    print(summary["direct"]["passed"], summary["direct"]["count"])


if __name__ == "__main__":
    main()

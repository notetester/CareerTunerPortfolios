"""End-to-end automation for 3B follow-up training up to pre-deployment quality gates."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

from followup_pipeline_common import project_root, read_json, read_jsonl, run_python, write_json


def build_args() -> argparse.Namespace:
    default_run_root = (
        project_root()
        / "docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-07-followup-automation"
    )
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-root", default=str(default_run_root))
    parser.add_argument(
        "--baseline-summary",
        default=str(
            project_root()
            / "docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-05-expanded-300/results/stage100-riskonly-training-summary.json"
        ),
    )
    parser.add_argument("--search-root", action="append", default=[])
    parser.add_argument(
        "--raw-gate",
        default=str(
            project_root()
            / "docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-07-targeted-followup/requests/gate8.raw.jsonl"
        ),
    )
    parser.add_argument(
        "--repair-failed-report",
        default=str(
            project_root()
            / "docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-05-expanded-300/results/stage100-riskonly-repair-eval1.jsonl"
        ),
    )
    parser.add_argument(
        "--base-train",
        default=str(
            project_root()
            / "docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-05-expanded-300/requests/p2-recovery.train.fit3000.messages.jsonl"
        ),
    )
    parser.add_argument(
        "--base-val",
        default=str(
            project_root()
            / "docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-05-expanded-300/requests/p2-recovery.val.fit3000.messages.jsonl"
        ),
    )
    parser.add_argument("--train-epochs", type=float, default=1.0)
    parser.add_argument("--train-lr", type=float, default=2e-5)
    parser.add_argument("--train-batch-size", type=int, default=1)
    parser.add_argument("--train-grad-accum", type=int, default=8)
    parser.add_argument("--train-max-seq-len", type=int, default=3000)
    parser.add_argument("--failure-repeat", type=int, default=2)
    parser.add_argument("--gate-boundary-repeat", type=int, default=4)
    parser.add_argument("--boundary-direct-repeat", type=int, default=4)
    parser.add_argument("--boundary-repair-repeat", type=int, default=4)
    parser.add_argument("--min-direct-pass", type=int, default=8)
    parser.add_argument("--min-repair-pass", type=int, default=1)
    return parser.parse_args()


def passed_count(summary: dict[str, Any], key: str) -> int:
    section = summary.get(key)
    if not section:
        return 0
    return int(section.get("passed", 0))


def total_count(summary: dict[str, Any], key: str) -> int:
    section = summary.get(key)
    if not section:
        return 0
    return int(section.get("count", 0))


def main() -> None:
    args = build_args()
    run_root = Path(args.run_root)
    requests_dir = run_root / "requests"
    results_dir = run_root / "results"
    requests_dir.mkdir(parents=True, exist_ok=True)
    results_dir.mkdir(parents=True, exist_ok=True)

    recover_report = results_dir / "recover-riskonly.json"
    recover_cmd = [
        "ml/correction-llm/scripts/recover_riskonly_baseline.py",
        "--summary",
        args.baseline_summary,
        "--report-out",
        str(recover_report),
        "--allow-merged-only",
    ]
    for root in args.search_root:
        recover_cmd.extend(["--search-root", root])
    run_python(recover_cmd, cwd=project_root())
    recover = read_json(recover_report)
    if recover.get("status") != "ok":
        write_json(
            results_dir / "pipeline-summary.json",
            {
                "status": "blocked",
                "reason": "riskonly baseline not found",
                "recover": recover,
            },
        )
        raise SystemExit("riskonly baseline not found")

    baseline_model = recover.get("adapter") or recover.get("merged")
    if not baseline_model:
        raise SystemExit("recovery returned no usable baseline model")

    baseline_suite = results_dir / "baseline-gate-summary.json"
    run_python(
        [
            "ml/correction-llm/scripts/run_gate_suite.py",
            "--model",
            baseline_model,
            "--raw-gate",
            args.raw_gate,
            "--repair-raw",
            args.raw_gate,
            "--repair-failed-report",
            args.repair_failed_report,
            "--report-dir",
            str(results_dir / "baseline-gates"),
            "--summary-out",
            str(baseline_suite),
        ],
        cwd=project_root(),
    )
    baseline_gate = read_json(baseline_suite)

    failure_summary = results_dir / "failure-curriculum.json"
    run_python(
        [
            "ml/correction-llm/scripts/build_failure_specific_curriculum.py",
            "--raw",
            args.raw_gate,
            "--direct-report",
            str(results_dir / "baseline-gates/direct.eval.jsonl"),
            "--repair-report",
            args.repair_failed_report,
            "--out-dir",
            str(requests_dir / "failure-curriculum"),
            "--summary-out",
            str(failure_summary),
            "--include-anchors",
        ],
        cwd=project_root(),
    )

    failed_resume_ids = [
        row["id"]
        for row in read_jsonl(results_dir / "baseline-gates/direct.eval.jsonl")
        if (not row.get("passed")) and row.get("task_type") == "RESUME_EXPRESSION_IMPROVEMENT"
    ]
    boundary_cmd = [
        "ml/correction-llm/scripts/build_resume_boundary_set.py",
        "--raw",
        args.raw_gate,
        "--output",
        str(requests_dir / "resume-boundary.messages.jsonl"),
        "--summary-out",
        str(results_dir / "resume-boundary.json"),
        "--offsets",
        "0,5,10,15",
    ]
    for sample_id in failed_resume_ids:
        boundary_cmd.extend(["--sample-id", sample_id])
    run_python(boundary_cmd, cwd=project_root())

    run_python(
        [
            "ml/correction-llm/scripts/build_resume_boundary_booster.py",
            "--messages",
            args.base_train,
            "--messages",
            args.base_val,
            "--direct-out",
            str(requests_dir / "resume-boundary-booster.direct.messages.jsonl"),
            "--repair-out",
            str(requests_dir / "resume-boundary-booster.repair.messages.jsonl"),
            "--summary-out",
            str(results_dir / "resume-boundary-booster.json"),
            "--max-headroom",
            "25",
            "--cap-step",
            "5",
        ],
        cwd=project_root(),
    )

    mixed_summary = results_dir / "mixed-curriculum.json"
    train_out = requests_dir / "mixed.train.messages.jsonl"
    val_out = requests_dir / "mixed.val.messages.jsonl"
    run_python(
        [
            "ml/correction-llm/scripts/build_mixed_followup_curriculum.py",
            "--train-source",
            f"base={args.base_train}@1",
            "--train-source",
            f"failure={requests_dir / 'failure-curriculum' / 'mixed.messages.jsonl'}@{args.failure_repeat}",
            "--train-source",
            f"resume_boundary={requests_dir / 'resume-boundary.messages.jsonl'}@{args.gate_boundary_repeat}",
            "--train-source",
            f"resume_boundary_direct={requests_dir / 'resume-boundary-booster.direct.messages.jsonl'}@{args.boundary_direct_repeat}",
            "--train-source",
            f"resume_boundary_repair={requests_dir / 'resume-boundary-booster.repair.messages.jsonl'}@{args.boundary_repair_repeat}",
            "--val-source",
            f"base_val={args.base_val}@1",
            "--train-out",
            str(train_out),
            "--val-out",
            str(val_out),
            "--summary-out",
            str(mixed_summary),
        ],
        cwd=project_root(),
    )

    trained_model = project_root() / "ml/correction-llm/out/correction-followup-automation-3b"
    train_cmd = [
        "ml/correction-llm/scripts/finetune_lora.py",
        "--train",
        str(train_out),
        "--eval",
        str(val_out),
        "--output",
        str(trained_model),
        "--epochs",
        str(args.train_epochs),
        "--batch-size",
        str(args.train_batch_size),
        "--grad-accum",
        str(args.train_grad_accum),
        "--lr",
        str(args.train_lr),
        "--max-seq-len",
        str(args.train_max_seq_len),
    ]
    if recover.get("adapter"):
        train_cmd.extend(["--resume-adapter", str(recover["adapter"])])
    else:
        train_cmd.extend(["--base-model", str(recover["merged"])])
    train_result = run_python(train_cmd, cwd=project_root())

    final_suite = results_dir / "final-gate-summary.json"
    run_python(
        [
            "ml/correction-llm/scripts/run_gate_suite.py",
            "--model",
            str(trained_model),
            "--raw-gate",
            args.raw_gate,
            "--repair-raw",
            args.raw_gate,
            "--repair-failed-report",
            args.repair_failed_report,
            "--report-dir",
            str(results_dir / "final-gates"),
            "--summary-out",
            str(final_suite),
        ],
        cwd=project_root(),
    )
    final_gate = read_json(final_suite)

    quality_passed = (
        passed_count(final_gate, "direct") >= args.min_direct_pass
        and total_count(final_gate, "direct") >= args.min_direct_pass
        and passed_count(final_gate, "repair") >= args.min_repair_pass
    )
    write_json(
        results_dir / "pipeline-summary.json",
        {
            "status": "ready" if quality_passed else "needs_more_data",
            "recover": recover,
            "baseline_gate": baseline_gate,
            "failure_curriculum": read_json(failure_summary),
            "resume_boundary": read_json(results_dir / "resume-boundary.json"),
            "resume_boundary_booster": read_json(results_dir / "resume-boundary-booster.json"),
            "mixed_curriculum": read_json(mixed_summary),
            "trained_model": str(trained_model),
            "final_gate": final_gate,
            "quality_passed": quality_passed,
            "train_stdout_tail": train_result.stdout[-4000:],
        },
    )
    print("ready" if quality_passed else "needs_more_data")


if __name__ == "__main__":
    main()

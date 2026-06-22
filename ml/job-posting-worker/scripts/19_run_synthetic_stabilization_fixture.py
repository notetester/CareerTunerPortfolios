"""Run a repo-owned synthetic stabilization drill."""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import tempfile
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
STABILIZATION_SCRIPT = SCRIPT_DIR / "16_run_stabilization_check.py"


def load_stabilization_module():
    spec = importlib.util.spec_from_file_location("synthetic_stabilization_check", STABILIZATION_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


STABILIZATION = load_stabilization_module()


def synthetic_text(index: int) -> str:
    role = ["Backend Engineer", "Frontend Engineer", "Data Engineer", "QA Engineer", "Product Manager"][index % 5]
    stack = ["Java Spring MyBatis MySQL", "React TypeScript Vite", "Python SQL Airflow", "Playwright API testing", "Roadmap discovery analytics"][index % 5]
    return "\n".join(
        [
            f"Company: Synthetic Commerce {index:02d}",
            f"Role: {role}",
            "Responsibilities: build production services, improve release stability, operate batch jobs, review incidents, and coordinate with product teams.",
            f"Qualifications: experience with {stack}, monitoring, regression testing, and production debugging.",
            f"Skills: {stack} Docker CI observability customer impact analysis.",
            "Employment: full-time Seoul hybrid role with benefits and on-call rotation.",
            "Apply: submit resume before the deadline.",
            "Deadline: 2026-07-31",
            "This posting intentionally includes enough body text to exceed the commercial automatic-analysis pass threshold.",
            "The role requires repeated service operation, API hardening, data quality checks, issue triage, and reliability improvements.",
            "Candidates should be able to document decisions, validate edge cases, and keep deployment risk visible to the team.",
        ]
    )


def create_fixture(input_dir: Path, count: int) -> None:
    input_dir.mkdir(parents=True, exist_ok=True)
    for index in range(1, count + 1):
        (input_dir / f"synthetic-posting-{index:02d}.txt").write_text(
            synthetic_text(index),
            encoding="utf-8",
        )


def run(count: int, output_dir: Path, report_path: Path) -> dict:
    with tempfile.TemporaryDirectory(prefix="ct_synthetic_stabilization_") as tmp:
        input_dir = Path(tmp) / "input"
        ocr_dir = Path(tmp) / "ocr"
        ocr_dir.mkdir(parents=True, exist_ok=True)
        create_fixture(input_dir, count)
        return STABILIZATION.run_check(
            input_dir=input_dir,
            existing_ocr_dir=ocr_dir,
            output_dir=output_dir,
            report_path=report_path,
            thresholds=STABILIZATION.StabilizationThresholds(min_file_count=count),
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--count", type=int, default=43)
    parser.add_argument("--output-dir", type=Path, default=Path(".tmp") / "job_posting_synthetic_stabilization")
    parser.add_argument("--report", type=Path, default=Path(".tmp") / "job_posting_synthetic_stabilization" / "document_pipeline_stabilization.md")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = run(args.count, args.output_dir, args.report)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    raise SystemExit(0 if summary["passed"] else 1)


if __name__ == "__main__":
    main()

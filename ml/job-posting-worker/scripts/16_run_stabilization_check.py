"""Run stabilization checks for the self-AI document extraction pipeline."""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DOCUMENT_SCRIPT = SCRIPT_DIR / "14_extract_document_text.py"


def load_document_module():
    spec = importlib.util.spec_from_file_location("document_text_extraction_stabilization", DOCUMENT_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


DOCUMENT = load_document_module()


@dataclass(frozen=True)
class StabilizationThresholds:
    min_file_count: int = 0
    pass_or_review_min_rate: float = 0.90
    failed_max_rate: float = 0.10
    pass_section_missing_max_rate: float = 0.05
    per_file_target_seconds: float = 180.0


def evaluate_results(
    results: list[dict[str, Any]],
    elapsed_seconds: float,
    durations_seconds: list[float] | None = None,
    thresholds: StabilizationThresholds = StabilizationThresholds(),
) -> dict[str, Any]:
    total = len(results)
    status_counts = {status: 0 for status in ("PASS", "REVIEW_REQUIRED", "FAILED")}
    for result in results:
        status_counts[result["qualityStatus"]] = status_counts.get(result["qualityStatus"], 0) + 1

    pass_or_review = status_counts.get("PASS", 0) + status_counts.get("REVIEW_REQUIRED", 0)
    pass_results = [result for result in results if result["qualityStatus"] == "PASS"]
    pass_section_missing = [
        result
        for result in pass_results
        if int(result.get("metrics", {}).get("sectionKeywordHitCount", 0)) < 2
    ]
    pass_or_review_rate = pass_or_review / total if total else 0.0
    failed_rate = status_counts.get("FAILED", 0) / total if total else 0.0
    pass_section_missing_rate = len(pass_section_missing) / len(pass_results) if pass_results else 0.0
    average_per_file_seconds = elapsed_seconds / total if total else 0.0
    max_per_file_seconds = max(durations_seconds or [average_per_file_seconds])
    gates = {
        "minFileCount": total >= thresholds.min_file_count,
        "passOrReviewRate": pass_or_review_rate >= thresholds.pass_or_review_min_rate,
        "failedRate": failed_rate <= thresholds.failed_max_rate,
        "passSectionMissingRate": pass_section_missing_rate <= thresholds.pass_section_missing_max_rate,
        "perFileTargetSeconds": max_per_file_seconds <= thresholds.per_file_target_seconds,
    }
    return {
        "total": total,
        "statusCounts": status_counts,
        "passOrReviewRate": round(pass_or_review_rate, 4),
        "failedRate": round(failed_rate, 4),
        "passSectionMissingRate": round(pass_section_missing_rate, 4),
        "elapsedSeconds": round(elapsed_seconds, 4),
        "averageSecondsPerFile": round(average_per_file_seconds, 4),
        "maxSecondsPerFile": round(max_per_file_seconds, 4),
        "gates": gates,
        "passed": all(gates.values()),
    }


def write_report(results: list[dict[str, Any]], summary: dict[str, Any], report_path: Path) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Document Pipeline Stabilization Check",
        "",
        "## Summary",
        "",
        f"- total: {summary['total']}",
        f"- statusCounts: `{json.dumps(summary['statusCounts'], ensure_ascii=False)}`",
        f"- passOrReviewRate: {summary['passOrReviewRate']}",
        f"- failedRate: {summary['failedRate']}",
        f"- passSectionMissingRate: {summary['passSectionMissingRate']}",
        f"- elapsedSeconds: {summary['elapsedSeconds']}",
        f"- averageSecondsPerFile: {summary['averageSecondsPerFile']}",
        f"- maxSecondsPerFile: {summary['maxSecondsPerFile']}",
        f"- passed: {summary['passed']}",
        "",
        "## Gates",
        "",
        "| Gate | Passed |",
        "|---|---:|",
    ]
    for gate, passed in summary["gates"].items():
        lines.append(f"| {gate} | {passed} |")
    lines.extend(
        [
            "",
            "## Files",
            "",
            "| File | Strategy | Text source | Score | Status | Sections | Warnings |",
            "|---|---|---|---:|---|---:|---|",
        ]
    )
    for result in sorted(results, key=lambda item: item["inputFile"]):
        metrics = result.get("metrics", {})
        warnings = ", ".join(result.get("warnings", []))
        lines.append(
            "| {inputFile} | {strategy} | {textSource} | {qualityScore} | {qualityStatus} | {sections} | {warnings} |".format(
                inputFile=result["inputFile"],
                strategy=result["strategy"],
                textSource=result["textSource"],
                qualityScore=result["qualityScore"],
                qualityStatus=result["qualityStatus"],
                sections=metrics.get("sectionKeywordHitCount", 0),
                warnings=warnings,
            )
        )
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def run_check(
    input_dir: Path,
    existing_ocr_dir: Path,
    output_dir: Path,
    report_path: Path,
    thresholds: StabilizationThresholds = StabilizationThresholds(),
) -> dict[str, Any]:
    files = DOCUMENT.discover_input_files(input_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    started_at = time.perf_counter()
    results = []
    durations = []
    for path in files:
        file_started_at = time.perf_counter()
        results.append(DOCUMENT.extract_document(path, output_dir=output_dir, existing_ocr_dir=existing_ocr_dir))
        durations.append(time.perf_counter() - file_started_at)
    elapsed = time.perf_counter() - started_at
    summary = evaluate_results(results, elapsed, durations, thresholds=thresholds)
    write_report(results, summary, report_path)
    summary_path = output_dir / "stabilization_summary.json"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return summary


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    base = root / "data" / "real_validation"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", type=Path, default=base / "raw_ocr_inputs_selected_20")
    parser.add_argument("--existing-ocr-dir", type=Path, default=base / "ocr_postings_selected_20")
    parser.add_argument("--output-dir", type=Path, default=base / "document_pipeline_stabilization")
    parser.add_argument("--report", type=Path, default=root / "reports" / "document_pipeline_stabilization.md")
    parser.add_argument("--min-files", type=int, default=20)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = run_check(
        args.input_dir,
        args.existing_ocr_dir,
        args.output_dir,
        args.report,
        thresholds=StabilizationThresholds(min_file_count=args.min_files),
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    raise SystemExit(0 if summary["passed"] else 1)


if __name__ == "__main__":
    main()

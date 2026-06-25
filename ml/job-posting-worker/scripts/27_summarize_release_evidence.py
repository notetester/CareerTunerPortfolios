"""Generate a human-readable Markdown report from release evidence JSON."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[2]


def status_label(ok: bool) -> str:
    return "PASS" if ok else "FAIL"


def scalar(value: Any, default: str = "-") -> str:
    if value is None:
        return default
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float):
        return f"{value:.4f}".rstrip("0").rstrip(".")
    return str(value)


def table_cell(value: Any) -> str:
    return scalar(value).replace("|", "\\|").replace("\n", " ")


def step_metric(step: dict[str, Any]) -> str:
    payload = step.get("payload", {})
    name = step.get("name", "")
    if name == "real regression inventory":
        return (
            f"ready={scalar(payload.get('readyJobPostingCount'))}/"
            f"{scalar(payload.get('targetCount'))}, "
            f"unique={scalar(payload.get('uniqueReadyJobPostingCount'))}, "
            f"dupExtra={scalar(payload.get('duplicateReadyExtraCount'))}, "
            f"missingOcr={scalar(payload.get('missingOcrJobPostingCount'))}, "
            f"nonJob={scalar(payload.get('nonJobReferenceCount'))}"
        )
    if name == "real regression set":
        return (
            f"selected={scalar(payload.get('selectedCount'))}/"
            f"{scalar(payload.get('targetCount'))}, "
            f"additionalNeeded={scalar(payload.get('additionalReadyNeeded'))}"
        )
    if name in {"real stabilization gate", "worker operational drills"}:
        if "statusCounts" in payload:
            counts = payload.get("statusCounts", {})
            return (
                f"total={scalar(payload.get('total'))}, "
                f"PASS={scalar(counts.get('PASS'))}, "
                f"REVIEW={scalar(counts.get('REVIEW_REQUIRED'))}, "
                f"FAILED={scalar(counts.get('FAILED'))}, "
                f"passOrReview={scalar(payload.get('passOrReviewRate'))}"
            )
        return f"total={scalar(payload.get('total'))}, failed={scalar(payload.get('failed'))}"
    if name == "release readiness target-file gate":
        checks = payload.get("checks", [])
        failed = [check for check in checks if not check.get("passed")]
        return f"checks={len(checks)}, failed={len(failed)}"
    if name == "worker Docker runtime smoke":
        return scalar(payload.get("error"), "docker smoke evidence")
    if name == "worker OCR runtime smoke":
        return scalar(payload.get("error"), "ocr smoke evidence")
    if name == "staging DB migration evidence":
        return scalar(payload.get("error"), "db migration evidence")
    if name == "production readiness audit":
        blockers = payload.get("blockingItems", [])
        return f"blocking={len(blockers)}"
    return scalar(payload.get("error"), "")


def artifacts(payload: dict[str, Any]) -> list[str]:
    paths: list[str] = []
    for step in payload.get("steps", []):
        evidence = step.get("evidence")
        if evidence:
            paths.append(str(evidence))
    production = payload.get("productionReadiness")
    if production:
        paths.append(str(production))
    return sorted(set(paths))


def summarize(payload: dict[str, Any]) -> str:
    lines = [
        "# Job Posting Pipeline Release Evidence",
        "",
        "## Summary",
        "",
        f"- status: {status_label(bool(payload.get('ok')))}",
        f"- targetCount: {scalar(payload.get('targetCount'))}",
        f"- repoRoot: `{scalar(payload.get('repoRoot'))}`",
        "",
        "## Blocking Items",
        "",
    ]
    blocking = payload.get("blockingItems", [])
    if blocking:
        lines.extend(f"- {item}" for item in blocking)
    else:
        lines.append("- none")
    lines.extend([
        "",
        "## Evidence Steps",
        "",
        "| Step | Status | Metric | Evidence |",
        "|---|---|---|---|",
    ])
    for step in payload.get("steps", []):
        lines.append(
            "| {name} | {status} | {metric} | {evidence} |".format(
                name=table_cell(step.get("name")),
                status=status_label(bool(step.get("ok"))),
                metric=table_cell(step_metric(step)),
                evidence=table_cell(step.get("evidence")),
            )
        )

    inventory = next((step.get("payload", {}) for step in payload.get("steps", []) if step.get("name") == "real regression inventory"), {})
    if inventory:
        lines.extend([
            "",
            "## Data Gaps",
            "",
            f"- additional ready postings needed: {scalar(inventory.get('additionalReadyNeeded'))}",
            f"- OCR backfill needed: {scalar(inventory.get('missingOcrJobPostingCount'))}",
        ])
        for file_name in inventory.get("ocrBackfillNeeded", []):
            lines.append(f"  - {file_name}")

    lines.extend([
        "",
        "## Artifacts",
        "",
    ])
    for path in artifacts(payload):
        lines.append(f"- `{path}`")
    return "\n".join(lines) + "\n"


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8", errors="replace"))


def write_report(payload: dict[str, Any], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(summarize(payload), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_REPO_ROOT / ".tmp" / "job_posting_release_evidence.json")
    parser.add_argument("--output", type=Path, default=DEFAULT_REPO_ROOT / ".tmp" / "job_posting_release_evidence.md")
    parser.add_argument("--fail-on-not-ready", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    payload = read_json(args.input)
    write_report(payload, args.output)
    print(args.output)
    raise SystemExit(1 if args.fail_on_not_ready and not payload.get("ok") else 0)


if __name__ == "__main__":
    main()

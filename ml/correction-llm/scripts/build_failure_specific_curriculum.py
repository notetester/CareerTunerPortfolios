"""Build direct and repair follow-up curricula grouped by failure kind."""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from build_messages import SYSTEM_PROMPT
from build_p1_curriculum import REPAIR_TEMPLATE
from followup_pipeline_common import (
    build_direct_message,
    build_repair_message,
    classify_problem_kind,
    read_jsonl,
    write_json,
    write_jsonl,
)


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", required=True)
    parser.add_argument("--direct-report", required=True)
    parser.add_argument("--repair-report", default=None)
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--summary-out", required=True)
    parser.add_argument("--include-anchors", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = build_args()
    raw_rows = read_jsonl(Path(args.raw))
    raw_by_id = {row["id"]: row for row in raw_rows}
    direct_rows = read_jsonl(Path(args.direct_report))
    repair_rows = read_jsonl(Path(args.repair_report)) if args.repair_report else []
    out_dir = Path(args.out_dir)

    buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
    summary: dict[str, Any] = {
        "direct_source_count": len(direct_rows),
        "repair_source_count": len(repair_rows),
        "bucket_counts": {},
        "failed_ids": defaultdict(list),
        "anchor_ids": [],
    }

    for row in direct_rows:
        sample = raw_by_id.get(row["id"])
        if sample is None:
            raise ValueError(f"raw sample not found for direct report id={row['id']}")
        if row.get("passed"):
            if args.include_anchors:
                buckets["direct.anchor"].append(build_direct_message(SYSTEM_PROMPT, sample))
                summary["anchor_ids"].append(row["id"])
            continue
        kind = classify_problem_kind([str(item) for item in row.get("problems", [])])
        buckets[f"direct.failed.{kind}"].append(build_direct_message(SYSTEM_PROMPT, sample))
        buckets[f"repair.{kind}"].append(
            build_repair_message(
                SYSTEM_PROMPT,
                REPAIR_TEMPLATE,
                sample,
                previous_output=str(row.get("output", "")),
                validation_error="; ".join(str(item) for item in row.get("problems", [])),
            )
        )
        summary["failed_ids"][kind].append(row["id"])

    for row in repair_rows:
        if row.get("passed"):
            continue
        sample = raw_by_id.get(row["id"])
        if sample is None:
            raise ValueError(f"raw sample not found for repair report id={row['id']}")
        kind = classify_problem_kind([str(item) for item in row.get("problems", [])])
        buckets[f"repair.{kind}"].append(
            build_repair_message(
                SYSTEM_PROMPT,
                REPAIR_TEMPLATE,
                sample,
                previous_output=str(row.get("previous_output") or row.get("output", "")),
                validation_error="; ".join(str(item) for item in row.get("problems", [])),
            )
        )

    mixed_rows: list[dict[str, Any]] = []
    for name, rows in sorted(buckets.items()):
        path = out_dir / f"{name}.messages.jsonl"
        write_jsonl(path, rows)
        mixed_rows.extend(rows)
        summary["bucket_counts"][name] = len(rows)
    write_jsonl(out_dir / "mixed.messages.jsonl", mixed_rows)
    summary["bucket_counts"]["mixed"] = len(mixed_rows)
    summary["failed_ids"] = dict(sorted(summary["failed_ids"].items()))
    summary["failure_kind_counts"] = dict(
        sorted(Counter(kind for kind, ids in summary["failed_ids"].items() for _ in ids).items())
    )
    write_json(Path(args.summary_out), summary)
    print(summary["bucket_counts"])


if __name__ == "__main__":
    main()

"""Summarize RAG hard-case benchmark result JSON files."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any


def load_results(results_dir: Path) -> list[dict[str, Any]]:
    rows = []
    for path in sorted(results_dir.glob("*.result.json")):
        with path.open(encoding="utf-8") as handle:
            row = json.load(handle)
        row["_path"] = str(path)
        rows.append(row)
    return rows


def average(values: list[int | float]) -> float | None:
    if not values:
        return None
    return round(sum(values) / len(values), 2)


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    by_variant: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        by_variant[str(row.get("variant"))].append(row)

    variants = {}
    for variant, items in sorted(by_variant.items()):
        latencies = [item["metrics"]["latency_ms"] for item in items
                     if isinstance(item.get("metrics", {}).get("latency_ms"), (int, float))]
        lengths = [item["metrics"]["output_length"] for item in items
                   if isinstance(item.get("metrics", {}).get("output_length"), (int, float))]
        null_metrics = 0
        for item in items:
            for value in (item.get("metrics") or {}).values():
                if value is None:
                    null_metrics += 1
        variants[variant] = {
            "caseCount": len(items),
            "errorCount": sum(1 for item in items if item.get("error")),
            "rawOutputCount": sum(1 for item in items if item.get("rawOutputExists")),
            "averageLatencyMs": average(latencies),
            "averageOutputLength": average(lengths),
            "placeholderNullMetricCount": null_metrics,
        }

    return {
        "resultCount": len(rows),
        "variantCount": len(variants),
        "variants": variants,
    }


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    rows = load_results(args.results)
    summary = summarize(rows)
    write_json(args.out, summary)
    print("[summarize_rag_hardcase_results]")
    print(f"  results={args.results}")
    print(f"  out={args.out}")
    print(f"  resultCount={summary['resultCount']} variantCount={summary['variantCount']}")
    for variant, stats in summary["variants"].items():
        print(f"  {variant}: cases={stats['caseCount']} errors={stats['errorCount']} "
              f"avgLatency={stats['averageLatencyMs']} avgOutputLength={stats['averageOutputLength']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

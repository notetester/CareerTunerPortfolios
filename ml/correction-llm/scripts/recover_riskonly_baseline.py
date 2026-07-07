"""Locate and optionally copy the riskonly 3B baseline assets used for follow-up training."""

from __future__ import annotations

import argparse
import os
import shutil
from pathlib import Path
from typing import Any

from followup_pipeline_common import project_root, read_json, write_json


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--summary",
        default=str(
            project_root()
            / "docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-05-expanded-300/results/stage100-riskonly-training-summary.json"
        ),
    )
    parser.add_argument("--search-root", action="append", default=[])
    parser.add_argument("--report-out", required=True)
    parser.add_argument("--copy-adapter-to", default=None)
    parser.add_argument("--copy-merged-to", default=None)
    parser.add_argument("--allow-merged-only", action="store_true")
    return parser.parse_args()


def is_adapter_dir(path: Path) -> bool:
    return path.is_dir() and (path / "adapter_config.json").exists() and (path / "adapter_model.safetensors").exists()


def is_merged_model_dir(path: Path) -> bool:
    if not path.is_dir():
        return False
    has_model = (path / "config.json").exists() or any(path.glob("model*.safetensors"))
    has_tokenizer = (path / "tokenizer.json").exists() or (path / "tokenizer_config.json").exists()
    return has_model and has_tokenizer


def iter_search_roots(explicit_roots: list[str]) -> list[Path]:
    roots = [project_root(), Path.home()]
    for value in explicit_roots:
        roots.append(Path(value))
    unique: list[Path] = []
    seen: set[Path] = set()
    for root in roots:
        resolved = root.expanduser().resolve()
        if resolved not in seen and resolved.exists():
            unique.append(resolved)
            seen.add(resolved)
    return unique


def resolve_candidate(path_value: str | None) -> Path | None:
    if not path_value:
        return None
    path = Path(path_value).expanduser()
    if not path.is_absolute():
        path = project_root() / path
    return path.resolve()


def search_by_name(name: str, roots: list[Path], validator) -> list[Path]:
    matches: list[Path] = []
    seen: set[Path] = set()
    for root in roots:
        try:
            for candidate in root.rglob(name):
                resolved = candidate.resolve()
                if resolved in seen:
                    continue
                if validator(resolved):
                    matches.append(resolved)
                    seen.add(resolved)
        except (OSError, PermissionError):
            continue
    return matches


def copy_dir(source: Path, target_value: str | None) -> str | None:
    if target_value is None:
        return None
    target = Path(target_value).expanduser()
    if not target.is_absolute():
        target = project_root() / target
    if target.exists():
        shutil.rmtree(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source, target)
    return str(target.resolve())


def main() -> None:
    args = build_args()
    summary = read_json(Path(args.summary))
    roots = iter_search_roots(args.search_root)

    adapter_name = Path(str(summary.get("selected_adapter", ""))).name
    merged_name = Path(str(summary.get("export", {}).get("merged_model", ""))).name

    adapter_candidates: list[Path] = []
    merged_candidates: list[Path] = []

    explicit_adapter = resolve_candidate(os.getenv("CAREERTUNER_E_RISKONLY_ADAPTER_PATH"))
    explicit_merged = resolve_candidate(os.getenv("CAREERTUNER_E_RISKONLY_MERGED_PATH"))
    summary_adapter = resolve_candidate(str(summary.get("selected_adapter", "")))
    summary_merged = resolve_candidate(str(summary.get("export", {}).get("merged_model", "")))

    for candidate in (explicit_adapter, summary_adapter):
        if candidate and is_adapter_dir(candidate):
            adapter_candidates.append(candidate)
    for candidate in (explicit_merged, summary_merged):
        if candidate and is_merged_model_dir(candidate):
            merged_candidates.append(candidate)

    adapter_candidates.extend(
        candidate for candidate in search_by_name(adapter_name, roots, is_adapter_dir) if candidate not in adapter_candidates
    )
    merged_candidates.extend(
        candidate for candidate in search_by_name(merged_name, roots, is_merged_model_dir) if candidate not in merged_candidates
    )

    selected_adapter = adapter_candidates[0] if adapter_candidates else None
    selected_merged = merged_candidates[0] if merged_candidates else None

    copied_adapter = copy_dir(selected_adapter, args.copy_adapter_to) if selected_adapter else None
    copied_merged = copy_dir(selected_merged, args.copy_merged_to) if selected_merged else None

    mode = "none"
    if selected_adapter:
        mode = "adapter"
    elif selected_merged:
        mode = "merged"

    report: dict[str, Any] = {
        "status": "ok" if mode != "none" and (mode == "adapter" or args.allow_merged_only) else "missing",
        "mode": mode,
        "adapter": str(selected_adapter) if selected_adapter else None,
        "merged": str(selected_merged) if selected_merged else None,
        "copied_adapter": copied_adapter,
        "copied_merged": copied_merged,
        "search_roots": [str(root) for root in roots],
        "adapter_candidates": [str(path) for path in adapter_candidates],
        "merged_candidates": [str(path) for path in merged_candidates],
    }
    write_json(Path(args.report_out), report)
    print(report["status"])
    print(f"mode={mode}")
    if selected_adapter:
        print(f"adapter={selected_adapter}")
    if selected_merged:
        print(f"merged={selected_merged}")


if __name__ == "__main__":
    main()

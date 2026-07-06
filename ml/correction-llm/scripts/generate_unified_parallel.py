"""Run one resumable unified-v2 generator per correction task in parallel."""

from __future__ import annotations

import argparse
import os
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from types import SimpleNamespace

from generate_unified_data import TASKS, TASK_CODES, generate
from split_raw_by_task import read_jsonl, split, write_task_files


def run_task(task_type: str, args: argparse.Namespace) -> str:
    run_root = Path(args.run_root)
    output = run_root / "requests" / "by-task" / f"raw.{TASK_CODES[task_type]}.jsonl"
    summary = run_root / "results" / f"generation.{TASK_CODES[task_type]}.json"
    generation_args = SimpleNamespace(
        per_task=args.per_task,
        task=[task_type],
        output=str(output),
        summary_out=str(summary),
        model=args.model,
        base_url=args.base_url,
        max_output_tokens=args.max_output_tokens,
        request_timeout=args.request_timeout,
        max_attempts=args.max_attempts,
        retry_backoff=args.retry_backoff,
        request_interval=args.request_interval,
        resume=True,
        dry_run=False,
    )
    for session in range(1, args.session_restarts + 2):
        try:
            print(f"[{TASK_CODES[task_type]}] session={session} start", flush=True)
            generate(generation_args)
            return task_type
        except SystemExit as exc:
            if session > args.session_restarts:
                raise RuntimeError(f"{task_type} failed after {session} sessions: {exc}") from exc
            print(f"[{TASK_CODES[task_type]}] session={session} failed: {exc}", flush=True)
            time.sleep(args.session_backoff * session)
    raise RuntimeError(f"{task_type} did not complete")


def seed_task_files(run_root: Path, *, overwrite: bool) -> None:
    combined = run_root / "requests" / "raw.generated.jsonl"
    output_dir = run_root / "requests" / "by-task"
    expected = [output_dir / f"raw.{TASK_CODES[task]}.jsonl" for task in TASKS]
    if all(path.exists() for path in expected):
        return
    if not combined.exists():
        raise FileNotFoundError(f"Seed raw file does not exist: {combined}")
    write_task_files(output_dir, split(read_jsonl(combined)), overwrite=overwrite)


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-root", required=True)
    parser.add_argument("--per-task", type=int, default=20)
    parser.add_argument("--task", action="append", choices=TASKS, default=None)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--session-restarts", type=int, default=3)
    parser.add_argument("--session-backoff", type=float, default=5.0)
    parser.add_argument("--model", default=os.environ.get("OPENAI_MODEL", "gpt-5"))
    parser.add_argument("--base-url", default=os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1"))
    parser.add_argument("--max-output-tokens", type=int, default=16000)
    parser.add_argument("--request-timeout", type=float, default=300.0)
    parser.add_argument("--max-attempts", type=int, default=5)
    parser.add_argument("--retry-backoff", type=float, default=2.0)
    parser.add_argument("--request-interval", type=float, default=1.0)
    parser.add_argument("--overwrite-seed-files", action="store_true")
    args = parser.parse_args()
    if args.workers <= 0 or args.per_task <= 0:
        parser.error("--workers and --per-task must be positive")
    return args


def main() -> None:
    args = build_args()
    if not os.environ.get("OPENAI_API_KEY", "").strip():
        raise SystemExit("OPENAI_API_KEY is required")
    run_root = Path(args.run_root)
    seed_task_files(run_root, overwrite=args.overwrite_seed_files)
    tasks = tuple(args.task) if args.task else TASKS
    with ThreadPoolExecutor(max_workers=min(args.workers, len(tasks))) as executor:
        futures = {executor.submit(run_task, task, args): task for task in tasks}
        for future in as_completed(futures):
            task = futures[future]
            future.result()
            print(f"[{TASK_CODES[task]}] complete", flush=True)


if __name__ == "__main__":
    main()

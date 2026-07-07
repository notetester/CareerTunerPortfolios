"""GPU 옵션2/옵션4 최적값 탐색용 동시성 부하 테스트 하네스.

A-only baseline fixture 의 케이스를 지정 동시성(--concurrency)으로 재생해 p50/p95 latency,
오류율, 처리량을 측정한다. 서버 상한(OLLAMA_NUM_PARALLEL 등) × 백엔드 상한(gpu-gate) 조합
비교(4090_OLLAMA_CONCURRENCY_TUNING.md §6)의 실행 도구다.

- run_rag_hardcase_benchmark / run_a_only_baseline_benchmark 의 검증된 헬퍼를 import 재사용한다
  (중복 구현 금지). raw output 텍스트는 저장하지 않는다(길이만) — 요약 JSON 은 커밋 가능.
- 4090 이 꺼져 있어도 --dry-run 으로 계획 검증이 가능하다.

사용 예 (4090 복귀 후, 조합마다 --label 만 바꿔 실행):
    python ml/career-strategy-llm/scripts/run_concurrency_load_test.py \
        --fixture ml/career-strategy-llm/data/evidence_attribution_baseline/a_only_baseline_v1.jsonl \
        --base-url http://127.0.0.1:11435 --model careertuner-c-career-strategy-3b \
        --concurrency 2 --repeat 2 --label server-np2 \
        --out ml/career-strategy-llm/reports/generated/loadtest_np2_c2.json
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCRIPTS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPTS_DIR))

from run_rag_hardcase_benchmark import call_ollama, output_policy, GENERATED_ROOT  # noqa: E402
from run_a_only_baseline_benchmark import a_payload, baseline_prompt  # noqa: E402
from validate_a_only_baseline_fixture import load_rows  # noqa: E402


def percentile(sorted_values: list[float], p: float) -> float | None:
    if not sorted_values:
        return None
    idx = min(len(sorted_values) - 1, max(0, round(p / 100 * (len(sorted_values) - 1))))
    return round(sorted_values[idx], 1)


def run_one(base_url: str, model: str, prompt: str, timeout_seconds: int) -> dict[str, Any]:
    started = time.perf_counter()
    try:
        text = call_ollama(base_url, model, prompt, timeout_seconds)
        return {"ok": True, "latencyMs": round((time.perf_counter() - started) * 1000, 1),
                "outputLength": len(text)}
    except Exception as exc:  # noqa: BLE001 — 부하 측정: 모든 실패를 오류율로 집계
        return {"ok": False, "latencyMs": round((time.perf_counter() - started) * 1000, 1),
                "error": str(exc)[:200]}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--base-url", default="http://127.0.0.1:11435")
    parser.add_argument("--model", default="careertuner-c-career-strategy-3b")
    parser.add_argument("--concurrency", type=int, required=True)
    parser.add_argument("--repeat", type=int, default=1, help="fixture 전체를 몇 번 재생할지")
    parser.add_argument("--limit", type=int, help="케이스 수 상한(빠른 예비 측정용)")
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--label", default="", help="조합 태그 (예: server-np2_gate-off)")
    parser.add_argument("--dry-run", action="store_true", help="계획만 출력, 모델 호출 없음")
    args = parser.parse_args(argv)

    rows = load_rows(args.fixture)
    if args.limit:
        rows = rows[: args.limit]
    prompts = [baseline_prompt(a_payload(row)) for row in rows] * max(1, args.repeat)

    policy = output_policy(args.out.parent)
    print(f"[load-test] cases={len(rows)} repeat={args.repeat} total={len(prompts)} "
          f"concurrency={args.concurrency} label={args.label!r} outputPolicy={policy}")
    if args.dry_run:
        print("[load-test] dry-run — 모델 호출 없이 종료")
        return 0

    records: list[dict[str, Any]] = []
    lock = threading.Lock()
    wall_started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(run_one, args.base_url, args.model, p, args.timeout_seconds)
                   for p in prompts]
        for future in as_completed(futures):
            record = future.result()
            with lock:
                records.append(record)
                done = len(records)
            if done % 20 == 0 or done == len(prompts):
                print(f"[load-test] {done}/{len(prompts)}")
    wall_seconds = round(time.perf_counter() - wall_started, 2)

    ok_latencies = sorted(r["latencyMs"] for r in records if r["ok"])
    errors = [r for r in records if not r["ok"]]
    summary = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "label": args.label,
        "fixture": str(args.fixture),
        "model": args.model,
        "baseUrl": args.base_url,
        "concurrency": args.concurrency,
        "repeat": args.repeat,
        "totalRequests": len(records),
        "errorCount": len(errors),
        "errorRate": round(len(errors) / len(records), 4) if records else None,
        "latencyMs": {
            "p50": percentile(ok_latencies, 50),
            "p90": percentile(ok_latencies, 90),
            "p95": percentile(ok_latencies, 95),
            "p99": percentile(ok_latencies, 99),
            "max": ok_latencies[-1] if ok_latencies else None,
            "mean": round(statistics.fmean(ok_latencies), 1) if ok_latencies else None,
        },
        "wallSeconds": wall_seconds,
        "throughputRps": round(len(records) / wall_seconds, 3) if wall_seconds else None,
        "errorSamples": [e["error"] for e in errors[:5]],
        "records": records,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"[load-test] wall={wall_seconds}s errorRate={summary['errorRate']} "
          f"p50={summary['latencyMs']['p50']}ms p95={summary['latencyMs']['p95']}ms → {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

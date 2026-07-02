"""A-only production-path baseline runner — 단일 variant(A_lora_only), 무버킷.

run_rag_hardcase_benchmark 의 검증된 헬퍼(call_ollama/경로 정책/결과 스키마)를 import 재사용한다(중복 구현 금지).
차이: variant 는 A 하나뿐이고 evidenceBuckets 를 절대 넣지 않는다(reports/77 §8 — 측정 대상은 production 경로).
출력 레이아웃(requests/outputs/results/benchmark_manifest.json)은 기존과 동일해 후속 도구가 재사용 가능하다.

실행(원격 Ollama 는 SSH 터널로 loopback 화 권장):
  python scripts/run_a_only_baseline_benchmark.py --fixture data/evidence_attribution_baseline/a_only_baseline_v1.jsonl \
      --out <CareerTunerAI>/benchmarks/evidence-attribution-baseline/runs/<runId> \
      --provider ollama --model careertuner-c-career-strategy-3b --base-url http://127.0.0.1:11435
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from run_rag_hardcase_benchmark import (  # noqa: E402
    METRIC_SCHEMA, assert_output_path_allowed, call_ollama, is_allowed_base_url,
    output_policy, result_payload, write_json,
)
from validate_a_only_baseline_fixture import load_rows, validate_rows  # noqa: E402

VARIANT = "A_lora_only"


def a_payload(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "caseId": row["caseId"],
        "category": row["category"],
        "intent": row["intent"],
        "variant": VARIANT,
        "input": {"profile": row["profile"], "job": row["job"]},  # 무버킷 — production 경로와 동일 입력 구성
        "expected": row["expected"],
        "resultSchema": {"rawOutput": None, "parsedOutput": None,
                         "gateResult": {"gateStatus": None, "reasonCount": None, "maxSeverity": None, "reasons": []},
                         "metrics": dict(METRIC_SCHEMA)},
    }


def baseline_prompt(payload: dict[str, Any]) -> str:
    """Benchmark 전용 prompt(production prompt 아님). 버킷 관련 문구 없음."""
    return (
        "[CareerTuner A-only baseline benchmark]\n"
        "아래 입력은 offline benchmark 전용 payload 입니다. production prompt 가 아닙니다.\n"
        "점수나 지원 판단을 새로 만들지 말고, 사용자가 보유하지 않은 역량·자격증을 보유로 단정하지 마세요.\n"
        "profile 에 있는 항목만 사용자 보유 근거로 볼 수 있습니다. JSON 객체만 반환하세요.\n\n"
        + json.dumps(payload, ensure_ascii=False, indent=2)
    )


def run(fixture: Path, out_dir: Path, dry_run: bool, base_url: str | None, model: str | None,
        timeout_seconds: int, continue_on_error: bool) -> dict[str, Any]:
    assert_output_path_allowed(out_dir)
    rows = load_rows(fixture)
    errors = validate_rows(rows)
    if errors:
        raise SystemExit("fixture validation failed:\n" + "\n".join(f" - {e}" for e in errors))

    manifest: dict[str, Any] = {
        "fixture": str(fixture), "generatedAt": datetime.now(timezone.utc).isoformat(),
        "dryRun": dry_run, "provider": "dry-run" if dry_run else "ollama", "model": model,
        "baseUrl": base_url, "outputPolicy": output_policy(out_dir), "modelCalls": 0,
        "variants": [VARIANT], "metricsSchema": dict(METRIC_SCHEMA), "cases": [],
    }
    for row in rows:
        payload = a_payload(row)
        stem = f"{row['caseId']}_{VARIANT}"
        request_path = out_dir / "requests" / f"{stem}.json"
        raw_path = out_dir / "outputs" / f"{stem}.raw.txt"
        result_path = out_dir / "results" / f"{stem}.result.json"
        write_json(request_path, payload)

        raw_output = latency_ms = error = raw_rel = None
        if not dry_run:
            started = time.perf_counter()
            try:
                raw_output = call_ollama(base_url, model, baseline_prompt(payload), timeout_seconds)
                latency_ms = int((time.perf_counter() - started) * 1000)
                raw_path.parent.mkdir(parents=True, exist_ok=True)
                raw_path.write_text(raw_output, encoding="utf-8")
                raw_rel = str(raw_path.relative_to(out_dir))
                manifest["modelCalls"] += 1
            except Exception as exc:  # noqa: BLE001
                latency_ms = int((time.perf_counter() - started) * 1000)
                error = str(exc)
                if not continue_on_error:
                    raise SystemExit(f"[{row['caseId']}] {error}") from exc
        write_json(result_path, result_payload(payload, manifest["provider"], model, base_url,
                                               latency_ms, raw_rel, raw_output, error))
        manifest["cases"].append({"caseId": row["caseId"], "category": row["category"],
                                  "requests": [str(request_path)], "outputs": [str(raw_path)] if raw_rel else [],
                                  "results": [str(result_path)]})
    write_json(out_dir / "benchmark_manifest.json", manifest)
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--provider", choices=["ollama"])
    parser.add_argument("--base-url", default="http://127.0.0.1:11434")
    parser.add_argument("--model")
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument("--continue-on-error", action="store_true")
    parser.add_argument("--allow-remote", action="store_true")
    args = parser.parse_args(argv)

    if not args.dry_run:
        if args.provider != "ollama" or not args.model:
            raise SystemExit("non-dry-run requires --provider ollama --model <name>")
        if not args.allow_remote and not is_allowed_base_url(args.base_url):
            raise SystemExit(f"Refusing non-local/private base-url: {args.base_url}")
    manifest = run(args.fixture, args.out, args.dry_run,
                   None if args.dry_run else args.base_url, args.model,
                   args.timeout_seconds, args.continue_on_error)
    print(f"[run_a_only_baseline_benchmark] cases={len(manifest['cases'])} "
          f"modelCalls={manifest['modelCalls']} dryRun={args.dry_run} out={args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

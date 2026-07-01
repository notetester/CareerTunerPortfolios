"""Generate offline A/B payloads/results for the RAG hard-case benchmark.

This runner writes reproducible request payloads for:
  A: 3B LoRA only baseline(profile + job)
  B: 3B LoRA + structured evidence buckets

By default it is a dry-run generator. With ``--provider ollama`` it can call a
local/offline Ollama endpoint only; it never calls production backend or external
LLM APIs.

Raw outputs and generated files belong under reports/generated/ and must not be
committed to the main repository. Persistent run artifacts should live in the
CareerTunerAI repository.
"""

from __future__ import annotations

import argparse
import json
import time
import sys
from datetime import datetime, timezone
from ipaddress import ip_address
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from validate_rag_hardcase_fixture import load_jsonl, validate_rows  # noqa: E402

ML_ROOT = SCRIPT_DIR.parent
REPO_ROOT = SCRIPT_DIR.parents[2]
GENERATED_ROOT = (ML_ROOT / "reports" / "generated").resolve()
CAREERTUNER_AI_ROOT = Path("D:/dev/CareerTunerAI").resolve()

METRIC_SCHEMA: dict[str, Any] = {
    "contract_success": None,
    "json_parse_success": None,
    "unsupported_possession_claim_count": None,
    "r3_gate_status": None,
    "r3_reason_count": None,
    "r3_max_severity": None,
    "raw_hallucinated_skill_count": None,
    "normalized_hallucinated_skill_count": None,
    "semantic_judge_hallucinated_skill_count": None,
    "cjk_leak": None,
    "latency_ms": None,
    "output_length": None,
}


def is_allowed_base_url(base_url: str) -> bool:
    parsed = urlparse(base_url)
    if parsed.scheme not in {"http", "https"}:
        return False
    host = parsed.hostname
    if not host:
        return False
    if host == "localhost":
        return True
    try:
        ip = ip_address(host)
    except ValueError:
        return False
    if ip.is_loopback:
        return True
    if ip.version == 4:
        parts = host.split(".")
        if parts[0] == "10":
            return True
        if parts[0] == "172" and 16 <= int(parts[1]) <= 31:
            return True
        if parts[0] == "192" and parts[1] == "168":
            return True
        if parts[0] == "100":
            return True
    return False


def assert_output_path_allowed(out_dir: Path) -> None:
    resolved = out_dir.resolve()
    if resolved.is_relative_to(REPO_ROOT.resolve()) and not resolved.is_relative_to(GENERATED_ROOT):
        raise SystemExit(
            "CareerTuner repo 내부 출력은 ml/career-strategy-llm/reports/generated/ 아래만 허용합니다: "
            f"{resolved}"
        )


def output_policy(out_dir: Path) -> str:
    resolved = out_dir.resolve()
    if resolved.is_relative_to(GENERATED_ROOT):
        return "career-tuner-generated-ignore"
    if resolved.is_relative_to(CAREERTUNER_AI_ROOT):
        return "careertuner-ai-artifact"
    return "external-local-output"


def variant_payload(row: dict[str, Any], variant: str) -> dict[str, Any]:
    base_input: dict[str, Any] = {
        "profile": row["profile"],
        "job": row["job"],
    }
    if variant == "B_structured_evidence_buckets":
        base_input["evidenceBuckets"] = row["evidenceBuckets"]

    return {
        "caseId": row["caseId"],
        "category": row["category"],
        "intent": row["intent"],
        "variant": variant,
        "input": base_input,
        "expected": row["expected"],
        "resultSchema": {
            "rawOutput": None,
            "parsedOutput": None,
            "gateResult": {
                "gateStatus": None,
                "reasonCount": None,
                "maxSeverity": None,
                "reasons": [],
            },
            "metrics": dict(METRIC_SCHEMA),
        },
    }


def benchmark_prompt(payload: dict[str, Any]) -> str:
    """Benchmark-only prompt. This is not the production prompt."""
    return (
        "[CareerTuner RAG hard-case benchmark]\n"
        "아래 입력은 offline benchmark 전용 payload 입니다. production prompt 가 아닙니다.\n"
        "점수나 지원 판단을 새로 만들지 말고, 사용자가 보유하지 않은 역량을 보유로 단정하지 마세요.\n"
        "B variant 의 evidenceBuckets 는 userEvidence/jobRequirements/catalogFacts/companyContext 로 분리되어 있습니다.\n"
        "userEvidence 에 있는 항목만 사용자 보유 근거로 볼 수 있습니다.\n"
        "JSON 객체만 반환하세요.\n\n"
        + json.dumps(payload, ensure_ascii=False, indent=2)
    )


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def try_parse_json(raw_output: str) -> tuple[bool | None, Any | None]:
    if not raw_output:
        return None, None
    try:
        return True, json.loads(raw_output)
    except json.JSONDecodeError:
        return False, None


def call_ollama(base_url: str, model: str, prompt: str, timeout_seconds: int) -> str:
    url = base_url.rstrip("/") + "/api/generate"
    body = json.dumps({"model": model, "prompt": prompt, "stream": False}, ensure_ascii=False).encode("utf-8")
    request = Request(url, data=body, headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Ollama HTTP {exc.code}: {detail}") from exc
    except URLError as exc:
        raise RuntimeError(f"Ollama connection failed: {exc}") from exc
    response_text = payload.get("response")
    if not isinstance(response_text, str):
        raise RuntimeError("Ollama response missing string field 'response'")
    return response_text


def result_payload(payload: dict[str, Any],
                   provider: str,
                   model: str | None,
                   base_url: str | None,
                   latency_ms: int | None,
                   raw_output_path: str | None,
                   raw_output: str | None,
                   error: str | None) -> dict[str, Any]:
    json_parse_success, parsed_output = try_parse_json(raw_output or "")
    metrics = dict(METRIC_SCHEMA)
    metrics["json_parse_success"] = json_parse_success
    metrics["latency_ms"] = latency_ms
    metrics["output_length"] = len(raw_output) if raw_output is not None else None
    return {
        "caseId": payload["caseId"],
        "category": payload["category"],
        "variant": payload["variant"],
        "model": model,
        "provider": provider,
        "baseUrl": base_url,
        "latencyMs": latency_ms,
        "rawOutputPath": raw_output_path,
        "rawOutputExists": raw_output is not None,
        "error": error,
        "parsedOutput": parsed_output if json_parse_success else None,
        "metrics": metrics,
        "gateResult": {
            "gateStatus": None,
            "reasonCount": None,
            "maxSeverity": None,
            "reasons": [],
        },
    }


def run(fixture: Path,
        out_dir: Path,
        dry_run: bool,
        provider: str | None,
        base_url: str | None,
        model: str | None,
        timeout_seconds: int,
        continue_on_error: bool) -> dict[str, Any]:
    assert_output_path_allowed(out_dir)
    rows = load_jsonl(fixture)
    errors = validate_rows(rows)
    if errors:
        raise SystemExit("fixture validation failed:\n" + "\n".join(f" - {error}" for error in errors))

    requests_dir = out_dir / "requests"
    outputs_dir = out_dir / "outputs"
    results_dir = out_dir / "results"
    active_provider = "dry-run" if dry_run else provider
    manifest = {
        "fixture": str(fixture),
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "dryRun": dry_run,
        "provider": active_provider,
        "model": model,
        "baseUrl": base_url,
        "outputPolicy": output_policy(out_dir),
        "modelCalls": 0,
        "variants": ["A_lora_only", "B_structured_evidence_buckets"],
        "metricsSchema": dict(METRIC_SCHEMA),
        "cases": [],
    }

    for row in rows:
        case_id = row["caseId"]
        category = row["category"]
        a_payload = variant_payload(row, "A_lora_only")
        b_payload = variant_payload(row, "B_structured_evidence_buckets")
        request_paths: list[str] = []
        result_paths: list[str] = []
        output_paths: list[str] = []
        for payload in (a_payload, b_payload):
            variant = payload["variant"]
            stem = f"{case_id}_{variant}"
            request_path = requests_dir / f"{stem}.json"
            raw_path = outputs_dir / f"{stem}.raw.txt"
            result_path = results_dir / f"{stem}.result.json"
            write_json(request_path, payload)
            request_paths.append(str(request_path))

            raw_output: str | None = None
            latency_ms: int | None = None
            error: str | None = None
            raw_output_rel: str | None = None
            if not dry_run:
                assert provider == "ollama"
                assert base_url is not None
                assert model is not None
                started = time.perf_counter()
                try:
                    raw_output = call_ollama(base_url, model, benchmark_prompt(payload), timeout_seconds)
                    latency_ms = int((time.perf_counter() - started) * 1000)
                    raw_path.parent.mkdir(parents=True, exist_ok=True)
                    raw_path.write_text(raw_output, encoding="utf-8")
                    raw_output_rel = str(raw_path.relative_to(out_dir))
                    output_paths.append(str(raw_path))
                    manifest["modelCalls"] += 1
                except Exception as exc:  # noqa: BLE001 - record per-variant failure
                    latency_ms = int((time.perf_counter() - started) * 1000)
                    error = str(exc)
                    if not continue_on_error:
                        write_json(result_path, result_payload(
                            payload, provider, model, base_url, latency_ms, None, None, error))
                        raise SystemExit(f"[{case_id} {variant}] {error}") from exc

            write_json(result_path, result_payload(
                payload=payload,
                provider=active_provider or "dry-run",
                model=model,
                base_url=base_url,
                latency_ms=latency_ms,
                raw_output_path=raw_output_rel,
                raw_output=raw_output,
                error=error,
            ))
            result_paths.append(str(result_path))

        manifest["cases"].append({
            "caseId": case_id,
            "category": category,
            "requests": request_paths,
            "outputs": output_paths,
            "results": result_paths,
        })

    write_json(out_dir / "benchmark_manifest.json", manifest)
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--dry-run", action="store_true", help="Generate payloads only; do not call any model.")
    parser.add_argument("--provider", choices=["ollama"], help="Offline model provider. Only Ollama is supported.")
    parser.add_argument("--base-url", default="http://127.0.0.1:11434")
    parser.add_argument("--model")
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument("--continue-on-error", action="store_true")
    parser.add_argument("--allow-remote", action="store_true",
                        help="Allow non-local/private Ollama base-url. Off by default.")
    args = parser.parse_args(argv)

    if args.dry_run:
        provider = None
        base_url = None
        model = args.model
    else:
        if args.provider != "ollama":
            raise SystemExit("non-dry-run requires --provider ollama")
        if not args.model:
            raise SystemExit("non-dry-run requires --model")
        if not args.allow_remote and not is_allowed_base_url(args.base_url):
            raise SystemExit(f"Refusing non-local/private base-url without --allow-remote: {args.base_url}")
        provider = args.provider
        base_url = args.base_url
        model = args.model
        print("[run_rag_hardcase_benchmark] offline model run")
        print(f"  provider={provider}")
        print(f"  model={model}")
        print(f"  baseUrl={base_url}")
        print(f"  out={args.out}")

    manifest = run(
        fixture=args.fixture,
        out_dir=args.out,
        dry_run=args.dry_run,
        provider=provider,
        base_url=base_url,
        model=model,
        timeout_seconds=args.timeout_seconds,
        continue_on_error=args.continue_on_error,
    )
    print("[run_rag_hardcase_benchmark]")
    print(f"  fixture={args.fixture}")
    print(f"  out={args.out}")
    print(f"  cases={len(manifest['cases'])} variants=2 modelCalls={manifest['modelCalls']} dryRun={args.dry_run}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

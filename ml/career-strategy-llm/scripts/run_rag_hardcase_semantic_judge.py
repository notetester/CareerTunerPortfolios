"""Run an independent semantic judge over RAG hard-case judge packets.

Supported providers:
  - none: validate packets and emit UNCLEAR placeholder results
  - manual: emit manual-review placeholder results
  - ollama: call a local/private Ollama endpoint only

The runner never calls OpenAI, Claude, or the production backend.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
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

REPO_ROOT = SCRIPT_DIR.parents[2].resolve()
ML_ROOT = SCRIPT_DIR.parent.resolve()
GENERATED_ROOT = (ML_ROOT / "reports" / "generated").resolve()
CAREERTUNER_AI_ROOT = Path("D:/dev/CareerTunerAI").resolve()

ALLOWED_LABELS = {"PASS", "UNSUPPORTED_POSSESSION_CLAIM", "UNCLEAR", "JUDGE_ERROR"}
COMPARABLE_LABELS = {"PASS", "UNSUPPORTED_POSSESSION_CLAIM"}


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


def assert_output_path_allowed(path: Path) -> None:
    resolved = path.resolve()
    if resolved.is_relative_to(REPO_ROOT) and not resolved.is_relative_to(GENERATED_ROOT):
        raise SystemExit(
            "Judge results must not be written into the CareerTuner main repo "
            f"outside reports/generated/: {resolved}"
        )


def output_policy(path: Path) -> str:
    resolved = path.resolve()
    if resolved.is_relative_to(CAREERTUNER_AI_ROOT):
        return "careertuner-ai-artifact"
    if resolved.is_relative_to(GENERATED_ROOT):
        return "career-tuner-generated-ignore"
    return "external-local-output"


def load_packets(path: Path) -> list[dict[str, Any]]:
    packets: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line_no, raw_line in enumerate(handle, 1):
            line = raw_line.strip()
            if not line:
                continue
            row = json.loads(line)
            if not isinstance(row, dict):
                raise ValueError(f"{path}:{line_no} must be an object")
            validate_packet(row, line_no)
            packets.append(row)
    return packets


def validate_packet(packet: dict[str, Any], line_no: int) -> None:
    required = [
        "caseId",
        "category",
        "variant",
        "expectedMustNotClaimOwned",
        "allowedOwned",
        "jobRequiredSkills",
        "profileSkills",
        "offlineEvaluation",
        "judgeTask",
    ]
    for key in required:
        if key not in packet:
            raise ValueError(f"packet line {line_no} missing {key}")
    if not isinstance(packet["expectedMustNotClaimOwned"], list):
        raise ValueError(f"packet line {line_no} expectedMustNotClaimOwned must be a list")
    labels = set((packet.get("judgeTask") or {}).get("labels") or [])
    if not {"PASS", "UNSUPPORTED_POSSESSION_CLAIM", "UNCLEAR"}.issubset(labels):
        raise ValueError(f"packet line {line_no} judgeTask.labels is incomplete")


def safe_stem(packet: dict[str, Any]) -> str:
    raw = f"{packet['caseId']}_{packet['variant']}"
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", raw)


def prompt_for(packet: dict[str, Any]) -> str:
    judge_payload = {
        "caseId": packet["caseId"],
        "category": packet["category"],
        "variant": packet["variant"],
        "expectedMustNotClaimOwned": packet.get("expectedMustNotClaimOwned") or [],
        "allowedOwned": packet.get("allowedOwned") or [],
        "knownMissingSkills": packet.get("knownMissingSkills") or [],
        "jobRequiredSkills": packet.get("jobRequiredSkills") or [],
        "jobPreferredSkills": packet.get("jobPreferredSkills") or [],
        "profileSkills": packet.get("profileSkills") or [],
        "profileCertificates": packet.get("profileCertificates") or [],
        "profileExperienceSummary": packet.get("profileExperienceSummary"),
        "textForSemanticJudgment": packet.get("textForSemanticJudgment") or "",
    }
    return (
        "You are an independent semantic judge for a synthetic RAG hard-case benchmark.\n"
        "Your task: decide whether the model output claims or strongly implies that the user owns "
        "any item in expectedMustNotClaimOwned.\n\n"
        "Rules:\n"
        "- Use textForSemanticJudgment as primary evidence.\n"
        "- The packet may include expected forbidden skills and job requirements, but those are not output claims.\n"
        "- Mentioning a job requirement is not an ownership claim.\n"
        "- Saying a skill is missing, needs verification, needs learning, or is required is PASS.\n"
        "- Korean phrases such as '사용자 프로필에 없어', '보유 여부를 확인해야 합니다', "
        "'검증할 필요가 있습니다', or '인정하기 어렵습니다' are PASS, not unsupported ownership claims.\n"
        "- English/Chinese negative statements such as 'the user does not have X', "
        "'X is not found in the profile', or '用户不拥有X' are PASS.\n"
        "- Only positive ownership statements are unsafe, such as 'the user has X', "
        "'X experience is a strength', 'X 보유', or 'X 경험이 있습니다'.\n"
        "- Do not put a skill in unsupportedClaims when the output says the user lacks it or must verify it.\n"
        "- If there is no human-facing claim at all, label PASS and explain that no claim is present.\n"
        "- Use UNSUPPORTED_POSSESSION_CLAIM only when the output says or strongly implies that the "
        "user has the forbidden skill/certificate.\n"
        "- Use UNCLEAR only if the text is genuinely ambiguous after applying the rules.\n\n"
        "Return only this JSON object, no markdown:\n"
        "{\"label\":\"PASS|UNSUPPORTED_POSSESSION_CLAIM|UNCLEAR\","
        "\"unsupportedClaims\":[\"...\"],\"reason\":\"short reason\","
        "\"confidence\":0.0}\n\n"
        "Packet:\n"
        f"{json.dumps(judge_payload, ensure_ascii=False, indent=2)}"
    )


def call_ollama(base_url: str, model: str, prompt: str, timeout_seconds: int) -> str:
    url = base_url.rstrip("/") + "/api/generate"
    body = json.dumps(
        {
            "model": model,
            "prompt": prompt,
            "stream": False,
            "format": "json",
            "options": {"temperature": 0},
        },
        ensure_ascii=False,
    ).encode("utf-8")
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


def extract_json_object(raw_text: str) -> dict[str, Any]:
    try:
        value = json.loads(raw_text)
        if isinstance(value, dict):
            return value
    except json.JSONDecodeError:
        pass
    match = re.search(r"\{.*\}", raw_text, flags=re.DOTALL)
    if not match:
        raise ValueError("judge response did not contain a JSON object")
    value = json.loads(match.group(0))
    if not isinstance(value, dict):
        raise ValueError("judge response JSON is not an object")
    return value


def normalize_claims(value: Any) -> list[str]:
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    if isinstance(value, str) and value.strip():
        return [value.strip()]
    return []


def normalize_confidence(value: Any) -> float:
    if isinstance(value, (int, float)):
        return max(0.0, min(1.0, float(value)))
    try:
        return max(0.0, min(1.0, float(str(value))))
    except (TypeError, ValueError):
        return 0.0


def normalize_judge_payload(raw_payload: dict[str, Any]) -> dict[str, Any]:
    label = str(raw_payload.get("label") or "").strip().upper()
    claims = normalize_claims(raw_payload.get("unsupportedClaims"))
    if label not in ALLOWED_LABELS:
        label = "UNSUPPORTED_POSSESSION_CLAIM" if claims else "UNCLEAR"
    if label == "PASS":
        claims = []
    return {
        "label": label,
        "unsupportedClaims": claims,
        "reason": str(raw_payload.get("reason") or "").strip(),
        "confidence": normalize_confidence(raw_payload.get("confidence")),
    }


def placeholder_result(packet: dict[str, Any],
                       provider: str,
                       model: str | None,
                       reason: str,
                       error: str | None = None) -> dict[str, Any]:
    return {
        "caseId": packet["caseId"],
        "category": packet["category"],
        "variant": packet["variant"],
        "provider": provider,
        "model": model,
        "label": "UNCLEAR",
        "unsupportedClaims": [],
        "reason": reason,
        "confidence": 0.0,
        "rawJudgeOutputPath": None,
        "error": error,
        "offlineEvaluation": packet.get("offlineEvaluation") or {},
        "sourceResult": packet.get("sourceResult") or {},
    }


def result_from_error(packet: dict[str, Any],
                      provider: str,
                      model: str | None,
                      raw_rel: str | None,
                      error: str) -> dict[str, Any]:
    return {
        "caseId": packet["caseId"],
        "category": packet["category"],
        "variant": packet["variant"],
        "provider": provider,
        "model": model,
        "label": "JUDGE_ERROR",
        "unsupportedClaims": [],
        "reason": "judge execution failed",
        "confidence": 0.0,
        "rawJudgeOutputPath": raw_rel,
        "error": error,
        "offlineEvaluation": packet.get("offlineEvaluation") or {},
        "sourceResult": packet.get("sourceResult") or {},
    }


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def run(packets_path: Path,
        out_dir: Path,
        provider: str,
        base_url: str | None,
        model: str | None,
        timeout_seconds: int,
        continue_on_error: bool) -> dict[str, Any]:
    assert_output_path_allowed(out_dir)
    packets = load_packets(packets_path)
    raw_dir = out_dir / "raw"
    results_dir = out_dir / "results"
    result_count = 0
    judge_error_count = 0
    model_calls = 0

    for packet in packets:
        stem = safe_stem(packet)
        result_path = results_dir / f"{stem}.judge.result.json"
        if provider == "none":
            result = placeholder_result(
                packet,
                provider,
                model,
                "provider=none validates packet schema only; no semantic judge call was made",
            )
            write_json(result_path, result)
            result_count += 1
            continue
        if provider == "manual":
            result = placeholder_result(
                packet,
                provider,
                model,
                "manual provider placeholder; fill this result from an external/manual review",
            )
            write_json(result_path, result)
            result_count += 1
            continue

        assert provider == "ollama"
        assert base_url is not None
        assert model is not None
        raw_path = raw_dir / f"{stem}.judge.raw.txt"
        raw_rel = str(raw_path.relative_to(out_dir))
        started = time.perf_counter()
        try:
            raw_text = call_ollama(base_url, model, prompt_for(packet), timeout_seconds)
            latency_ms = int((time.perf_counter() - started) * 1000)
            raw_path.parent.mkdir(parents=True, exist_ok=True)
            raw_path.write_text(raw_text, encoding="utf-8")
            parsed = normalize_judge_payload(extract_json_object(raw_text))
            result = {
                "caseId": packet["caseId"],
                "category": packet["category"],
                "variant": packet["variant"],
                "provider": provider,
                "model": model,
                "label": parsed["label"],
                "unsupportedClaims": parsed["unsupportedClaims"],
                "reason": parsed["reason"],
                "confidence": parsed["confidence"],
                "latencyMs": latency_ms,
                "rawJudgeOutputPath": raw_rel,
                "error": None,
                "offlineEvaluation": packet.get("offlineEvaluation") or {},
                "sourceResult": packet.get("sourceResult") or {},
            }
            model_calls += 1
        except Exception as exc:  # noqa: BLE001 - preserve per-case failure for review
            error = str(exc)
            judge_error_count += 1
            result = result_from_error(packet, provider, model, raw_rel, error)
            if not continue_on_error:
                write_json(result_path, result)
                raise SystemExit(f"[{packet['caseId']} {packet['variant']}] {error}") from exc
        write_json(result_path, result)
        result_count += 1

    manifest = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "packets": str(packets_path),
        "output": str(out_dir),
        "outputPolicy": output_policy(out_dir),
        "provider": provider,
        "model": model,
        "baseUrl": base_url,
        "resultCount": result_count,
        "modelCalls": model_calls,
        "judgeErrorCount": judge_error_count,
        "allowedLabels": sorted(ALLOWED_LABELS),
        "notes": [
            "No external OpenAI/Claude API was called",
            "offlineEvaluation is carried for comparison only and is not used as the judge verdict",
        ],
    }
    write_json(out_dir / "judge_manifest.json", manifest)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--packets", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--provider", required=True, choices=["none", "ollama", "manual"])
    parser.add_argument("--base-url", default="http://127.0.0.1:11434")
    parser.add_argument("--model")
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument("--continue-on-error", action="store_true")
    parser.add_argument("--allow-remote", action="store_true",
                        help="Allow non-local/private Ollama base-url. Off by default.")
    args = parser.parse_args()

    base_url: str | None = None
    model: str | None = args.model
    if args.provider == "ollama":
        if not args.model:
            raise SystemExit("--provider ollama requires --model")
        if not args.allow_remote and not is_allowed_base_url(args.base_url):
            raise SystemExit(f"Refusing non-local/private base-url without --allow-remote: {args.base_url}")
        base_url = args.base_url
    elif args.model:
        model = args.model

    manifest = run(
        packets_path=args.packets,
        out_dir=args.out,
        provider=args.provider,
        base_url=base_url,
        model=model,
        timeout_seconds=args.timeout_seconds,
        continue_on_error=args.continue_on_error,
    )
    print("[run_rag_hardcase_semantic_judge]")
    print(f"  packets={args.packets}")
    print(f"  out={args.out}")
    print(f"  provider={args.provider} model={model}")
    print(
        f"  resultCount={manifest['resultCount']} "
        f"modelCalls={manifest['modelCalls']} judgeErrorCount={manifest['judgeErrorCount']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

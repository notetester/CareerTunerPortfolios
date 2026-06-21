"""
C_FIT_EXPLAIN 자체모델 평가 하니스 — '서비스 계약' 기준 정량 측정.

C 모델은 점수를 만들지 않으므로(뉴로-심볼릭), 평가는 점수 정답이 아니라
**설명 JSON 계약 위반 여부**를 측정한다:
  JSON 파싱 / 필수키 / 금지키 / 중국어·일본어 누출 / mustMention·mustNotMention /
  forbiddenClaims(합격 보장 등) / 허용 밖 스킬 언급(환각) / 지연(latency).

system 프롬프트는 학습/서빙과 동일한 synth_prompts.FIT_EXPLAIN_SYS,
user 프롬프트는 assemble_dataset.build_fit_user 를 재사용한다(train/serve/eval skew 방지).

사용:
  python scripts/eval_fit_model.py \
    --cases eval/golden_fit_cases.jsonl \
    --base-url http://localhost:11434/v1 \
    --model careertuner-c-career-strategy-3b \
    --out out/eval/c-fit-3b-eval.json

  # 모델 없이 파이프라인만 점검(드라이런):
  python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl --mock --out out/eval/mock.json
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

from synth_prompts import FIT_EXPLAIN_SYS          # noqa: E402
from assemble_dataset import build_fit_user        # noqa: E402

# 중국어/일본어 누출 탐지: 일본어 가나 + CJK 한자(Ext-A/통합). 한국어(한글)는 제외.
CJK_RE = re.compile(r"[぀-ヿ㐀-䶿一-鿿]")
REQUIRED_KEYS = ["fitSummary", "strengths", "risks", "strategyActions", "learningTaskReasons"]
FORBIDDEN_KEYS = ["fitScore", "score", "applyDecision", "decision"]


def load_cases(path):
    cases = []
    with open(path, encoding="utf-8") as f:
        for ln, line in enumerate(f, 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            try:
                cases.append(json.loads(line))
            except json.JSONDecodeError as e:
                raise SystemExit(f"골든셋 {path}:{ln} JSON 오류: {e}")
    return cases


def extract_json_span(text):
    """백엔드 CareerAnalysisOssClient.extractJsonSpan 미러 — 첫 {/[ 부터 마지막 }/] 까지."""
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text).strip()
    obj, arr = text.find("{"), text.find("[")
    start = obj if arr < 0 else (arr if obj < 0 else min(obj, arr))
    end = max(text.rfind("}"), text.rfind("]"))
    return text[start:end + 1] if (start >= 0 and end > start) else text


def collect_text(parsed):
    """검사 대상 텍스트(설명 필드 전체)를 한 덩어리로."""
    parts = []
    for k in ("fitSummary",):
        v = parsed.get(k)
        if isinstance(v, str):
            parts.append(v)
    for k in ("strengths", "risks", "strategyActions"):
        v = parsed.get(k)
        if isinstance(v, list):
            parts += [str(x) for x in v]
    for item in parsed.get("learningTaskReasons", []) or []:
        if isinstance(item, dict):
            parts += [str(item.get("skill", "")), str(item.get("why", ""))]
    return "\n".join(parts)


def call_model(base_url, model, user, max_tokens, temperature, timeout):
    url = base_url.rstrip("/") + "/chat/completions"
    payload = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": FIT_EXPLAIN_SYS},
            {"role": "user", "content": user},
        ],
        "temperature": temperature,
        "max_tokens": max_tokens,
        "response_format": {"type": "json_object"},
    }).encode("utf-8")
    req = urllib.request.Request(url, data=payload,
                                headers={"Content-Type": "application/json"}, method="POST")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = r.read().decode("utf-8")
        latency = (time.perf_counter() - t0) * 1000
        root = json.loads(body)
        content = root.get("choices", [{}])[0].get("message", {}).get("content", "")
        return content, latency, None
    except urllib.error.HTTPError as e:
        return "", (time.perf_counter() - t0) * 1000, f"HTTP_{e.code}"
    except Exception as e:  # noqa: BLE001 (네트워크/타임아웃 등 광범위 — 실패 사유로 기록)
        return "", (time.perf_counter() - t0) * 1000, f"ERROR_{type(e).__name__}"


def mock_content(case):
    """모델 없이 파이프라인 점검용 — 계약을 만족하는 가짜 응답(드라이런)."""
    exp = case.get("expected", {})
    allowed = exp.get("allowedSkills") or ["기초 역량"]
    summary = "규칙엔진 점수와 부족역량을 근거로 한 설명입니다. " + " ".join(exp.get("mustMention") or [])
    return json.dumps({
        "fitSummary": summary.strip(),
        "strengths": ["보유 역량을 근거로 한 강점"],
        "risks": ["부족 필수역량에 따른 위험"],
        "strategyActions": ["부족역량을 학습으로 보완한 뒤 재분석"],
        "learningTaskReasons": [{"skill": allowed[0], "why": "공고 필수 역량이라 우선 보완"}],
    }, ensure_ascii=False)


def evaluate(case, content, error):
    exp = case.get("expected", {})
    res = {"id": case.get("id"), "domainGroup": case.get("domainGroup"),
           "expectedDecision": case.get("expectedDecision")}
    failures = []

    if error:
        return {**res, "failure": error, "json_ok": False, "required_ok": False,
                "forbidden_key": False, "cjk_leak": False, "hallucination": False, "success": False}

    span = extract_json_span(content or "")
    if not span.strip():
        return {**res, "failure": "EMPTY", "json_ok": False, "required_ok": False,
                "forbidden_key": False, "cjk_leak": False, "hallucination": False, "success": False}

    try:
        parsed = json.loads(span)
    except json.JSONDecodeError:
        return {**res, "failure": "PARSE_FAIL", "json_ok": False, "required_ok": False,
                "forbidden_key": False, "cjk_leak": False, "hallucination": False, "success": False}
    if not isinstance(parsed, dict):
        return {**res, "failure": "NOT_OBJECT", "json_ok": False, "required_ok": False,
                "forbidden_key": False, "cjk_leak": False, "hallucination": False, "success": False}

    req_keys = exp.get("requiredKeys") or REQUIRED_KEYS
    forb_keys = exp.get("forbiddenKeys") or FORBIDDEN_KEYS
    text = collect_text(parsed)

    missing_keys = [k for k in req_keys if k not in parsed]
    forbidden_hit = [k for k in forb_keys if k in parsed]
    cjk = bool(CJK_RE.search(text))
    must_missing = [m for m in (exp.get("mustMention") or []) if m not in text]
    must_not_hit = [m for m in (exp.get("mustNotMention") or []) if m in text]
    claim_hit = [c for c in (exp.get("forbiddenClaims") or []) if c in text]
    allowed = exp.get("allowedSkills") or []
    bad_skills = []
    if allowed:
        for item in parsed.get("learningTaskReasons", []) or []:
            sk = (item or {}).get("skill")
            if sk and sk not in allowed:
                bad_skills.append(sk)

    if missing_keys:
        failures.append("MISSING_REQUIRED_KEY")
    if forbidden_hit:
        failures.append("FORBIDDEN_KEY")
    if cjk:
        failures.append("CJK_LEAK")
    if claim_hit:
        failures.append("FORBIDDEN_CLAIM")
    if must_missing:
        failures.append("MISSING_MUST_MENTION")
    if must_not_hit:
        failures.append("FORBIDDEN_MENTION")
    if bad_skills:
        failures.append("HALLUCINATED_SKILL")

    hallucination = bool(claim_hit or must_not_hit or bad_skills)
    return {**res,
            "json_ok": True, "required_ok": not missing_keys,
            "forbidden_key": bool(forbidden_hit), "cjk_leak": cjk,
            "hallucination": hallucination,
            "failure": failures[0] if failures else None,
            "detail": {"missing_keys": missing_keys, "forbidden_hit": forbidden_hit,
                       "must_missing": must_missing, "must_not_hit": must_not_hit,
                       "claim_hit": claim_hit, "bad_skills": bad_skills},
            "success": not failures}


def percentile(values, p):
    if not values:
        return 0.0
    s = sorted(values)
    k = max(0, min(len(s) - 1, int(round((p / 100.0) * (len(s) - 1)))))
    return round(s[k], 1)


def aggregate(results, model, base_url):
    n = len(results)
    lat = [r["latency_ms"] for r in results if r.get("latency_ms") is not None]
    reasons = {}
    for r in results:
        if r.get("failure"):
            reasons[r["failure"]] = reasons.get(r["failure"], 0) + 1

    def rate(pred):
        return round(sum(1 for r in results if pred(r)) / n, 3) if n else 0.0

    return {
        "model": model, "base_url": base_url, "total_cases": n,
        "success_count": sum(1 for r in results if r.get("success")),
        "json_parse_rate": rate(lambda r: r.get("json_ok")),
        "required_key_rate": rate(lambda r: r.get("required_ok")),
        "forbidden_key_rate": rate(lambda r: r.get("forbidden_key")),
        "cjk_leak_rate": rate(lambda r: r.get("cjk_leak")),
        "hallucination_flag_rate": rate(lambda r: r.get("hallucination")),
        "avg_latency_ms": round(sum(lat) / len(lat), 1) if lat else 0.0,
        "p95_latency_ms": percentile(lat, 95),
        "failure_reasons": dict(sorted(reasons.items(), key=lambda x: -x[1])),
    }


def main():
    ap = argparse.ArgumentParser(description="C_FIT_EXPLAIN 자체모델 평가 하니스(계약 위반 측정)")
    ap.add_argument("--cases", required=True, help="골든셋 JSONL (eval/golden_fit_cases.jsonl)")
    ap.add_argument("--base-url", default="http://localhost:11434/v1", help="OpenAI 호환 엔드포인트")
    ap.add_argument("--model", default="careertuner-c-career-strategy-3b")
    ap.add_argument("--out", default="out/eval/c-fit-3b-eval.json")
    ap.add_argument("--max-tokens", type=int, default=1280)
    ap.add_argument("--temperature", type=float, default=0.2)
    ap.add_argument("--timeout", type=float, default=120.0)
    ap.add_argument("--limit", type=int, default=0, help="앞에서 N건만(0=전체)")
    ap.add_argument("--mock", action="store_true", help="모델 호출 없이 가짜 응답으로 파이프라인만 점검")
    args = ap.parse_args()

    cases = load_cases(args.cases)
    if args.limit > 0:
        cases = cases[:args.limit]
    if not cases:
        raise SystemExit("골든셋 케이스가 없습니다.")

    results = []
    for case in cases:
        user = build_fit_user(case.get("input") or {})
        if args.mock:
            content, latency, error = mock_content(case), 0.0, None
        else:
            content, latency, error = call_model(
                args.base_url, args.model, user, args.max_tokens, args.temperature, args.timeout)
        row = evaluate(case, content, error)
        row["latency_ms"] = round(latency, 1)
        results.append(row)

    summary = aggregate(results, args.model, args.base_url)
    summary["mock"] = bool(args.mock)
    report = {"summary": summary, "cases": results}

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    s = summary
    print(f"[eval] model={s['model']} mock={s['mock']} cases={s['total_cases']} "
          f"success={s['success_count']}/{s['total_cases']}")
    print(f"  json_parse_rate={s['json_parse_rate']} required_key_rate={s['required_key_rate']} "
          f"forbidden_key_rate={s['forbidden_key_rate']} cjk_leak_rate={s['cjk_leak_rate']} "
          f"hallucination_flag_rate={s['hallucination_flag_rate']}")
    print(f"  avg_latency_ms={s['avg_latency_ms']} p95_latency_ms={s['p95_latency_ms']} "
          f"failure_reasons={s['failure_reasons']}")
    print(f"  → {args.out}")


if __name__ == "__main__":
    main()

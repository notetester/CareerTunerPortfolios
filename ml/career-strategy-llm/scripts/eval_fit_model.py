"""
C_FIT_EXPLAIN 자체모델 평가 하니스 v2 — '서비스 계약' 측정 + raw output 저장(설명 품질 pairwise 준비).

C 모델은 점수를 만들지 않으므로(뉴로-심볼릭), 평가는 점수 정답이 아니라
**설명 JSON 계약 위반 여부**를 측정한다(파싱/필수키/금지키/CJK 누출/mustMention·mustNotMention/
forbiddenClaims/허용밖 스킬/지연). v2 는 추가로:
  - raw_output·parsed·user_prompt·prompt_hash·output_text_length 저장(--save-raw)
  - cold-start 제거(--warmup N) 및 cold/warm latency 분리 리포트
  - stochastic 실패율(--repeat N)
  - pairwise 비교 입력 파일 생성(--pairwise)

system 은 synth_prompts.FIT_EXPLAIN_SYS, user 는 assemble_dataset.build_fit_user 재사용
(train/serve/eval skew 방지).

평가:
  python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl \
    --base-url http://localhost:11434/v1 --model careertuner-c-career-strategy-3b \
    --out out/eval/c-fit-3b-eval-v2.json --save-raw --warmup 1 --repeat 3 --timeout 180
pairwise 입력 생성:
  python scripts/eval_fit_model.py --pairwise \
    --lora-result out/eval/c-fit-3b-eval-v2.json --base-result out/eval/c-fit-3b-base-eval-v2.json \
    --pairwise-out out/eval/c-fit-3b-pairwise-input.json
드라이런(모델 없이):
  python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl --mock --save-raw --repeat 2 --out out/eval/mock-v2.json
"""
import argparse
import hashlib
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
RAW_MAX = 8000  # raw_output 저장 시 상한(폭주 방지)


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
    parts = []
    v = parsed.get("fitSummary")
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
        usage = root.get("usage")
        return content, latency, None, usage
    except urllib.error.HTTPError as e:
        return "", (time.perf_counter() - t0) * 1000, f"HTTP_{e.code}", None
    except Exception as e:  # noqa: BLE001
        return "", (time.perf_counter() - t0) * 1000, f"ERROR_{type(e).__name__}", None


def mock_content(case):
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
    base = {"id": case.get("id"), "domainGroup": case.get("domainGroup"),
            "expectedDecision": case.get("expectedDecision")}
    fail = {"json_ok": False, "required_ok": False, "forbidden_key": False,
            "cjk_leak": False, "hallucination": False, "success": False}

    if error:
        return {**base, **fail, "failure": error, "parsed": None}
    span = extract_json_span(content or "")
    if not span.strip():
        return {**base, **fail, "failure": "EMPTY", "parsed": None}
    try:
        parsed = json.loads(span)
    except json.JSONDecodeError:
        return {**base, **fail, "failure": "PARSE_FAIL", "parsed": None}
    if not isinstance(parsed, dict):
        return {**base, **fail, "failure": "NOT_OBJECT", "parsed": None}

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

    failures = []
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

    return {**base, "json_ok": True, "required_ok": not missing_keys,
            "forbidden_key": bool(forbidden_hit), "cjk_leak": cjk,
            "hallucination": bool(claim_hit or must_not_hit or bad_skills),
            "failure": failures[0] if failures else None,
            "detail": {"missing_keys": missing_keys, "forbidden_hit": forbidden_hit,
                       "must_missing": must_missing, "must_not_hit": must_not_hit,
                       "claim_hit": claim_hit, "bad_skills": bad_skills},
            "success": not failures, "parsed": parsed}


def percentile(values, p):
    if not values:
        return 0.0
    s = sorted(values)
    k = max(0, min(len(s) - 1, int(round((p / 100.0) * (len(s) - 1)))))
    return round(s[k], 1)


def aggregate(results, cold_start_ms, args):
    n = len(results)
    all_lat = [r["latency_ms"] for r in results]
    # warm = warmup 했으면 전부 warm. 안 했으면 첫 케이스(run0 첫 건) 제외.
    warm_lat = all_lat if args.warmup > 0 else all_lat[1:]

    def rate(pred):
        return round(sum(1 for r in results if pred(r)) / n, 3) if n else 0.0
    reasons = {}
    for r in results:
        if r.get("failure"):
            reasons[r["failure"]] = reasons.get(r["failure"], 0) + 1
    return {
        "model": args.model, "base_url": args.base_url, "mock": bool(args.mock),
        "warmup": args.warmup, "repeat": args.repeat, "timeout_s": args.timeout,
        "total_runs": n, "cases": n // max(1, args.repeat),
        "success_count": sum(1 for r in results if r.get("success")),
        "success_rate": rate(lambda r: r.get("success")),
        "json_parse_rate": rate(lambda r: r.get("json_ok")),
        "required_key_rate": rate(lambda r: r.get("required_ok")),
        "forbidden_key_rate": rate(lambda r: r.get("forbidden_key")),
        "cjk_leak_rate": rate(lambda r: r.get("cjk_leak")),
        "hallucination_flag_rate": rate(lambda r: r.get("hallucination")),
        "timeout_count": reasons.get("ERROR_TimeoutError", 0),
        "cold_start_latency_ms": round(cold_start_ms, 1),
        "warm_avg_latency_ms": round(sum(warm_lat) / len(warm_lat), 1) if warm_lat else 0.0,
        "warm_p95_latency_ms": percentile(warm_lat, 95),
        "avg_latency_ms": round(sum(all_lat) / len(all_lat), 1) if all_lat else 0.0,
        "failure_reasons": dict(sorted(reasons.items(), key=lambda x: -x[1])),
    }


def run_eval(args):
    cases = load_cases(args.cases)
    if args.limit > 0:
        cases = cases[:args.limit]
    if not cases:
        raise SystemExit("골든셋 케이스가 없습니다.")

    cold_start_ms = 0.0
    if args.warmup > 0 and not args.mock:
        print(f"[warmup] {args.warmup}회 더미 호출(cold-start 제거)...")
        for i in range(args.warmup):
            _, lat, _, _ = call_model(args.base_url, args.model, "준비 확인용 호출입니다. JSON 으로 짧게 답하세요.",
                                      64, args.temperature, args.timeout)
            if i == 0:
                cold_start_ms = lat

    results = []
    for run in range(args.repeat):
        for case in cases:
            user = build_fit_user(case.get("input") or {})
            phash = hashlib.sha256((FIT_EXPLAIN_SYS + "\n" + user).encode("utf-8")).hexdigest()[:12]
            if args.mock:
                content, latency, error, usage = mock_content(case), 0.0, None, None
            else:
                content, latency, error, usage = call_model(
                    args.base_url, args.model, user, args.max_tokens, args.temperature, args.timeout)
            row = evaluate(case, content, error)
            row["run"] = run
            row["latency_ms"] = round(latency, 1)
            row["prompt_hash"] = phash
            row["output_text_length"] = len(content or "")
            if usage:
                row["usage"] = usage
            parsed = row.pop("parsed", None)
            if args.save_raw:
                row["user_prompt"] = user
                row["raw_output"] = (content or "")[:RAW_MAX]
                row["parsed"] = parsed
            results.append(row)

    summary = aggregate(results, cold_start_ms, args)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"summary": summary, "results": results}, f, ensure_ascii=False, indent=2)

    s = summary
    print(f"[eval] model={s['model']} mock={s['mock']} cases={s['cases']} repeat={s['repeat']} "
          f"success={s['success_count']}/{s['total_runs']} ({s['success_rate']})")
    print(f"  json_parse={s['json_parse_rate']} forbidden_key={s['forbidden_key_rate']} "
          f"cjk_leak={s['cjk_leak_rate']} hallucination={s['hallucination_flag_rate']} timeout={s['timeout_count']}")
    print(f"  cold_start={s['cold_start_latency_ms']}ms warm_avg={s['warm_avg_latency_ms']}ms "
          f"warm_p95={s['warm_p95_latency_ms']}ms")
    print(f"  failure_reasons={s['failure_reasons']}  → {args.out}")


def _first_output_by_case(path):
    """결과 파일에서 케이스별 대표(run0) raw_output·user_prompt 추출."""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    out = {}
    for r in data.get("results", []):
        cid = r.get("id")
        if cid in out:
            continue
        out[cid] = {"raw_output": r.get("raw_output"), "user_prompt": r.get("user_prompt"),
                    "domainGroup": r.get("domainGroup"), "expectedDecision": r.get("expectedDecision"),
                    "success": r.get("success"), "failure": r.get("failure")}
    return out, data.get("summary", {})


def build_pairwise(args):
    lora, lsum = _first_output_by_case(args.lora_result)
    base, bsum = _first_output_by_case(args.base_result)
    if not any(v.get("raw_output") for v in lora.values()):
        print("[warn] lora-result 에 raw_output 이 없습니다. --save-raw 로 다시 평가하세요.")
    pairs = []
    for cid in lora:
        if cid not in base:
            continue
        pairs.append({
            "caseId": cid,
            "domainGroup": lora[cid].get("domainGroup"),
            "expectedDecision": lora[cid].get("expectedDecision"),
            "user_prompt": lora[cid].get("user_prompt"),
            "lora_output": lora[cid].get("raw_output"),
            "base_output": base[cid].get("raw_output"),
        })
    payload = {"lora_model": lsum.get("model"), "base_model": bsum.get("model"),
               "axes": ["job_fit_relevance", "specificity", "evidence_grounding",
                        "risk_awareness", "tone", "non_it_domain_fit"],
               "pairs": pairs}
    os.makedirs(os.path.dirname(os.path.abspath(args.pairwise_out)), exist_ok=True)
    with open(args.pairwise_out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f"[pairwise] {len(pairs)}쌍 → {args.pairwise_out}")


def main():
    ap = argparse.ArgumentParser(description="C_FIT_EXPLAIN 자체모델 평가 하니스 v2(계약 측정 + raw output + pairwise)")
    ap.add_argument("--cases", help="골든셋 JSONL (eval/golden_fit_cases.jsonl)")
    ap.add_argument("--base-url", default="http://localhost:11434/v1")
    ap.add_argument("--model", default="careertuner-c-career-strategy-3b")
    ap.add_argument("--out", default="out/eval/c-fit-3b-eval-v2.json")
    ap.add_argument("--max-tokens", type=int, default=1280)
    ap.add_argument("--temperature", type=float, default=0.2)
    ap.add_argument("--timeout", type=float, default=180.0)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--mock", action="store_true", help="모델 없이 가짜 응답으로 파이프라인 점검")
    ap.add_argument("--save-raw", action="store_true", help="raw_output·parsed·user_prompt 저장(품질 pairwise용)")
    ap.add_argument("--warmup", type=int, default=0, help="평가 전 더미 호출 N회(cold-start 제거)")
    ap.add_argument("--repeat", type=int, default=1, help="골든셋 N회 반복(stochastic 실패율)")
    # pairwise 모드
    ap.add_argument("--pairwise", action="store_true", help="두 결과 파일을 케이스별로 묶어 비교 입력 생성")
    ap.add_argument("--lora-result")
    ap.add_argument("--base-result")
    ap.add_argument("--pairwise-out", default="out/eval/c-fit-3b-pairwise-input.json")
    args = ap.parse_args()

    if args.pairwise:
        if not (args.lora_result and args.base_result):
            ap.error("--pairwise 에는 --lora-result 와 --base-result 가 필요합니다.")
        build_pairwise(args)
        return
    if not args.cases:
        ap.error("--cases 가 필요합니다(평가 모드).")
    run_eval(args)


if __name__ == "__main__":
    main()

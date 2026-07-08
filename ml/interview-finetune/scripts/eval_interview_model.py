"""면접 채점 모델 평가 하니스 — interview-3b:q4(Q4_K_M) vs interview-3b(F16) 오프라인 골든 검증용.

골든셋(eval/interview_golden_cases.jsonl)을 읽어 케이스마다:
  system = InterviewPromptCatalog.EVALUATION_SYSTEM_PROMPT
  user   = OssAnswerEvaluator.evaluateAnswer 의 user 프롬프트와 '완전히 동일한 모양'
을 조립하고 {--base-url}/chat/completions 로 POST(temperature=0, response_format json_object) 한다.
응답 content 는 백엔드 extractJsonSpan(첫 {/[ ~ 마지막 }/]) 미러 + clampScore(0..100) 로 파싱한다.

per-case 로 {id, model, parsed_score, json_ok, cjk_leak, latency_ms, raw} 를 남긴다.

live A/B 는 GPU 게이트(양 Ollama 호스트 DOWN)라 라이브 호출 없이 --mock 로 파이프라인을 증명한다.

  # 라이브(참고 — 호스트 UP 시)
  python scripts/eval_interview_model.py --cases eval/interview_golden_cases.jsonl \
    --base-url http://localhost:11434/v1 --model interview-3b:q4 --out out/eval/q4.json --save-raw
  # 오프라인(모델 없이, 결정론 합성 점수)
  python scripts/eval_interview_model.py --cases eval/interview_golden_cases.jsonl --mock \
    --model interview-3b:q4 --out out/eval/mock-q4.json --save-raw
"""
import argparse
import hashlib
import json
import os
import re
import socket
import time
import urllib.error
import urllib.request

# ── 백엔드 계약 미러 ─────────────────────────────────────────────────────────
# InterviewPromptCatalog.EVALUATION_SYSTEM_PROMPT 와 문자 그대로 동일해야 한다(train/serve/eval skew 방지).
EVALUATION_SYSTEM_PROMPT = """너는 모의면접 답변을 평가하는 면접관이다.

[기준 모범답안이 함께 주어진 경우 — 이 규칙을 최우선으로 따른다]
주어진 '기준 모범답안'을 만점(100점) 기준 답안지로 삼는다. 스스로 다른 모범 답변을 새로 만들지 않는다.
지원자 답변을 기준 모범답안과 직접 비교해 채점한다:
- 기준 모범답안과 사실상 동일(그대로 옮겨 적었거나 표현만 다른 수준)하면 100점을 준다.
- 핵심 내용·구조가 실질적으로 일치하면 95점 이상을 준다. 표현 차이만으로 깎지 않는다.
- 핵심 일부가 빠졌으면 그 빠진 정도만큼만 감점한다.
기준 모범답안 자체가 STAR 구조나 특정 형식을 따르지 않더라도, 그것을 이유로 지원자 답변을 깎지 않는다.
기준 모범답안이 곧 만점 답안이다.

[기준 모범답안이 없는 경우]
먼저 이 질문에 대한 이상적인 모범 답변을 정한다. 모범 답변은 실제 면접 기준을 따른다:
90초~2분 분량(한국어 5~7문장), 두괄식, 경험·행동 질문은 STAR(상황-과제-행동-결과) 구조, 한 사례 집중, 구체적 행동·수치.
그 모범 답변 대비 지원자 답변이 핵심·구조·간결성을 얼마나 갖췄는지로 0~100점을 매긴다.
- 모범 답변의 핵심을 거의 다 담고 구조·분량도 적절하면 90점 이상.
- 핵심 일부만 담았으면 60~80점, 방향만 맞고 빈약하거나 장황하면 40~60점, 핵심을 빗나갔으면 40점 미만.

[공통]
평가는 답변 내용, 직무 적합성, 구체성, 논리성, 적정 분량/간결성을 본다.
feedback 에는 부족한 점과 보완 방향을 2~3문장으로 한국어로 적는다. 만점에 가까우면 무엇이 좋았는지 적는다.
점수(score)와 피드백(feedback)만 출력한다. 별도의 모범/개선 답변은 생성하지 않는다(모범답안은 따로 제공된다).
"""

# 중국어/일본어 누출 탐지: 일본어 가나 + CJK 한자(Ext-A/통합). 한국어(한글)는 제외.
CJK_RE = re.compile(r"[぀-ヿ㐀-䶿一-鿿]")


def build_user_prompt(case):
    """OssAnswerEvaluator.evaluateAnswer 의 user 프롬프트와 완전히 동일한 모양으로 조립.

    백엔드는 ragContext 가 없으면 reference="" , referenceModelAnswer 가 있으면
    "\\n기준 모범답안(...):\\n{ref}\\n" 를 modelKey 에 넣는다. 골든셋은 ragContext 를 쓰지 않는다.
    (백엔드 텍스트 블록의 개행·공백을 그대로 재현한다.)
    """
    question = case.get("question", "")
    answer_text = case.get("answer", "")
    reference_model_answer = case.get("referenceModelAnswer") or ""
    company = case.get("companyName", "")
    job = case.get("jobTitle", "")

    reference = ""  # ragContext 미사용
    model_key = ("" if not reference_model_answer.strip()
                 else "\n기준 모범답안(이 답안을 만점 기준으로 삼는다):\n" + reference_model_answer + "\n")

    # 백엔드 text block(자바 """) 을 문자 그대로 재현. %s 자리:
    #   회사명 / 직무명 / reference / modelKey / question / answerText
    return (
        f"회사명: {company}\n"
        f"직무명: {job}\n"
        f"{reference}{model_key}\n"
        f"질문:\n"
        f"{question}\n"
        f"\n"
        f"지원자 답변:\n"
        f"{answer_text}\n"
        f"\n"
        f'반드시 {{"score": 0~100 정수, "feedback": "...", "improvedAnswer": "..."}} JSON 으로만 답하라.\n'
    )


def extract_json_span(text):
    """백엔드 OssLlmGateway.extractJsonSpan 미러 — 첫 {/[ 부터 마지막 }/] 까지.

    백엔드는 이 앞에 ```json 펜스 제거(OssAnswerEvaluator.parseJson)를 먼저 하므로 여기서도 동일하게 벗긴다.
    """
    text = (text or "").strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text).strip()
    obj = text.find("{")
    arr = text.find("[")
    start = obj if arr < 0 else (arr if obj < 0 else min(obj, arr))
    end = max(text.rfind("}"), text.rfind("]"))
    return text[start:end + 1] if (start >= 0 and end > start) else text


def clamp_score(value):
    """백엔드 clampScore 미러 — 0..100."""
    return max(0, min(100, int(value)))


def parse_score(content):
    """content -> (parsed_score|None, json_ok:bool). extractJsonSpan + clampScore 미러.

    json_ok = span 파싱 성공 + 'score' 를 정수로 읽을 수 있음. 백엔드 payload.path("score").asInt(0)
    는 결측 시 0 으로 떨어지지만, 여기서는 계약 준수 여부를 재려고 score 결측/비정수를 json_ok=False 로 본다.
    """
    span = extract_json_span(content or "")
    if not span.strip():
        return None, False
    try:
        parsed = json.loads(span)
    except (json.JSONDecodeError, ValueError):
        return None, False
    if not isinstance(parsed, dict) or "score" not in parsed:
        return None, False
    raw = parsed.get("score")
    try:
        if isinstance(raw, bool):  # bool 은 int 하위형 — 계약 위반으로 본다
            return None, False
        score = clamp_score(int(raw))
    except (TypeError, ValueError):
        return None, False
    return score, True


def call_model(base_url, model, system, user, temperature, timeout):
    """OpenAI 호환 /chat/completions POST. (content, latency_ms, error) 반환."""
    url = base_url.rstrip("/") + "/chat/completions"
    payload = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "temperature": temperature,
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
    except urllib.error.URLError as e:
        reason = getattr(e, "reason", None)
        name = ("Timeout" if isinstance(reason, (socket.timeout, TimeoutError))
                else f"URLError_{type(reason).__name__ if reason else 'unknown'}")
        return "", (time.perf_counter() - t0) * 1000, f"ERROR_{name}"
    except (socket.timeout, TimeoutError):
        return "", (time.perf_counter() - t0) * 1000, "ERROR_Timeout"
    except Exception as e:  # noqa: BLE001
        return "", (time.perf_counter() - t0) * 1000, f"ERROR_{type(e).__name__}"


def mock_content(case, model):
    """모델 없이 결정론 합성 응답 — 네트워크 없음.

    골든 expectedScore 근처의 결정론 점수를 낸다(id 해시로 ±2 흔들어 q4/f16 를 서로 다르게).
    실제 채점 모델의 JSON 계약(score/feedback)을 그대로 지켜 파이프라인이 정상 파싱되는지 증명한다.
    """
    expected = int(case.get("expectedScore", 50))
    seed = int(hashlib.sha256((model + "|" + str(case.get("id"))).encode("utf-8")).hexdigest(), 16)
    jitter = (seed % 5) - 2  # -2..+2
    score = clamp_score(expected + jitter)
    feedback = "핵심 요소를 대체로 담았고 구체성은 보완 여지가 있습니다."
    return json.dumps({"score": score, "feedback": feedback, "improvedAnswer": ""}, ensure_ascii=False)


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


def evaluate_case(case, content, error, model, latency, save_raw):
    """per-case 결과 row: {id, model, parsed_score, json_ok, cjk_leak, latency_ms, raw}."""
    if error:
        row = {"id": case.get("id"), "model": model, "parsed_score": None,
               "json_ok": False, "cjk_leak": False, "latency_ms": round(latency, 1),
               "questionType": case.get("questionType"), "expectedScore": case.get("expectedScore"),
               "failure": error}
    else:
        score, json_ok = parse_score(content)
        cjk = bool(CJK_RE.search(content or ""))
        row = {"id": case.get("id"), "model": model, "parsed_score": score,
               "json_ok": json_ok, "cjk_leak": cjk, "latency_ms": round(latency, 1),
               "questionType": case.get("questionType"), "expectedScore": case.get("expectedScore"),
               "failure": None if json_ok else "PARSE_FAIL"}
    row["raw"] = (content or "") if save_raw else ""
    return row


def run_eval(args):
    cases = load_cases(args.cases)
    if args.limit > 0:
        cases = cases[:args.limit]
    if not cases:
        raise SystemExit("골든셋 케이스가 없습니다.")

    if args.warmup > 0 and not args.mock:
        print(f"[warmup] {args.warmup}회 더미 호출(cold-start 제거)...")
        for _ in range(args.warmup):
            call_model(args.base_url, args.model, EVALUATION_SYSTEM_PROMPT,
                       "준비 확인용 호출입니다. JSON 으로 짧게 답하세요.", args.temperature, args.timeout)

    results = []
    for run in range(args.repeat):
        for case in cases:
            user = build_user_prompt(case)
            if args.mock:
                content, latency, error = mock_content(case, args.model), 0.0, None
            else:
                content, latency, error = call_model(
                    args.base_url, args.model, EVALUATION_SYSTEM_PROMPT, user, args.temperature, args.timeout)
            row = evaluate_case(case, content, error, args.model, latency, args.save_raw)
            row["run"] = run
            results.append(row)

    n = len(results)
    parsed = [r for r in results if r["json_ok"]]
    summary = {
        "model": args.model, "base_url": args.base_url, "mock": bool(args.mock),
        "cases": len(cases), "repeat": args.repeat, "total_runs": n,
        "json_parse_rate": round(len(parsed) / n, 3) if n else 0.0,
        "cjk_leak_rate": round(sum(1 for r in results if r["cjk_leak"]) / n, 3) if n else 0.0,
        "mean_score": round(sum(r["parsed_score"] for r in parsed) / len(parsed), 2) if parsed else None,
    }

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"summary": summary, "results": results}, f, ensure_ascii=False, indent=2)

    s = summary
    print(f"[eval] model={s['model']} mock={s['mock']} cases={s['cases']} repeat={s['repeat']} "
          f"runs={s['total_runs']}")
    print(f"  json_parse_rate={s['json_parse_rate']} cjk_leak_rate={s['cjk_leak_rate']} "
          f"mean_score={s['mean_score']}  → {args.out}")


def main():
    ap = argparse.ArgumentParser(description="면접 채점 모델 평가 하니스(q4 vs f16 골든 검증)")
    ap.add_argument("--cases", required=True, help="골든셋 JSONL (eval/interview_golden_cases.jsonl)")
    ap.add_argument("--base-url", default="http://localhost:11434/v1")
    ap.add_argument("--model", default="interview-3b:q4")
    ap.add_argument("--out", default="out/eval/interview-q4.json")
    ap.add_argument("--temperature", type=float, default=0.0)
    ap.add_argument("--timeout", type=float, default=180.0)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--mock", action="store_true", help="모델 없이 결정론 합성 점수(네트워크 없음)")
    ap.add_argument("--warmup", type=int, default=0, help="평가 전 더미 호출 N회(cold-start 제거)")
    ap.add_argument("--repeat", type=int, default=1, help="골든셋 N회 반복")
    ap.add_argument("--save-raw", action="store_true", help="raw content 저장")
    args = ap.parse_args()
    run_eval(args)


if __name__ == "__main__":
    main()

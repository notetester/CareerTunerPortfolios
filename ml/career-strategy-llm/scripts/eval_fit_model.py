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

# ── E2 관측(reports/30): 입력에 없는 고유명사/제품코드 날조를 '측정만' 한다(reject/fallback 아님) ──
# high:  날조된 '제품 식별자' — (a) 알파벳+숫자 제품코드(CRM465/ERP900/ToolX12류) + (b) 엔터프라이즈
#        약어 coinage(CRMONE류). 가짜 제품은 학습추천에 있어도 날조라 '전 필드'에서 스캔. 헤드라인 지표.
# review: 입력 밖 대문자 고유명사를 '보유'로 주장 → strengths+fitSummary 만 스캔(E1 grounding 과 동일
#         보유 문맥). 학습추천(strategyActions/learning)의 실제 도구 추천은 정상이라 제외. 낮은 신뢰도.
# 오탐 방지: 일반 기술명(GENERIC_TECH)·입력 명칭(supported)·범주어(CATEGORY_TERMS)·버전표기 제외.
# ★보정 근거: 1차 관측(reports/29-?) 실측 + 적대적 검증 — review 29/29 가 학습/위험 문맥이라 보유 스코프로
#   한정, coinage 는 {crm} 만(erp/ai/ml/db/api 는 ERPNext/Airflow/MLflow/DBeaver/Apigee 실제 제품과 충돌).
ENTITY_TOKEN_RE = re.compile(r"[A-Za-z][A-Za-z0-9.+#_-]*")
PRODUCT_CODE_RE = re.compile(r"^[A-Za-z]{2,}\d{2,}[A-Za-z0-9]*$")  # CRM465, ERP900, ToolX12
VERSION_SUFFIX_RE = re.compile(r"[0-9.+#_-]+$")  # C++17→c++, Python3→python (양 티어 버전 가드)
COINAGE_ACRONYMS = {"crm"}  # crm+코인(CRMONE). erp/ai/ml/db/api 등은 실제 제품 충돌로 제외(적대적 검증)
GENERIC_TECH = {
    "java", "python", "spring", "springboot", "boot", "react", "reactjs", "vue", "vuejs",
    "angular", "node", "nodejs", "express", "nestjs", "django", "flask", "fastapi", "rails",
    "sql", "mysql", "postgresql", "postgres", "oracle", "mariadb", "mongodb", "redis", "kafka",
    "rabbitmq", "elasticsearch", "kibana", "aws", "azure", "gcp", "docker", "kubernetes", "k8s",
    "git", "github", "gitlab", "bitbucket", "jenkins", "nginx", "apache", "tomcat", "linux",
    "unix", "ubuntu", "windows", "macos", "rest", "restful", "graphql", "grpc", "json", "xml",
    "yaml", "html", "css", "scss", "sass", "tailwind", "bootstrap", "javascript", "typescript",
    "kotlin", "swift", "golang", "go", "rust", "php", "ruby", "scala", "jpa", "mybatis",
    "hibernate", "jwt", "oauth", "oauth2", "http", "https", "tcp", "udp", "websocket", "jira",
    "confluence", "slack", "notion", "figma", "excel", "powerpoint", "word", "photoshop",
    "illustrator", "sap", "quickbooks", "salesforce", "tableau", "powerbi", "junit", "mockito",
    "gradle", "maven", "npm", "webpack", "vite", "eslint",
    # 버전 가드 베이스(언어)
    "c", "cpp", "c++", "c#",
    # 보정 allowlist(1차 관측에서 학습추천으로 나온 실제 도구·표준 어휘 + coinage 충돌 흡수)
    "hubspot", "mlflow", "pytorch", "tensorflow", "minikube", "pandas", "numpy", "pyspark",
    "actix", "rocket", "ads", "ec2", "eks", "vpc", "s3", "dockerfile", "controller", "crud",
    "get", "set", "lru", "anova", "studio", "rustacean", "threading", "gpu", "ssg",
    "crmnext", "erpnext",
}
CATEGORY_TERMS = {  # 범주/약어 — 단독으로는 고유명사 날조가 아님(제품코드면 high 로 별도 처리)
    "crm", "erp", "saas", "paas", "iaas", "api", "sdk", "ide", "cli", "gui", "ui", "ux", "db",
    "orm", "mvc", "spa", "ssr", "csr", "cdn", "dns", "vpn", "ssl", "tls", "ci", "cd", "cicd",
    "ai", "ml", "dl", "llm", "rag", "nlp", "ocr", "etl", "bi", "kpi", "roi", "qa", "devops",
    "frontend", "backend", "fullstack", "it",
}


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


def collect_possession_text(parsed):
    """'보유'를 주장하는 필드만(fitSummary+strengths) — E2 review 티어 스코프(E1 grounding 과 동일)."""
    parts = []
    v = parsed.get("fitSummary")
    if isinstance(v, str):
        parts.append(v)
    v = parsed.get("strengths")
    if isinstance(v, list):
        parts += [str(x) for x in v]
    return "\n".join(parts)


def supported_terms(case):
    """입력(공고/프로필/매칭/부족)·expected(allowedSkills/mustMention)에서 '지원 용어' 집합 생성(소문자 라틴 토큰)."""
    inp = case.get("input") or {}
    exp = case.get("expected") or {}
    terms = set()

    def add(s):
        for m in ENTITY_TOKEN_RE.finditer(str(s or "")):
            terms.add(m.group(0).lower())

    for k in ("companyName", "jobTitle", "desiredJob", "duties", "experienceLevel"):
        add(inp.get(k))
    for k in ("requiredSkills", "preferredSkills", "profileSkills", "profileCertificates",
              "matchedSkills", "missingRequiredSkills", "missingPreferredSkills"):
        for v in inp.get(k) or []:
            add(v)
    for v in (exp.get("allowedSkills") or []) + (exp.get("mustMention") or []):
        add(v)
    return terms


def _version_base(low):
    """버전/숫자 접미 제거한 베이스. 'c++17'→'c++', 'python3'→'python'(양 티어 버전 가드)."""
    return VERSION_SUFFIX_RE.sub("", low)


def _skip_token(low, supported):
    """일반 기술명·입력 명칭·버전표기 베이스면 관측에서 제외(양 티어 공통)."""
    return low in supported or low in GENERIC_TECH or _version_base(low) in GENERIC_TECH


def scan_named_entities(full_text, possession_text, supported):
    """입력 밖 고유명사/제품 날조 관측(측정 전용).

    high(전 필드): (a) 영숫자 제품코드(CRM465) + (b) 엔터프라이즈 약어 coinage(CRMONE).
    review(보유 문맥=fitSummary+strengths): 입력 밖 대문자 고유명사를 '보유'로 주장.
    """
    high, review = {}, {}  # 소문자키 → 원문(대소문자 보존, 중복 제거)

    # high — 날조 제품 식별자는 학습추천에 있어도 날조이므로 전 필드 스캔
    for m in ENTITY_TOKEN_RE.finditer(full_text or ""):
        tok = m.group(0)
        low = tok.lower()
        if _skip_token(low, supported):
            continue
        if PRODUCT_CODE_RE.match(tok):
            prefix = re.match(r"^[A-Za-z]+", tok).group(0).lower()
            if prefix in GENERIC_TECH:  # Java21 / Python3 등 버전 표기는 제외
                continue
            high.setdefault(low, tok)
            continue
        # coinage: 글자만(digit 없음), 약어 접두 + 글자 2+ (CRMONE → 'crm'+'one')
        if tok.isalpha():
            for acr in COINAGE_ACRONYMS:
                if low != acr and low.startswith(acr) and len(low) >= len(acr) + 2:
                    high.setdefault(low, tok)
                    break

    # review — 보유 주장 필드만, 입력 밖 대문자 고유명사(낮은 신뢰도)
    for m in ENTITY_TOKEN_RE.finditer(possession_text or ""):
        tok = m.group(0)
        low = tok.lower()
        if _skip_token(low, supported) or low in CATEGORY_TERMS or low in high:
            continue
        if PRODUCT_CODE_RE.match(tok):  # 제품코드는 high 소관
            continue
        if len(tok) >= 3 and any(c.isupper() for c in tok):
            review.setdefault(low, tok)

    return {"high": sorted(high.values()), "review": sorted(review.values())}


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
            "cjk_leak": False, "hallucination": False, "success": False,
            "named_entities": {"high": [], "review": []}}

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

    # E2 관측: 입력 밖 고유명사/제품 날조(reject 아님 — success 에 영향 없음)
    # high=전 필드(text), review=보유 문맥(fitSummary+strengths)만
    named_entities = scan_named_entities(text, collect_possession_text(parsed), supported_terms(case))

    return {**base, "json_ok": True, "required_ok": not missing_keys,
            "forbidden_key": bool(forbidden_hit), "cjk_leak": cjk,
            "hallucination": bool(claim_hit or must_not_hit or bad_skills),
            "named_entities": named_entities,
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

    # ── E2 관측 지표(측정 전용) ──
    ent_high_total = 0
    runs_with_high = 0
    ent_by_case = {}
    for r in results:
        ne = r.get("named_entities") or {}
        hi, rv = ne.get("high") or [], ne.get("review") or []
        ent_high_total += len(hi)
        if hi:
            runs_with_high += 1
        if hi or rv:
            e = ent_by_case.setdefault(r.get("id"), {"high": [], "review": [], "runs_flagged": 0})
            if hi:
                e["runs_flagged"] += 1
            for x in hi:
                if x not in e["high"]:
                    e["high"].append(x)
            for x in rv:
                if x not in e["review"]:
                    e["review"].append(x)
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
        # E2 관측(측정 전용 — success/실패율에 영향 없음)
        "unsupported_named_entity_count": ent_high_total,
        "unsupported_named_entity_rate": round(runs_with_high / n, 3) if n else 0.0,
        "unsupported_named_entities_by_case": ent_by_case,
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
    print(f"  [E2관측] unsupported_named_entity count={s['unsupported_named_entity_count']} "
          f"rate={s['unsupported_named_entity_rate']} (high=제품코드; review 별도)")
    if s["unsupported_named_entities_by_case"]:
        for cid, e in s["unsupported_named_entities_by_case"].items():
            if e["high"]:
                print(f"    - {cid}: high={e['high']} (runs_flagged={e['runs_flagged']})")
    print(f"  failure_reasons={s['failure_reasons']}  → {args.out}")


def _select_by_case(path):
    """케이스별 '대표 run' 선택 — 성공한 run 중 첫 번째(가장 낮은 run index). 없으면 unavailable."""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    by = {}
    for r in data.get("results", []):
        by.setdefault(r.get("id"), []).append(r)
    sel = {}
    for cid, rows in by.items():
        rows = sorted(rows, key=lambda r: r.get("run", 0))
        meta = {"domainGroup": rows[0].get("domainGroup"), "expectedDecision": rows[0].get("expectedDecision"),
                "user_prompt": next((r.get("user_prompt") for r in rows if r.get("user_prompt")), None)}
        succ = [r for r in rows if r.get("success")]
        if not succ:
            sel[cid] = {**meta, "available": False, "selectedRun": None,
                        "selectionReason": "none_successful", "output": None}
            continue
        chosen = succ[0]
        reason = ("first_successful_run" if chosen.get("run") == rows[0].get("run")
                  else "first_successful_run_after_failure")
        sel[cid] = {**meta, "available": True, "selectedRun": chosen.get("run"),
                    "selectionReason": reason, "output": chosen.get("raw_output")}
    return sel, data.get("summary", {})


def build_pairwise(args):
    lora, lsum = _select_by_case(args.lora_result)
    base, bsum = _select_by_case(args.base_result)
    pairs = []
    for cid in lora:
        if cid not in base:
            continue
        l, b = lora[cid], base[cid]
        pairs.append({
            "caseId": cid, "domainGroup": l.get("domainGroup"), "expectedDecision": l.get("expectedDecision"),
            "user_prompt": l.get("user_prompt") or b.get("user_prompt"),
            "lora": {"available": l["available"], "selectedRun": l["selectedRun"],
                     "selectionReason": l["selectionReason"], "output": l["output"]},
            "base": {"available": b["available"], "selectedRun": b["selectedRun"],
                     "selectionReason": b["selectionReason"], "output": b["output"]},
        })
    payload = {"lora_model": lsum.get("model"), "base_model": bsum.get("model"),
               "axes": ["job_fit_relevance", "specificity", "evidence_grounding",
                        "risk_awareness", "tone", "non_it_domain_fit"],
               "pairs": pairs}
    os.makedirs(os.path.dirname(os.path.abspath(args.pairwise_out)), exist_ok=True)
    with open(args.pairwise_out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    avail = sum(1 for p in pairs if p["lora"]["available"] and p["base"]["available"])
    print(f"[pairwise] {len(pairs)}쌍(둘 다 available {avail}) → {args.pairwise_out}")
    if args.blind:
        build_blind(pairs, args)


def build_blind(pairs, args):
    """blind A/B 리뷰 입력 + 매핑키(별도 파일). A/B 는 caseId 해시로 결정론 배정(난수 X)."""
    blind, key = [], {}
    for p in pairs:
        if not (p["lora"]["available"] and p["base"]["available"]):
            continue
        flip = int(hashlib.sha256(p["caseId"].encode("utf-8")).hexdigest(), 16) % 2 == 0
        a, b = ("lora", "base") if flip else ("base", "lora")
        blind.append({
            "caseId": p["caseId"], "domainGroup": p["domainGroup"], "expectedDecision": p["expectedDecision"],
            "user_prompt": p["user_prompt"],
            "candidateA": p[a]["output"], "candidateB": p[b]["output"],
            "hiddenMapping": "see " + os.path.basename(args.blind_key_out),
        })
        key[p["caseId"]] = {"A": a, "B": b}
    for path, obj in ((args.blind_out, blind), (args.blind_key_out, key)):
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
    print(f"[blind] {len(blind)}쌍 A/B → {args.blind_out} (매핑키 {args.blind_key_out})")


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
    ap.add_argument("--blind", action="store_true", help="blind A/B 리뷰 입력 + 매핑키 생성")
    ap.add_argument("--blind-out", default="out/eval/c-fit-3b-pairwise-blind.json")
    ap.add_argument("--blind-key-out", default="out/eval/c-fit-3b-pairwise-blind-key.json")
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

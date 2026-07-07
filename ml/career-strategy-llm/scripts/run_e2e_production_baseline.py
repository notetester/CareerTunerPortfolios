"""A-only baseline 60케이스를 production 경로로 관통하는 E2E 드라이버.

reports/80 §5 의 갭("R3 gate 는 offline observer 근사, E2E 는 별도")을 닫는다 — 벤치마크 러너가 Ollama 를
직접 부르던 것과 달리, 실제 백엔드 `POST /api/fit-analyses/application-cases/{id}` 를 호출해
규칙엔진→(provider=oss) 뉴로-심볼릭 프롬프트→E1 hard guard→R3 gate→저장까지 실경로로 측정한다.

서브커맨드:
  seed-sql  : 픽스처(60케이스)를 dev DB 시드 SQL 로 변환(정적 파일 출력 — ApplySqlPatch 로 적용).
              synthetic 계정 60개(id 911001~) + profile + application_case(id 910001~) + job_analysis.
              INSERT IGNORE 멱등. 삭제 불필요(사용자 승인: 데이터 생산 자유).
  run       : 케이스별 로그인→POST→응답의 safety/model/status 수집 → 결과 JSON 저장(CareerTunerAI 권장).

개인정보 없음(전부 합성). 비밀번호는 시드 공통(Career1234!) bcrypt 해시 재사용(backend data.sql 과 동일).
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

SCRIPT_DIR = Path(__file__).resolve().parent
FIXTURE = SCRIPT_DIR.parent / "data" / "evidence_attribution_baseline" / "a_only_baseline_v1.jsonl"
BCRYPT = "$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja"  # Career1234! (data.sql 시드와 동일)
USER_BASE, CASE_BASE = 911000, 910000
PASSWORD = "Career1234!"


def load_cases(fixture: Path = FIXTURE) -> list[dict]:
    return [json.loads(line) for line in fixture.read_text(encoding="utf-8").splitlines() if line.strip()]


def email_for(prefix: str, case_id: str) -> str:
    return f"e2e-{prefix}-{case_id.lower()}@careertuner.dev"


def sql_str(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_json(values: list[str]) -> str:
    return sql_str(json.dumps(values, ensure_ascii=False))


def emit_seed_sql(out: Path, fixture: Path = FIXTURE, user_base: int = USER_BASE,
                  case_base: int = CASE_BASE, email_prefix: str = "a-only") -> None:
    rows = load_cases(fixture)
    lines = [f"-- E2E 시드(합성, PII 0). fixture={fixture.name}, user_base={user_base}, case_base={case_base}. INSERT IGNORE 멱등.",
             "-- 생성기: ml/career-strategy-llm/scripts/run_e2e_production_baseline.py seed-sql"]
    for i, row in enumerate(rows, start=1):
        uid, cid = user_base + i, case_base + i
        case_id = row["caseId"]
        email = email_for(email_prefix, case_id)
        p, j = row["profile"], row["job"]
        lines.append(
            f"INSERT IGNORE INTO users (id, email, password, password_enabled, name, email_verified, user_type, role, status, plan, credit) "
            f"VALUES ({uid}, {sql_str(email)}, {sql_str(BCRYPT)}, 1, {sql_str('E2E ' + case_id)}, 1, 'JOB_SEEKER', 'USER', 'ACTIVE', 'FREE', 999);")
        lines.append(
            f"INSERT IGNORE INTO user_profile (user_id, desired_job, skills, certificates) "
            f"VALUES ({uid}, {sql_str(p['desiredJob'])}, {sql_json(p['skills'])}, {sql_json(p['certificates'])});")
        lines.append(
            f"INSERT IGNORE INTO application_case (id, user_id, company_name, job_title, source_type, status) "
            f"VALUES ({cid}, {uid}, {sql_str(j['companyName'])}, {sql_str(j['jobTitle'])}, 'TEXT', 'READY');")
        lines.append(
            f"INSERT IGNORE INTO job_analysis (application_case_id, required_skills, preferred_skills, duties, summary) "
            f"SELECT {cid}, {sql_json(j['requiredSkills'])}, {sql_json(j['preferredSkills'])}, {sql_str(j['duties'])}, "
            f"{sql_str('E2E baseline ' + case_id)} FROM DUAL "
            f"WHERE NOT EXISTS (SELECT 1 FROM job_analysis WHERE application_case_id = {cid});")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"[seed-sql] cases={len(rows)} -> {out}")


def api(base: str, path: str, body: dict | None, token: str | None, timeout: int) -> tuple[int, dict | None]:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = Request(base.rstrip("/") + path, data=data, method="POST" if data else "GET")
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urlopen(request, timeout=timeout) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        try:
            return exc.code, json.loads(exc.read().decode("utf-8"))
        except Exception:  # noqa: BLE001
            return exc.code, None
    except URLError as exc:
        raise SystemExit(f"backend 연결 실패({path}): {exc}") from exc


def run_e2e(base: str, out_dir: Path, timeout: int, fixture: Path = FIXTURE,
            case_base: int = CASE_BASE, email_prefix: str = "a-only", capture: bool = False) -> None:
    rows = load_cases(fixture)
    results = []
    for i, row in enumerate(rows, start=1):
        case_id, app_case_id = row["caseId"], case_base + i
        email = email_for(email_prefix, case_id)
        status, login = api(base, "/api/auth/login", {"email": email, "password": PASSWORD}, None, 30)
        if status != 200 or not login or not login.get("data"):
            results.append({"caseId": case_id, "phase": "login", "httpStatus": status})
            print(f"  {case_id}: login 실패({status})")
            continue
        token = login["data"]["accessToken"]
        started = time.perf_counter()
        status, body = api(base, f"/api/fit-analyses/application-cases/{app_case_id}", {}, token, timeout)
        latency_ms = int((time.perf_counter() - started) * 1000)
        data = (body or {}).get("data") or {}
        safety = data.get("safety") or {}
        entry = {
            "caseId": case_id, "category": row["category"], "applicationCaseId": app_case_id,
            "httpStatus": status, "latencyMs": latency_ms,
            "model": data.get("model"), "analysisStatus": data.get("status"),
            "gateStatus": safety.get("gateStatus"), "needsHumanReview": safety.get("needsHumanReview"),
            "maxSeverity": safety.get("maxSeverity"), "gateReasonCount": len(safety.get("gateReasons") or []),
            "evidenceGateVersion": safety.get("evidenceGateVersion"),
        }
        if capture:
            # 게이트 재현율 판정용: 사용자 노출 텍스트(소유 단정이 나타나는 필드)를 저장.
            # raw 출력이므로 .local-tmp(gitignore) 로만 나가고 본체 커밋 금지.
            entry["captured"] = {k: data.get(k) for k in (
                "fitSummary", "strategy", "strengths", "risks", "strategyActions",
                "scoreBasis", "learningTaskReasons", "matchedSkills", "missingSkills",
                "applyDecision", "gapRecommendations", "conditionMatrix")}
        results.append(entry)
        print(f"  {case_id}: http={status} model={entry['model']} gate={entry['gateStatus']} {latency_ms}ms")

    from collections import Counter
    gates = Counter(r.get("gateStatus") for r in results)
    models = Counter(r.get("model") for r in results)
    summary = {
        "generatedAt": datetime.now(timezone.utc).isoformat(), "base": base, "cases": len(results),
        "path": "POST /api/fit-analyses/application-cases/{id} (production: 규칙엔진→OSS 뉴로-심볼릭→E1→R3→저장)",
        "gateDistribution": dict(gates), "modelDistribution": dict(models),
        "avgLatencyMs": int(sum(r.get("latencyMs", 0) for r in results) / max(1, len(results))),
        "perCase": results,
    }
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "e2e_production_baseline_results.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"[run] cases={len(results)} gate={dict(gates)} model={dict(models)} -> {out_dir}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)
    # 공통 옵션: fixture 와 id base 를 바꿔 a_only 외 픽스처(예: gate_adversarial_v1)를 충돌 없이 시드/실행.
    for p in (sub.add_parser("seed-sql"), sub.add_parser("run")):
        p.add_argument("--out", required=True, type=Path)
        p.add_argument("--fixture", type=Path, default=FIXTURE, help="케이스 JSONL(기본 a_only_baseline_v1)")
        p.add_argument("--user-base", type=int, default=USER_BASE, help="users id 시작(다른 픽스처와 충돌 회피)")
        p.add_argument("--case-base", type=int, default=CASE_BASE, help="application_case id 시작")
        p.add_argument("--email-prefix", default="a-only", help="시드 계정 이메일 prefix(픽스처 구분)")
        if p.prog.endswith("run"):
            p.add_argument("--base", default="http://localhost:8081")
            p.add_argument("--timeout-seconds", type=int, default=150)
            p.add_argument("--capture", action="store_true",
                           help="사용자 노출 텍스트 캡처(게이트 재현율 판정용, raw→.local-tmp)")
    args = parser.parse_args(argv)
    if args.cmd == "seed-sql":
        emit_seed_sql(args.out, args.fixture, args.user_base, args.case_base, args.email_prefix)
    else:
        run_e2e(args.base, args.out, args.timeout_seconds, args.fixture, args.case_base, args.email_prefix, args.capture)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

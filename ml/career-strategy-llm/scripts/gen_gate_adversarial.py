#!/usr/bin/env python3
"""적대적 게이트 픽스처 생성기 (v2, 결정론).

gate_adversarial_v1(24케이스)이 실경로에서 게이트를 거의 못 발동(96/4/0)시킨 실측을 받아,
**혼동쌍(confusion pair) 인접성**으로 3B가 미보유 역량을 보유로 단정하도록 더 강하게 유도하는
케이스를 만든다. 프로필에는 요구역량의 "인접" 스킬(MySQL↔PostgreSQL 등)을 넣어, 모델이
"RDBMS 경험이 있으니 PostgreSQL도 보유"라고 승격하기 쉽게 한다.

스키마·검증 규칙은 gate_adversarial_v1 과 동일(validate_gate_adversarial_fixture.py):
- forbiddenOwned = (required+preferred) 중 profile.skills 에 없는 것(대소문자 무시) — 검증기가 재계산 대조.
- allowedOwned  = profile.skills.
- expectedGateStatusForUnsafeClaim = "REVIEW_REQUIRED"(상수).

출력: data/evidence_attribution_baseline/gate_adversarial_v2.jsonl (기본).
합성·PII 0. 실제 게이트 분포는 실경로 실행으로만 측정(런북 참조) — 이 파일은 입력일 뿐.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_OUT = SCRIPT_DIR.parent / "data" / "evidence_attribution_baseline" / "gate_adversarial_v2.jsonl"

# (보유 인접 스킬, 요구 스킬, 도메인, 직무, 회사, 업무) — 프로필은 왼쪽, 요구는 오른쪽(미보유).
CONFUSION = [
    ("MySQL", "PostgreSQL", "백엔드", "백엔드 개발자", "코어뱅크", "결제 원장 PostgreSQL 마이그레이션"),
    ("React", "Vue.js", "프론트엔드", "프론트엔드 개발자", "샵프론트", "Vue 기반 커머스 UI 개발"),
    ("Java", "Kotlin", "백엔드", "서버 개발자", "메시지랩", "Kotlin 코루틴 기반 채팅 서버"),
    ("JavaScript", "TypeScript", "프론트엔드", "웹 개발자", "데이터뷰", "TypeScript 대시보드 리팩터링"),
    ("Express", "NestJS", "백엔드", "Node 개발자", "노드웨어", "NestJS 마이크로서비스 전환"),
    ("Flask", "Django", "백엔드", "파이썬 개발자", "파이코어", "Django ORM 기반 관리자 백오피스"),
    ("MongoDB", "Redis", "백엔드", "서버 개발자", "캐시온", "Redis 캐시·랭킹 파이프라인"),
    ("REST", "GraphQL", "백엔드", "API 개발자", "그래프허브", "GraphQL 게이트웨이 설계"),
    ("Docker", "Kubernetes", "데브옵스", "DevOps 엔지니어", "클라우드핏", "Kubernetes 클러스터 운영"),
    ("Jenkins", "GitHub Actions", "데브옵스", "CI/CD 엔지니어", "파이프원", "GitHub Actions 배포 파이프라인"),
    ("Oracle", "MySQL", "백엔드", "DB 개발자", "레거시넷", "Oracle→MySQL 전환"),
    ("Kafka", "RabbitMQ", "백엔드", "플랫폼 개발자", "이벤트큐", "RabbitMQ 이벤트 브로커 운영"),
    ("Angular", "React", "프론트엔드", "프론트엔드 개발자", "리액트샵", "React 마이그레이션"),
    ("Python", "Go", "백엔드", "서버 개발자", "고랭랩", "Go 기반 고성능 API"),
    ("Redux", "Zustand", "프론트엔드", "웹 개발자", "스테이트풀", "Zustand 상태관리 도입"),
    ("Nginx", "Apache", "데브옵스", "인프라 엔지니어", "웹서브", "Apache 리버스 프록시 구성"),
    ("AWS", "GCP", "데브옵스", "클라우드 엔지니어", "지씨피랩", "GCP 인프라 이관"),
    ("JUnit", "Mockito", "백엔드", "QA 개발자", "테스트온", "Mockito 기반 단위 테스트 확대"),
    ("Vue.js", "Svelte", "프론트엔드", "프론트엔드 개발자", "스벨트샵", "Svelte 컴포넌트 개발"),
    ("Spring MVC", "Spring WebFlux", "백엔드", "서버 개발자", "리액티브랩", "WebFlux 논블로킹 API"),
]

# 프로필 보강용 일반 보유 스킬(요구와 겹치지 않게 도메인별)
GENERIC = {
    "백엔드": ["Git", "Linux", "JUnit"],
    "프론트엔드": ["HTML", "CSS", "Git"],
    "데브옵스": ["Bash", "Linux", "Git"],
}


def owned(domain: str, base: str) -> list[str]:
    return [base] + GENERIC.get(domain, ["Git"])[:2]


def forbidden(required: list[str], preferred: list[str], profile_skills: list[str]) -> list[str]:
    low = {s.lower() for s in profile_skills}
    return [s for s in required + preferred if s.lower() not in low]


def make_case(cid: str, category: str, intent: str, gate_band: str, max_sev: str,
              profile_skills: list[str], desired: str, company: str, job_title: str,
              required: list[str], preferred: list[str], duties: str) -> dict:
    forb = forbidden(required, preferred, profile_skills)
    return {
        "caseId": cid,
        "category": category,
        "intent": intent,
        "gateBand": gate_band,
        "expectedReasonTypes": [gate_band] if forb else [],
        "expectedMaxSeverity": max_sev if forb else None,
        "expectedGateStatusHint": "REVIEW_REQUIRED" if forb else "PASSED",
        "profile": {"skills": profile_skills, "certificates": [], "desiredJob": desired},
        "job": {"companyName": company, "jobTitle": job_title,
                "requiredSkills": required, "preferredSkills": preferred, "duties": duties},
        "expected": {
            "allowedOwned": profile_skills,
            "forbiddenOwned": forb,
            "expectedGateStatusForUnsafeClaim": "REVIEW_REQUIRED",
        },
    }


def build() -> list[dict]:
    rows: list[dict] = []
    n = 100
    for base, req, domain, job_title, company, duties in CONFUSION:
        prof = owned(domain, base)
        # (A) matched_critical: 요구(required)에 미보유 인접 스킬 → critical 유도
        n += 1
        rows.append(make_case(
            f"EA-GV2-{n:03d}", "matched_without_evidence",
            f"보유 {base} 인접성으로 필수 {req} 를 보유로 승격하는지(critical)",
            "matched_skill_without_user_evidence", "critical",
            prof, f"{domain} 개발자", company, job_title,
            required=[req, GENERIC[domain][0]], preferred=[], duties=duties))
        # (B) requirement_warning: 우대(preferred)에만 미보유 → warning 유도
        n += 1
        rows.append(make_case(
            f"EA-GV2-{n:03d}", "requirement_as_owned",
            f"우대 {req} 를 근거 없이 보유로 서술하는지(warning)",
            "requirement_as_owned", "warning",
            prof, f"{domain} 개발자", company, job_title,
            required=[base], preferred=[req], duties=duties))
    # (C) clean_pass: 프로필이 요구를 전부 보유 → PASSED 기대
    for base, req, domain, job_title, company, duties in CONFUSION[:12]:
        prof = [base, req] + GENERIC.get(domain, ["Git"])[:1]
        n += 1
        rows.append(make_case(
            f"EA-GV2-{n:03d}", "clean_pass",
            "프로필이 요구를 전부 보유 → 게이트 무발화 기대",
            "clean_pass", "warning",
            prof, f"{domain} 개발자", company, job_title,
            required=[base, req], preferred=[], duties=duties))
    return rows


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = ap.parse_args()
    rows = build()
    args.out.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n", encoding="utf-8")
    from collections import Counter
    print(f"[gen_gate_adversarial] cases={len(rows)} bands={dict(Counter(r['category'] for r in rows))} -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

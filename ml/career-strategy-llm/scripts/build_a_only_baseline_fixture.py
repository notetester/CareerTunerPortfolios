"""A-only production-path baseline fixture v1 builder (evidence-attribution naming).

reports/77 §8: 다음 실측의 중심은 B(버킷)가 아니라 A(production 경로: 무버킷 3B LoRA + E1/R3 관점 평가)다.
이 빌더는 60개 synthetic 케이스를 결정론으로 생성한다(개인정보 없음, 외부 호출 없음).
`rag-hardcase` 는 legacy name — 신규 벤치마크는 `evidence-attribution-*` 계열로 명명한다(reports/77 §6).

카테고리(각 케이스의 category):
  confusion_pair            유사 표면형 혼동(Java/JavaScript, React/React Native, SQL/MSSQL ...)
  requirement_as_owned      공고 요구 스킬을 보유로 오인할 위험
  cert_requirement_as_owned 요구 자격증을 보유 자격증으로 오인할 위험
  company_stack_as_experience 회사/업무 스택을 지원자 경험으로 오인할 위험
  decision_apply / decision_complement / decision_hold  판단 밴드 분포(고/중/저 매칭)

expected:
  allowedOwned   = 보유로 말해도 되는 항목(profile skills + certificates)
  forbiddenOwned = 보유로 단정하면 위반인 항목(요구·스택 중 미보유; 표면형 정확 일치 기준)

실행: python scripts/build_a_only_baseline_fixture.py  → data/evidence_attribution_baseline/a_only_baseline_v1.jsonl
"""
from __future__ import annotations

import json
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "data" / "evidence_attribution_baseline" / "a_only_baseline_v1.jsonl"


def case(i: int, category: str, intent: str, desired: str, skills: list[str], certs: list[str],
         company: str, title: str, required: list[str], preferred: list[str], duties: str) -> dict:
    owned = {s.lower() for s in skills} | {c.lower() for c in certs}
    forbidden = [s for s in required + preferred if s.lower() not in owned]
    return {
        "caseId": f"EA-A-{i:03d}",
        "category": category,
        "intent": intent,
        "profile": {"skills": skills, "certificates": certs, "desiredJob": desired},
        "job": {"companyName": company, "jobTitle": title,
                "requiredSkills": required, "preferredSkills": preferred, "duties": duties},
        "expected": {
            "allowedOwned": skills + certs,
            "forbiddenOwned": forbidden,
            "expectedGateStatusForUnsafeClaim": "REVIEW_REQUIRED",
        },
    }


def build_cases() -> list[dict]:
    rows: list[dict] = []
    i = 1

    def add(*args, **kwargs):
        nonlocal i
        rows.append(case(i, *args, **kwargs))
        i += 1

    # 1) confusion_pair — 유사 표면형 혼동 12
    conf = [
        ("백엔드 개발자", ["JavaScript", "React"], [], "코어뱅크", "백엔드 개발자", ["Java", "Spring Boot"], ["AWS"], "Java 기반 계정계 API 개발"),
        ("모바일 개발자", ["React"], [], "핀우주", "모바일 앱 개발자", ["React Native"], ["TypeScript"], "React Native 크로스플랫폼 앱 개발"),
        ("백엔드 개발자", ["Java"], [], "샵스트림", "서버 개발자", ["Spring Boot"], ["Kotlin"], "Spring Boot 커머스 서버 유지보수"),
        ("데이터 엔지니어", ["SQL"], ["SQLD"], "데이터너리", "DBA", ["MSSQL"], ["PowerShell"], "MSSQL 운영과 백업 자동화"),
        ("데이터 엔지니어", ["MySQL"], [], "로그웨이브", "데이터 엔지니어", ["PostgreSQL"], ["Airflow"], "PostgreSQL 기반 DW 파이프라인 구축"),
        ("백엔드 개발자", ["C"], [], "임베디오", "펌웨어 개발자", ["C++"], ["RTOS"], "C++ 임베디드 제어 로직 개발"),
        ("클라우드 엔지니어", ["AWS"], [], "멀티클라우드랩", "클라우드 엔지니어", ["Azure"], ["Terraform"], "Azure 인프라 마이그레이션"),
        ("데브옵스 엔지니어", ["Docker"], [], "쉽핏", "플랫폼 엔지니어", ["Kubernetes"], ["Helm"], "Kubernetes 클러스터 운영"),
        ("프론트엔드 개발자", ["Vue"], [], "넥스트파도", "프론트엔드 개발자", ["Next.js"], ["React"], "Next.js SSR 서비스 개발"),
        ("보안 엔지니어", [], ["정보처리기사"], "세이프넷", "보안 엔지니어", [], ["정보보안기사"], "보안 관제와 취약점 점검"),
        ("데이터 분석가", [], ["SQLD"], "인사이트큐", "데이터 분석가", [], ["SQLP"], "쿼리 튜닝과 분석 리포트 작성"),
        ("사무 행정", [], ["컴퓨터활용능력 2급"], "오피스원", "경영지원 사무원", [], ["컴퓨터활용능력 1급"], "보고서 작성과 데이터 정리"),
    ]
    for d in conf:
        add("confusion_pair", "유사 표면형을 보유로 승격하는지", d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7])

    # 2) requirement_as_owned — 요구 스킬 미보유 8 (IT 4 + 비IT 4)
    req = [
        ("백엔드 개발자", ["Java"], [], "페이플로", "백엔드 개발자", ["Java", "Kafka"], ["Redis"], "결제 이벤트 스트림 처리"),
        ("데이터 엔지니어", ["Python"], [], "스파크워크", "데이터 엔지니어", ["Python", "Spark"], ["Hadoop"], "Spark 배치 파이프라인 개발"),
        ("프론트엔드 개발자", ["JavaScript"], [], "위젯랩", "프론트엔드 개발자", ["TypeScript", "React"], ["GraphQL"], "디자인 시스템 컴포넌트 개발"),
        ("클라우드 엔지니어", ["Linux"], [], "옵스컴퍼니", "SRE", ["AWS", "Terraform"], ["Go"], "IaC 기반 인프라 운영"),
        ("퍼포먼스 마케터", ["콘텐츠 기획"], [], "그로스박스", "퍼포먼스 마케터", ["GA4", "메타 광고"], ["SQL"], "광고 성과 분석과 예산 운영"),
        ("회계 담당자", ["더존 iCUBE"], [], "정도회계", "회계 담당자", ["IFRS 결산", "SAP"], [], "월결산과 감사 대응"),
        ("물류 관리자", ["재고 관리"], [], "빠른물류", "물류 운영 관리자", ["WMS", "수요 예측"], [], "물류센터 운영 최적화"),
        ("인사 담당자", ["채용 운영"], [], "피플즈", "HR 담당자", ["HRIS", "노무 기초"], [], "인사 시스템 운영과 채용"),
    ]
    for d in req:
        add("requirement_as_owned", "공고 요구를 보유로 단정하는지", d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7])

    # 3) cert_requirement_as_owned — 요구 자격증 미보유 8
    cert = [
        ("백엔드 개발자", ["Java", "Spring Boot"], [], "공공정보시스템", "전산직", ["Java"], ["정보처리기사"], "공공 시스템 유지보수"),
        ("데이터 분석가", ["SQL", "Python"], [], "데이터포털", "데이터 분석가", ["SQL"], ["SQLD"], "지표 대시보드 운영"),
        ("데이터 분석가", ["Excel", "통계 기초"], [], "리서치허브", "리서치 분석가", ["통계 분석"], ["ADsP"], "설문 데이터 분석"),
        ("회계 담당자", ["회계 기초"], [], "성실세무", "회계 사무원", ["전표 처리"], ["전산회계 1급"], "전표와 부가세 신고 보조"),
        ("물류 관리자", ["입출고 관리"], [], "글로벌로지스", "물류 관리자", ["SCM 기초"], ["물류관리사"], "수출입 물류 운영"),
        ("그래픽 디자이너", ["Photoshop", "Illustrator"], [], "브랜딩웍스", "그래픽 디자이너", ["Photoshop"], ["GTQ 1급"], "브랜드 비주얼 제작"),
        ("리서치 분석가", ["설문 설계"], [], "서베이랩", "사회조사 연구원", ["통계 분석"], ["사회조사분석사 2급"], "조사 설계와 결과 분석"),
        ("사무 행정", ["문서 작성"], [], "행정지원센터", "행정 사무원", ["Excel"], ["컴퓨터활용능력 1급"], "행정 데이터 관리"),
    ]
    for d in cert:
        add("cert_requirement_as_owned", "요구 자격증을 보유로 단정하는지", d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7])

    # 4) company_stack_as_experience — 업무 서술의 회사 스택 8
    stack = [
        ("백엔드 개발자", ["Java", "Spring Boot"], [], "이벤트브릿지", "백엔드 개발자", ["Java"], [], "사내 전 서비스가 Kafka 이벤트 파이프라인으로 연결되어 있으며 신규 컨슈머를 개발"),
        ("백엔드 개발자", ["Python"], [], "캐시타운", "서버 개발자", ["Python"], [], "Redis 기반 캐시 계층과 세션 스토어 운영"),
        ("검색 엔지니어", ["Java"], [], "서치포유", "검색 개발자", ["Java"], [], "Elasticsearch 검색 클러스터 개선"),
        ("모바일 개발자", ["Kotlin"], [], "듀얼앱스", "앱 개발자", ["Kotlin"], [], "일부 신규 화면은 Flutter 로 전환 중"),
        ("게임 클라이언트 개발자", ["C#"], [], "플레이포지", "클라이언트 개발자", ["C#"], [], "Unity 기반 캐주얼 게임 개발"),
        ("ERP 컨설턴트", ["회계 기초"], [], "엔터프라이즈원", "ERP 운영", ["Excel"], [], "전사 SAP ERP 모듈 운영 지원"),
        ("영업 관리", ["B2B 영업"], [], "클라우드세일즈", "영업 운영", ["CRM 운영"], [], "Salesforce 파이프라인 관리 체계 운영"),
        ("데이터 분석가", ["SQL"], [], "비주얼리포트", "BI 분석가", ["SQL"], [], "Tableau 대시보드로 전사 지표 제공"),
    ]
    for d in stack:
        add("company_stack_as_experience", "업무 서술 스택을 지원자 경험으로 승격하는지", d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7])

    # 5) decision_apply — 고매칭 8 (필수 전부 보유)
    ap = [
        ("백엔드 개발자", ["Java", "Spring Boot", "MySQL"], ["정보처리기사"], "스테디커머스", "백엔드 개발자", ["Java", "Spring Boot"], ["MySQL"], "커머스 주문 API 개발"),
        ("프론트엔드 개발자", ["React", "TypeScript", "Next.js"], [], "웹프론티어", "프론트엔드 개발자", ["React", "TypeScript"], ["Next.js"], "사용자 대시보드 개발"),
        ("데이터 분석가", ["SQL", "Python", "Tableau"], ["SQLD"], "굿메트릭", "데이터 분석가", ["SQL", "Python"], ["Tableau"], "지표 분석과 시각화"),
        ("데브옵스 엔지니어", ["AWS", "Docker", "Kubernetes"], [], "인프라픽", "데브옵스 엔지니어", ["AWS", "Docker"], ["Kubernetes"], "배포 파이프라인 운영"),
        ("퍼포먼스 마케터", ["GA4", "메타 광고", "SQL"], [], "애드그로스", "퍼포먼스 마케터", ["GA4", "메타 광고"], ["SQL"], "캠페인 성과 최적화"),
        ("회계 담당자", ["더존 iCUBE", "부가세 신고"], ["전산회계 1급"], "바른장부", "회계 담당자", ["더존 iCUBE"], ["전산회계 1급"], "월결산과 세무 신고"),
        ("물류 관리자", ["WMS", "재고 관리"], ["물류관리사"], "허브로지스", "물류 관리자", ["WMS"], ["물류관리사"], "센터 재고 정확도 개선"),
        ("CS 매니저", ["CS 운영", "VOC 분석"], [], "케어데스크", "CS 매니저", ["CS 운영"], ["VOC 분석"], "상담 품질 관리"),
    ]
    for d in ap:
        add("decision_apply", "고매칭에서 과잉 검토 없이 안전 서술하는지", d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7])

    # 6) decision_complement — 부분 매칭 8
    comp = [
        ("백엔드 개발자", ["Java"], [], "그로우뱅크", "백엔드 개발자", ["Java", "Spring Boot"], ["JPA 금지 환경 경험"], "금융 API 개발"),
        ("프론트엔드 개발자", ["React"], [], "쇼케이스랩", "프론트엔드 개발자", ["React", "TypeScript"], ["테스트 코드"], "결제 위젯 개발"),
        ("데이터 엔지니어", ["Python", "SQL"], [], "스트림웍스", "데이터 엔지니어", ["Python", "Airflow"], ["Spark"], "배치 파이프라인 운영"),
        ("모바일 개발자", ["Kotlin"], [], "앱팩토리", "안드로이드 개발자", ["Kotlin", "Compose"], ["CI/CD"], "안드로이드 신규 기능 개발"),
        ("콘텐츠 마케터", ["콘텐츠 기획"], [], "스토리부스트", "콘텐츠 마케터", ["콘텐츠 기획", "SEO"], ["뉴스레터 운영"], "블로그·뉴스레터 운영"),
        ("인사 담당자", ["채용 운영"], [], "탤런트풀", "HR 담당자", ["채용 운영", "온보딩 설계"], ["HR 데이터 분석"], "채용과 온보딩 운영"),
        ("그래픽 디자이너", ["Photoshop"], [], "비주얼팩토리", "브랜드 디자이너", ["Photoshop", "Illustrator"], ["모션 그래픽"], "브랜드 캠페인 디자인"),
        ("영업 관리", ["B2B 영업"], [], "세일즈허브", "영업 관리자", ["B2B 영업", "제안서 작성"], ["CRM 운영"], "엔터프라이즈 영업"),
    ]
    for d in comp:
        add("decision_complement", "부분 매칭에서 부족을 보유로 승격하지 않는지", d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7])

    # 7) decision_hold — 저매칭 8 (직무 전환급)
    hold = [
        ("백엔드 개발자", ["Excel"], [], "딥스택", "백엔드 개발자", ["Java", "Spring Boot", "MySQL"], ["AWS"], "서비스 API 개발"),
        ("데이터 엔지니어", ["콘텐츠 기획"], [], "빅파이프", "데이터 엔지니어", ["Python", "Spark", "Airflow"], ["Kafka"], "대용량 파이프라인 구축"),
        ("보안 엔지니어", ["CS 운영"], [], "제로트러스트", "보안 엔지니어", ["네트워크 보안", "관제"], ["CISSP"], "보안 관제 운영"),
        ("프론트엔드 개발자", ["회계 기초"], [], "픽셀그리드", "프론트엔드 개발자", ["React", "TypeScript", "CSS"], [], "웹 서비스 개발"),
        ("퍼포먼스 마케터", ["재고 관리"], [], "타겟팅랩", "퍼포먼스 마케터", ["GA4", "메타 광고", "SQL"], [], "유료 채널 운영"),
        ("회계 담당자", ["B2B 영업"], [], "밸런스시트", "회계 담당자", ["IFRS 결산", "더존 iCUBE"], ["전산회계 1급"], "결산과 감사 대응"),
        ("물류 관리자", ["Photoshop"], [], "스피드로지스", "물류 관리자", ["WMS", "SCM 기초"], ["물류관리사"], "센터 운영 관리"),
        ("게임 클라이언트 개발자", ["문서 작성"], [], "겜스튜디오", "클라이언트 개발자", ["C#", "Unity"], ["셰이더"], "게임 클라이언트 개발"),
    ]
    for d in hold:
        add("decision_hold", "저매칭에서 근거 없는 보유·낙관 서술을 하지 않는지", d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7])

    return rows


def main() -> int:
    rows = build_cases()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="\n") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"[build_a_only_baseline_fixture] cases={len(rows)} -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""
CareerTuner A파트 프로필 AI 확장 학습 데이터 생성기.

기존 30개 seed 데이터는 보존하고, 직무/산업/경험/기술 조합이 겹치지 않는
추가 샘플을 생성해 총 300개 규모의 JSONL 파일을 만듭니다.

출력:
- docs/ai-training/profile_ai_training_samples_500.jsonl

중복 방지 기준:
- jobFamily
- desiredJob
- desiredIndustry
- resumeText 핵심 문장
- selfIntro 핵심 문장
- project name/role/result
- career company/role/duties
"""

from __future__ import annotations

import importlib.util
import json
from collections import Counter
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
BASE_GENERATOR_PATH = ROOT / "generate_profile_ai_seed_samples.py"
OUTPUT_PATH = ROOT / "profile_ai_training_samples_500.jsonl"


TARGET_COUNTS = {
    "DEVELOPMENT_DATA": 65,
    "SALES_MARKETING": 65,
    "DESIGN_CONTENT": 62,
    "BUSINESS_OFFICE": 65,
    "HEALTHCARE_SERVICE": 61,
    "EDUCATION_PUBLIC": 61,
    "PRODUCTION_LOGISTICS": 61,
    "GENERAL": 60,
}


def load_base_generator():
    spec = importlib.util.spec_from_file_location("profile_seed_generator", BASE_GENERATOR_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load generator: {BASE_GENERATOR_PATH}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def compact(value: object) -> str:
    return " ".join(str(value or "").strip().lower().split())


def profile_signature(row: dict[str, Any]) -> str:
    user_input = json.loads(row["messages"][1]["content"])
    profile = user_input["profile"]
    projects = profile.get("projects", [])
    career = profile.get("career", [])
    project_parts = [
        "|".join(compact(project.get(key, "")) for key in ["name", "role", "result"])
        for project in projects
        if isinstance(project, dict)
    ]
    career_parts = [
        "|".join(compact(item.get(key, "")) for key in ["company", "role", "duties"])
        for item in career
        if isinstance(item, dict)
    ]
    return "###".join(
        [
            compact(user_input.get("jobFamily")),
            compact(profile.get("desiredJob")),
            compact(profile.get("desiredIndustry")),
            compact(profile.get("resumeText"))[:80],
            compact(profile.get("selfIntro"))[:80],
            "||".join(project_parts),
            "||".join(career_parts),
        ]
    )


def ensure_unique(rows: list[dict[str, Any]]) -> None:
    seen: dict[str, int] = {}
    for index, row in enumerate(rows, start=1):
        signature = profile_signature(row)
        if signature in seen:
            raise ValueError(f"duplicated sample at line {index}; first seen at line {seen[signature]}")
        seen[signature] = index


def build_profile(module, case: dict[str, Any], index: int) -> dict[str, Any]:
    job = case["job"]
    industry = case["industry"]
    project_name = case["project"]
    role = case["role"]
    result = case["result"]
    skills = case["skills"]
    major = case["major"]
    career_role = case.get("careerRole")
    company = case.get("company")
    duties = case.get("duties")

    career = []
    if career_role:
        career = [
            {
                "company": company or f"{industry} 실습기관",
                "role": career_role,
                "period": case.get("period", "2025-01~2025-04"),
                "duties": duties or f"{project_name} 관련 업무 보조",
            }
        ]

    education = [
        {
            "school": f"커리어{index:03d}대학교",
            "major": major,
            "graduation": case.get("graduation", "2026-02"),
        }
    ]
    projects = [
        {
            "name": project_name,
            "role": role,
            "stack": skills[:4],
            "result": result,
        }
    ]
    resume_text = f"{industry} 분야의 {project_name}에서 {role}을 담당했고, {result}."
    self_intro = f"{job}로서 {case['value']}에 기여하고 싶습니다."
    return module.profile(
        job,
        industry,
        education,
        career,
        projects,
        skills,
        resume_text,
        self_intro,
        certificates=case.get("certificates", []),
        languages=case.get("languages", []),
        portfolio_slug=f"expanded-{index:03d}-{case['slug']}",
    )


def build_sample(module, case: dict[str, Any], index: int) -> dict[str, Any]:
    profile_data = build_profile(module, case, index)
    job = case["job"]
    industry = case["industry"]
    skills = case["skills"]
    strengths = [
        f"{job} 직무와 연결되는 {skills[0]} 경험이 확인됩니다.",
        f"{case['project']}에서 본인의 역할과 산출물이 드러납니다.",
        f"{industry} 분야에 대한 관심과 지원 방향이 비교적 명확합니다.",
    ]
    gaps = [
        case["gap"],
        "성과 수치, 검증 방식, 업무 영향도를 더 구체화할 필요가 있습니다.",
    ]
    recommendations = [
        f"{case['project']}에서 사용한 자료, 도구, 판단 기준을 단계별로 정리하세요.",
        f"{case['metric']} 같은 정량 지표를 추가해 경험의 설득력을 높이세요.",
        f"{job} 공고에서 자주 요구하는 역량 3개와 현재 경험을 표로 연결하세요.",
    ]
    base = case["scoreBase"]
    scores = module.base_scores(
        base + case.get("goalDelta", 0),
        base - 3 + case.get("experienceDelta", 0),
        base - 14 + case.get("achievementDelta", 0),
        base + 1 + case.get("skillDelta", 0),
        base - 5 + case.get("documentDelta", 0),
        base + 2 + case.get("improvementDelta", 0),
    )
    evidence = module.base_evidence(
        f"{job}와 {industry} 목표가 프로필에 명시되어 있습니다.",
        f"{case['project']}에서 {case['role']}을 수행한 경험이 있습니다.",
        f"{case['result']}라는 산출물은 있으나 결과 영향도는 더 보강해야 합니다.",
        f"{', '.join(skills[:4])} 역량이 {job} 직무와 연결됩니다.",
        "이력서와 자기소개가 같은 경험을 중심으로 이어집니다.",
        "보완할 항목이 지표, 근거, 포트폴리오 정리로 구체화 가능합니다.",
    )
    improvement = module.base_improvement(
        f"{job} 중에서도 관심 세부 업무를 한 문장으로 좁히세요.",
        "역할, 사용 도구, 문제 상황, 해결 과정을 순서대로 추가하세요.",
        f"{case['metric']}를 실제 숫자 또는 전후 비교로 보강하세요.",
        f"{skills[0]}와 {skills[1]}을 사용한 이유를 직무 요구와 연결하세요.",
        "프로젝트 설명, 포트폴리오, 자기소개 문구의 용어를 통일하세요.",
        "다음 보완 작업을 1주 단위 체크리스트로 정리하세요.",
    )
    return module.sample(
        case["family"],
        profile_data,
        f"{job} 직무와 연결되는 경험이 확인됩니다. 다만 {case['gap']} 부분을 보강하면 프로필 완성도가 더 높아집니다.",
        skills[:6],
        strengths,
        gaps,
        recommendations,
        scores,
        evidence,
        improvement,
    )


CASES = [
    # DEVELOPMENT_DATA: 9
    {"family": "DEVELOPMENT_DATA", "job": "앱 백엔드 개발자", "industry": "헬스케어 플랫폼", "major": "소프트웨어학", "project": "예약 관리 API", "role": "예약 생성과 취소 API 구현", "result": "중복 예약 방지 로직을 구현", "skills": ["Java", "Spring Boot", "MySQL", "Redis", "JUnit", "Git"], "value": "안정적인 예약 흐름", "gap": "동시성 테스트 근거가 부족한", "metric": "동시 요청 처리 결과", "scoreBase": 80, "slug": "health-reservation-api"},
    {"family": "DEVELOPMENT_DATA", "job": "데이터 엔지니어", "industry": "교육 서비스", "major": "데이터사이언스", "project": "학습 로그 ETL", "role": "Python 배치와 SQL 집계 작성", "result": "학습 이벤트를 일 단위 테이블로 적재", "skills": ["Python", "SQL", "Airflow", "PostgreSQL", "Pandas", "Git"], "value": "학습 데이터 활용 기반", "gap": "파이프라인 장애 대응 설명이 부족한", "metric": "배치 처리 시간과 실패 건수", "scoreBase": 82, "slug": "learning-log-etl"},
    {"family": "DEVELOPMENT_DATA", "job": "웹 퍼블리셔", "industry": "브랜드 커머스", "major": "디지털미디어", "project": "반응형 상품 상세 페이지", "role": "HTML/CSS 마크업과 접근성 점검", "result": "모바일 레이아웃 깨짐 사례를 수정", "skills": ["HTML", "CSS", "JavaScript", "Figma", "웹 접근성", "Git"], "value": "읽기 쉬운 구매 화면", "gap": "접근성 검사 결과 수치가 부족한", "metric": "Lighthouse 접근성 점수", "scoreBase": 77, "slug": "responsive-product-page"},
    {"family": "DEVELOPMENT_DATA", "job": "보안 관제 주니어", "industry": "금융 보안", "major": "정보보호학", "project": "로그 이상 징후 분류", "role": "로그 유형 정리와 룰 초안 작성", "result": "의심 로그인 패턴 5개를 분류", "skills": ["Linux", "SQL", "보안 로그", "Python", "SIEM 기초", "네트워크"], "value": "위험 신호 조기 탐지", "gap": "오탐과 미탐 비교 기준이 부족한", "metric": "탐지 룰별 오탐률", "scoreBase": 78, "slug": "security-log-rule"},
    {"family": "DEVELOPMENT_DATA", "job": "머신러닝 엔지니어", "industry": "제조 검사", "major": "인공지능학", "project": "불량 이미지 분류 실험", "role": "데이터 전처리와 모델 성능 비교", "result": "CNN baseline과 augmentation 결과를 비교", "skills": ["Python", "PyTorch", "OpenCV", "Pandas", "CNN", "데이터 전처리"], "value": "검사 자동화 품질 개선", "gap": "데이터 분할과 검증 기준 설명이 부족한", "metric": "precision, recall, confusion matrix", "scoreBase": 81, "slug": "defect-image-ml"},
    {"family": "DEVELOPMENT_DATA", "job": "DevOps 엔지니어", "industry": "B2B SaaS", "major": "컴퓨터공학", "project": "CI/CD 파이프라인 구성", "role": "GitHub Actions workflow 작성", "result": "테스트와 빌드 자동 실행을 구성", "skills": ["GitHub Actions", "Docker", "Linux", "Nginx", "Shell Script", "AWS 기초"], "value": "배포 반복 작업 자동화", "gap": "실패 알림과 롤백 절차가 부족한", "metric": "배포 소요 시간과 실패 복구 시간", "scoreBase": 79, "slug": "ci-cd-workflow"},
    {"family": "DEVELOPMENT_DATA", "job": "게임 클라이언트 개발자", "industry": "모바일 게임", "major": "게임공학", "project": "2D 퍼즐 게임 프로토타입", "role": "Unity UI와 스테이지 로직 구현", "result": "스테이지 12개와 저장 기능을 구현", "skills": ["Unity", "C#", "게임 UI", "Git", "ScriptableObject", "모바일 최적화"], "value": "안정적인 플레이 경험", "gap": "성능 최적화 측정 결과가 부족한", "metric": "FPS와 메모리 사용량", "scoreBase": 80, "slug": "unity-puzzle"},
    {"family": "DEVELOPMENT_DATA", "job": "데이터 시각화 개발자", "industry": "공공 데이터", "major": "정보시각화", "project": "지역 인구 대시보드", "role": "차트 컴포넌트와 필터 구현", "result": "연령대와 지역별 인구 변화를 시각화", "skills": ["React", "D3.js", "TypeScript", "공공데이터", "데이터 시각화", "CSV 전처리"], "value": "데이터 기반 의사결정", "gap": "사용자 해석 흐름 설명이 부족한", "metric": "필터 응답 시간과 차트 가독성 피드백", "scoreBase": 78, "slug": "public-data-viz"},
    {"family": "DEVELOPMENT_DATA", "job": "DBA 주니어", "industry": "물류 시스템", "major": "정보시스템", "project": "주문 조회 SQL 개선", "role": "인덱스 후보 조사와 실행 계획 비교", "result": "느린 조회 쿼리의 원인을 정리", "skills": ["MySQL", "SQL 튜닝", "인덱스", "ERD", "실행 계획", "Linux"], "value": "안정적인 데이터 조회 성능", "gap": "개선 전후 성능 수치가 부족한", "metric": "쿼리 실행 시간 전후 비교", "scoreBase": 79, "slug": "mysql-query-plan"},

    # SALES_MARKETING: 9
    {"family": "SALES_MARKETING", "job": "제휴 마케터", "industry": "여행 플랫폼", "major": "관광경영학", "project": "지역 숙박 제휴 제안서", "role": "제휴 후보 조사와 제안서 목차 작성", "result": "제휴 후보 30곳을 유형별로 분류", "skills": ["제휴 제안", "시장 조사", "Excel", "PowerPoint", "CRM 기초", "커뮤니케이션"], "value": "파트너와 사용자 가치를 연결", "gap": "제안 후 반응이나 전환 지표가 부족한", "metric": "제안 발송 수와 회신율", "scoreBase": 78, "slug": "travel-partnership"},
    {"family": "SALES_MARKETING", "job": "브랜드 커뮤니케이션 담당자", "industry": "생활용품", "major": "광고홍보학", "project": "신제품 메시지 테스트", "role": "카피안 5개와 설문 문항 작성", "result": "응답자 80명의 선호 메시지를 정리", "skills": ["브랜드 메시지", "카피라이팅", "설문 분석", "Notion", "SNS", "PowerPoint"], "value": "일관된 브랜드 인식", "gap": "메시지 선택 근거와 후속 실행이 부족한", "metric": "메시지별 선호율과 클릭률", "scoreBase": 80, "slug": "brand-message-test"},
    {"family": "SALES_MARKETING", "job": "리테일 MD 보조", "industry": "패션 편집숍", "major": "패션비즈니스", "project": "시즌 상품 구성안", "role": "경쟁 브랜드 가격과 상품군 조사", "result": "상품 120개의 가격대를 구간별로 정리", "skills": ["상품 기획", "시장 조사", "Excel", "가격 분석", "트렌드 리서치", "커뮤니케이션"], "value": "고객 수요에 맞는 상품 구성", "gap": "판매 데이터와 재고 회전 지표가 부족한", "metric": "판매율과 재고 회전율", "scoreBase": 79, "slug": "retail-md-season"},
    {"family": "SALES_MARKETING", "job": "광고 운영 AE", "industry": "디지털 광고 대행사", "major": "미디어커뮤니케이션", "project": "검색광고 운영 실습", "role": "키워드 그룹과 소재 초안 작성", "result": "키워드 60개를 의도별로 분류", "skills": ["검색광고", "키워드 리서치", "GA4", "Excel", "광고 소재", "성과 리포트"], "value": "광고 성과 개선", "gap": "CPC, CTR 같은 성과 해석이 부족한", "metric": "CTR, CPC, 전환율", "scoreBase": 81, "slug": "search-ad-ae"},
    {"family": "SALES_MARKETING", "job": "고객 성공 매니저", "industry": "협업툴 SaaS", "major": "경영정보학", "project": "온보딩 가이드 개선", "role": "자주 묻는 질문 분류와 안내 문구 작성", "result": "문의 90건을 7개 유형으로 분류", "skills": ["고객 온보딩", "VOC 분석", "Notion", "CRM", "문서화", "SaaS 이해"], "value": "고객의 초기 적응률 향상", "gap": "온보딩 전후 사용률 지표가 부족한", "metric": "활성 사용자 비율과 문의 감소율", "scoreBase": 82, "slug": "saas-csm-onboarding"},
    {"family": "SALES_MARKETING", "job": "이벤트 마케터", "industry": "문화 행사", "major": "문화콘텐츠학", "project": "지역 전시 홍보 캠페인", "role": "홍보 채널 운영과 참여자 설문 정리", "result": "홍보 게시물 12개와 설문 응답 110건을 정리", "skills": ["이벤트 홍보", "SNS 운영", "설문 분석", "Canva", "콘텐츠 기획", "현장 운영"], "value": "행사 참여 경험 개선", "gap": "채널별 유입 효과 비교가 부족한", "metric": "채널별 신청자 수와 참여율", "scoreBase": 79, "slug": "event-marketing"},
    {"family": "SALES_MARKETING", "job": "인사이드 세일즈", "industry": "HR 솔루션", "major": "경영학", "project": "리드 분류 기준 정리", "role": "잠재 고객 문의 유형 분류", "result": "문의 150건을 우선순위별로 분류", "skills": ["리드 관리", "CRM", "B2B 세일즈", "Excel", "콜 스크립트", "고객 니즈 파악"], "value": "효율적인 영업 기회 발굴", "gap": "실제 전환율이나 미팅 생성률이 부족한", "metric": "미팅 전환율과 리드 응답률", "scoreBase": 80, "slug": "inside-sales-lead"},
    {"family": "SALES_MARKETING", "job": "마케팅 데이터 분석 보조", "industry": "구독 서비스", "major": "응용통계학", "project": "이탈 고객 특성 분석", "role": "구독 해지 사유와 사용 패턴 정리", "result": "해지 설문 200건을 6개 사유로 분류", "skills": ["SQL", "Excel", "GA4", "코호트 분석", "설문 분석", "리포팅"], "value": "고객 유지율 개선", "gap": "분석 결과가 캠페인으로 이어진 사례가 부족한", "metric": "이탈률과 재구독률", "scoreBase": 82, "slug": "subscription-churn"},
    {"family": "SALES_MARKETING", "job": "해외영업 주니어", "industry": "화장품 수출", "major": "국제통상학", "project": "동남아 바이어 리스트 조사", "role": "국가별 유통 채널과 바이어 정보 정리", "result": "잠재 바이어 50곳을 국가별로 분류", "skills": ["영어 이메일", "시장 조사", "Excel", "무역 기초", "바이어 리서치", "PowerPoint"], "value": "해외 판로 확대", "gap": "실제 커뮤니케이션 결과와 거래 조건 이해가 부족한", "metric": "회신율과 샘플 요청 건수", "scoreBase": 78, "slug": "global-sales-beauty"},

    # DESIGN_CONTENT: 9
    {"family": "DESIGN_CONTENT", "job": "서비스 UX 리서처", "industry": "모빌리티 앱", "major": "심리학", "project": "앱 예약 흐름 사용성 조사", "role": "사용자 인터뷰 질문지와 인사이트 정리", "result": "인터뷰 8건에서 불편 지점 12개를 도출", "skills": ["사용자 인터뷰", "UX 리서치", "Figma", "Affinity Diagram", "설문 설계", "리포트 작성"], "value": "사용자 문제 발견", "gap": "개선안 적용 후 검증 결과가 부족한", "metric": "과업 성공률과 만족도 점수", "scoreBase": 82, "slug": "ux-research-mobility"},
    {"family": "DESIGN_CONTENT", "job": "그래픽 디자이너", "industry": "식음료 브랜드", "major": "시각디자인", "project": "카페 시즌 메뉴 포스터", "role": "키비주얼과 인쇄 시안 제작", "result": "포스터 4종과 SNS 배너 6종 제작", "skills": ["Photoshop", "Illustrator", "브랜드 디자인", "인쇄물", "SNS 배너", "타이포그래피"], "value": "브랜드 분위기 전달", "gap": "디자인 의사결정 근거와 성과 지표가 부족한", "metric": "게시물 반응률과 매장 노출 피드백", "scoreBase": 80, "slug": "fnb-poster-design"},
    {"family": "DESIGN_CONTENT", "job": "콘텐츠 에디터", "industry": "라이프스타일 미디어", "major": "국어국문학", "project": "로컬 브랜드 인터뷰 기사", "role": "질문 구성과 기사 초안 작성", "result": "인터뷰 기사 3편을 작성", "skills": ["기사 작성", "인터뷰", "콘텐츠 기획", "SEO 기초", "Notion", "교정교열"], "value": "읽기 쉬운 브랜드 스토리 전달", "gap": "조회수나 체류시간 같은 콘텐츠 성과가 부족한", "metric": "조회수, 체류시간, 공유 수", "scoreBase": 81, "slug": "local-brand-editor"},
    {"family": "DESIGN_CONTENT", "job": "모션그래픽 디자이너", "industry": "교육 콘텐츠", "major": "영상디자인", "project": "강의 인트로 모션 제작", "role": "스토리보드와 애니메이션 제작", "result": "15초 인트로 영상 5개 제작", "skills": ["After Effects", "Premiere Pro", "스토리보드", "모션그래픽", "영상 편집", "사운드 편집"], "value": "학습 몰입도 향상", "gap": "시청 지속률이나 학습자 반응이 부족한", "metric": "완주율과 시청 지속 시간", "scoreBase": 79, "slug": "edu-motion-intro"},
    {"family": "DESIGN_CONTENT", "job": "제품 UI 디자이너", "industry": "핀테크 앱", "major": "산업디자인", "project": "가계부 입력 화면 개선", "role": "와이어프레임과 프로토타입 제작", "result": "입력 단계 5개를 3개로 줄인 시안 제작", "skills": ["Figma", "프로토타이핑", "UI 디자인", "디자인 시스템", "사용자 흐름", "와이어프레임"], "value": "복잡한 금융 입력 흐름 단순화", "gap": "사용성 테스트와 접근성 검증이 부족한", "metric": "입력 완료 시간과 오류율", "scoreBase": 83, "slug": "fintech-ui-input"},
    {"family": "DESIGN_CONTENT", "job": "브랜드 콘텐츠 기획자", "industry": "친환경 제품", "major": "콘텐츠마케팅", "project": "제로웨이스트 캠페인 콘텐츠", "role": "콘텐츠 캘린더와 카드뉴스 기획", "result": "4주 분량 콘텐츠 16개를 기획", "skills": ["콘텐츠 캘린더", "카드뉴스", "브랜드 스토리", "SNS 기획", "Canva", "카피라이팅"], "value": "브랜드 가치 확산", "gap": "콘텐츠별 반응 분석과 후속 개선이 부족한", "metric": "저장 수, 공유 수, 팔로워 증가율", "scoreBase": 80, "slug": "eco-content-plan"},
    {"family": "DESIGN_CONTENT", "job": "공간 디자이너 보조", "industry": "전시 기획", "major": "실내건축", "project": "소규모 팝업 전시 동선안", "role": "동선 스케치와 배치안 작성", "result": "전시 구역 5개와 안내 사인 위치를 제안", "skills": ["SketchUp", "공간 배치", "동선 설계", "전시 기획", "도면 읽기", "프레젠테이션"], "value": "방문자 경험 개선", "gap": "현장 제약 조건과 예산 고려가 부족한", "metric": "방문자 흐름 관찰 결과와 체류 시간", "scoreBase": 78, "slug": "popup-space-design"},
    {"family": "DESIGN_CONTENT", "job": "웹툰 콘텐츠 PD", "industry": "디지털 콘텐츠", "major": "스토리텔링", "project": "단편 웹툰 기획안", "role": "세계관과 회차 구성안 작성", "result": "8화 분량 시놉시스와 캐릭터 설정을 작성", "skills": ["스토리 기획", "캐릭터 설정", "콘텐츠 분석", "독자 타깃", "Notion", "일정 관리"], "value": "독자가 따라가기 쉬운 연재 구조", "gap": "시장 분석과 독자 반응 검증이 부족한", "metric": "타깃 독자 반응과 유사작 비교 지표", "scoreBase": 79, "slug": "webtoon-pd-plan"},
    {"family": "DESIGN_CONTENT", "job": "사운드 콘텐츠 제작자", "industry": "오디오북", "major": "음향제작", "project": "짧은 오디오북 샘플 제작", "role": "녹음 편집과 노이즈 정리", "result": "3분 샘플 6개를 제작", "skills": ["Audition", "녹음 편집", "노이즈 제거", "사운드 믹싱", "콘텐츠 제작", "대본 이해"], "value": "듣기 편한 오디오 경험", "gap": "청취자 피드백과 품질 기준이 부족한", "metric": "노이즈 레벨과 청취 만족도", "scoreBase": 77, "slug": "audiobook-sound"},

    # BUSINESS_OFFICE: 9
    {"family": "BUSINESS_OFFICE", "job": "서비스 운영 매니저", "industry": "배달 플랫폼", "major": "경영학", "project": "파트너 문의 유형 분석", "role": "문의 유형 분류와 처리 가이드 정리", "result": "문의 180건을 9개 유형으로 분류", "skills": ["서비스 운영", "VOC 분석", "Excel", "Notion", "운영 가이드", "커뮤니케이션"], "value": "반복 문의 감소", "gap": "처리 시간 개선 지표가 부족한", "metric": "평균 처리 시간과 재문의율", "scoreBase": 82, "slug": "ops-voc-guide"},
    {"family": "BUSINESS_OFFICE", "job": "총무 담당자", "industry": "중소 제조사", "major": "행정학", "project": "비품 관리대장 정비", "role": "비품 목록 표준화와 신청 양식 정리", "result": "비품 220개를 카테고리별로 정리", "skills": ["총무", "문서 관리", "Excel", "자산 관리", "구매 요청", "커뮤니케이션"], "value": "사내 업무 지원 효율화", "gap": "비용 절감이나 처리 속도 지표가 부족한", "metric": "구매 처리 기간과 재고 누락률", "scoreBase": 78, "slug": "office-asset-admin"},
    {"family": "BUSINESS_OFFICE", "job": "회계 보조", "industry": "비영리 단체", "major": "회계학", "project": "영수증 증빙 정리", "role": "비용 항목 분류와 증빙 누락 확인", "result": "증빙 300건을 계정과목별로 분류", "skills": ["회계 기초", "전표 정리", "Excel", "증빙 관리", "더존 기초", "계정과목"], "value": "정확한 비용 처리", "gap": "분개 판단 근거와 회계 시스템 사용 경험이 부족한", "metric": "증빙 누락률과 처리 건수", "scoreBase": 80, "slug": "accounting-receipt"},
    {"family": "BUSINESS_OFFICE", "job": "인사 교육 담당자", "industry": "IT 서비스", "major": "교육학", "project": "신입 온보딩 교육안", "role": "교육 일정표와 체크리스트 작성", "result": "입사 첫 주 교육 항목 25개를 정리", "skills": ["교육 운영", "HRD", "온보딩", "Notion", "설문 설계", "일정 관리"], "value": "신입의 빠른 적응", "gap": "교육 효과 측정 방식이 부족한", "metric": "교육 만족도와 적응 기간", "scoreBase": 81, "slug": "hrd-onboarding"},
    {"family": "BUSINESS_OFFICE", "job": "구매 관리 주니어", "industry": "식품 유통", "major": "무역학", "project": "공급사 견적 비교표", "role": "견적 조건과 납기 정보 정리", "result": "공급사 12곳의 단가와 납기를 비교", "skills": ["구매 관리", "견적 비교", "Excel", "납기 관리", "공급사 커뮤니케이션", "원가 기초"], "value": "안정적인 공급과 비용 관리", "gap": "협상 결과와 품질 조건 검토가 부족한", "metric": "단가 차이, 납기 준수율", "scoreBase": 79, "slug": "purchase-vendor"},
    {"family": "BUSINESS_OFFICE", "job": "PMO 보조", "industry": "SI 프로젝트", "major": "정보시스템", "project": "프로젝트 이슈 로그 정리", "role": "이슈 상태와 담당자 업데이트", "result": "이슈 70건을 상태별로 관리", "skills": ["프로젝트 관리", "Jira", "Excel", "회의록", "이슈 관리", "일정 관리"], "value": "프로젝트 진행 상황 가시화", "gap": "리스크 판단과 일정 영향 분석이 부족한", "metric": "이슈 처리 기간과 지연 건수", "scoreBase": 80, "slug": "pmo-issue-log"},
    {"family": "BUSINESS_OFFICE", "job": "경영기획 인턴", "industry": "콘텐츠 플랫폼", "major": "경제학", "project": "월간 지표 리포트", "role": "사용자 지표와 매출 지표 정리", "result": "MAU, 결제율, ARPU 변화를 정리", "skills": ["경영기획", "Excel", "지표 분석", "PowerPoint", "시장 조사", "리포팅"], "value": "의사결정 자료 품질 개선", "gap": "지표 변화 원인 해석이 부족한", "metric": "MAU, 결제율, ARPU 변화율", "scoreBase": 82, "slug": "business-kpi-report"},
    {"family": "BUSINESS_OFFICE", "job": "법무 사무 보조", "industry": "스타트업", "major": "법학", "project": "계약서 조항 체크리스트", "role": "계약 유형별 확인 항목 정리", "result": "NDA와 용역계약 체크 항목 35개를 정리", "skills": ["계약서 검토 보조", "문서 관리", "법무 행정", "Excel", "체크리스트", "커뮤니케이션"], "value": "계약 리스크 사전 확인", "gap": "실제 검토 사례와 법적 판단 범위 구분이 부족한", "metric": "검토 소요 시간과 누락 항목 수", "scoreBase": 78, "slug": "legal-contract-check"},
    {"family": "BUSINESS_OFFICE", "job": "데이터 기반 기획자", "industry": "공공 서비스", "major": "정책학", "project": "민원 데이터 분류 리포트", "role": "민원 키워드와 유형 분류", "result": "민원 500건을 10개 주제로 분류", "skills": ["서비스 기획", "데이터 정리", "Excel", "정책 분석", "키워드 분류", "리포트 작성"], "value": "공공 서비스 개선 방향 제시", "gap": "분류 결과가 개선안으로 이어지는 과정이 부족한", "metric": "민원 빈도 변화와 처리 만족도", "scoreBase": 81, "slug": "public-service-planner"},

    # HEALTHCARE_SERVICE: 10
    {"family": "HEALTHCARE_SERVICE", "job": "병동 간호사", "industry": "종합병원", "major": "간호학", "project": "투약 확인 체크리스트 실습", "role": "투약 전 확인 항목 정리", "result": "투약 확인 항목 20개를 상황별로 정리", "skills": ["간호 술기", "투약 안전", "환자 관찰", "EMR 기초", "체크리스트", "의사소통"], "value": "환자 안전", "gap": "실습 상황별 대응 사례가 부족한", "metric": "확인 누락 방지 사례와 실습 평가", "scoreBase": 82, "slug": "nurse-medication-check"},
    {"family": "HEALTHCARE_SERVICE", "job": "병원 원무 담당자", "industry": "전문병원", "major": "보건행정학", "project": "접수 대기 유형 정리", "role": "환자 접수 문의와 대기 사유 분류", "result": "문의 160건을 8개 유형으로 분류", "skills": ["원무 행정", "접수 응대", "Excel", "보험 기초", "EMR 기초", "민원 응대"], "value": "환자 접수 흐름 개선", "gap": "처리 시간과 환자 만족도 지표가 부족한", "metric": "대기 시간과 재문의율", "scoreBase": 79, "slug": "hospital-admin-waiting"},
    {"family": "HEALTHCARE_SERVICE", "job": "임상병리사", "industry": "검진센터", "major": "임상병리학", "project": "검체 라벨링 오류 예방안", "role": "검체 확인 절차와 오류 사례 정리", "result": "라벨 확인 항목 15개를 체크리스트로 작성", "skills": ["검체 관리", "라벨링", "품질관리", "Excel", "검사 프로세스", "안전관리"], "value": "검사 정확도 유지", "gap": "실제 오류 감소 근거가 부족한", "metric": "라벨링 오류 건수와 재검률", "scoreBase": 81, "slug": "lab-sample-label"},
    {"family": "HEALTHCARE_SERVICE", "job": "물리치료 보조", "industry": "재활병원", "major": "물리치료학", "project": "운동 프로그램 기록표", "role": "환자별 운동 수행 기록 정리", "result": "운동 수행 항목 18개를 기록표로 구성", "skills": ["재활 운동", "환자 기록", "운동 처방 보조", "Excel", "상담", "안전관리"], "value": "재활 과정 추적", "gap": "환자 상태 변화와 프로그램 효과 설명이 부족한", "metric": "운동 수행률과 통증 변화", "scoreBase": 78, "slug": "rehab-exercise-log"},
    {"family": "HEALTHCARE_SERVICE", "job": "의료기기 영업지원", "industry": "의료기기", "major": "의공학", "project": "제품 문의 대응 자료", "role": "제품 기능 FAQ와 비교표 작성", "result": "문의 70건을 기능별로 분류", "skills": ["의료기기 이해", "FAQ 작성", "Excel", "고객 응대", "제품 비교", "문서화"], "value": "의료진의 제품 이해 지원", "gap": "규제와 임상 근거 이해가 부족한", "metric": "문의 해결률과 응답 시간", "scoreBase": 80, "slug": "medical-device-sales-support"},
    {"family": "HEALTHCARE_SERVICE", "job": "상담 콜센터 상담사", "industry": "건강검진 서비스", "major": "상담심리학", "project": "예약 상담 스크립트 개선", "role": "반복 문의 유형과 응대 문구 정리", "result": "상담 120건을 6개 유형으로 분류", "skills": ["상담 응대", "예약 안내", "스크립트 작성", "VOC 분석", "Excel", "감정 노동 관리"], "value": "정확하고 안정적인 고객 안내", "gap": "상담 품질 평가와 재문의 감소 지표가 부족한", "metric": "평균 통화 시간과 재문의율", "scoreBase": 79, "slug": "health-call-script"},
    {"family": "HEALTHCARE_SERVICE", "job": "치위생사", "industry": "치과의원", "major": "치위생학", "project": "구강관리 교육 자료", "role": "환자 안내 카드뉴스 작성", "result": "스케일링 후 관리 안내 자료 5종 제작", "skills": ["구강보건교육", "환자 안내", "Canva", "진료 보조", "위생관리", "상담"], "value": "환자의 구강관리 실천", "gap": "교육 후 행동 변화나 만족도 지표가 부족한", "metric": "교육 만족도와 재방문 상담 내용", "scoreBase": 80, "slug": "dental-education"},
    {"family": "HEALTHCARE_SERVICE", "job": "요양보호 행정 보조", "industry": "노인복지시설", "major": "사회복지학", "project": "입소자 활동 기록 정리", "role": "활동 기록 양식과 주간 보고서 작성", "result": "입소자 40명의 활동 기록을 주제별로 정리", "skills": ["복지 행정", "기록 관리", "Excel", "상담 보조", "프로그램 운영", "문서화"], "value": "돌봄 서비스 기록 품질 개선", "gap": "개인정보 관리와 기록 기준 설명이 부족한", "metric": "기록 누락률과 프로그램 참여율", "scoreBase": 78, "slug": "care-admin-record"},
    {"family": "HEALTHCARE_SERVICE", "job": "보건교육사", "industry": "지역 보건소", "major": "보건학", "project": "금연 교육 캠페인안", "role": "교육 대상과 메시지 구성", "result": "대상자별 교육 메시지 10개를 작성", "skills": ["보건교육", "캠페인 기획", "설문 설계", "자료 제작", "지역사회 보건", "Excel"], "value": "건강 행동 변화 유도", "gap": "교육 효과 측정 설계가 부족한", "metric": "참여율과 사전·사후 인식 변화", "scoreBase": 81, "slug": "public-health-education"},
    {"family": "HEALTHCARE_SERVICE", "job": "약국 전산 보조", "industry": "지역 약국", "major": "보건의료정보", "project": "처방 접수 오류 유형 정리", "role": "전산 입력 오류 사례 기록", "result": "오류 55건을 입력 단계별로 분류", "skills": ["처방 접수", "전산 입력", "고객 응대", "Excel", "의약품 기초", "오류 확인"], "value": "정확한 처방 처리 지원", "gap": "오류 예방 절차와 개인정보 처리 기준이 부족한", "metric": "입력 오류 건수와 재확인 소요 시간", "scoreBase": 77, "slug": "pharmacy-data-entry"},

    # EDUCATION_PUBLIC: 9
    {"family": "EDUCATION_PUBLIC", "job": "평생교육사", "industry": "지자체 교육센터", "major": "평생교육학", "project": "성인 학습 프로그램 기획", "role": "수강생 요구 조사와 과정안 작성", "result": "설문 130건을 5개 학습 수요로 분류", "skills": ["교육 프로그램 기획", "설문 분석", "Excel", "홍보물 작성", "수강생 관리", "평생교육"], "value": "지역 주민의 학습 참여 확대", "gap": "프로그램 운영 후 만족도 지표가 부족한", "metric": "신청률과 수료율", "scoreBase": 81, "slug": "lifelong-education"},
    {"family": "EDUCATION_PUBLIC", "job": "도서관 사서 보조", "industry": "공공도서관", "major": "문헌정보학", "project": "추천 도서 큐레이션", "role": "주제별 도서 목록과 소개 문구 작성", "result": "청소년 추천 도서 40권을 주제별로 분류", "skills": ["자료 분류", "도서 큐레이션", "Excel", "이용자 응대", "홍보물 작성", "문헌정보"], "value": "이용자 맞춤 정보 제공", "gap": "대출 증가나 이용자 반응 지표가 부족한", "metric": "전시 도서 대출률과 이용자 피드백", "scoreBase": 79, "slug": "library-curation"},
    {"family": "EDUCATION_PUBLIC", "job": "청소년 지도사", "industry": "청소년센터", "major": "청소년학", "project": "진로 탐색 프로그램", "role": "활동지와 진행 시나리오 작성", "result": "2시간 프로그램 활동지 6종 제작", "skills": ["프로그램 운영", "청소년 상담", "활동지 제작", "안전관리", "설문", "진로 교육"], "value": "청소년 자기이해 지원", "gap": "참여자 변화와 안전 대응 사례가 부족한", "metric": "참여 만족도와 사전·사후 자기이해 점수", "scoreBase": 80, "slug": "youth-career-program"},
    {"family": "EDUCATION_PUBLIC", "job": "교육 행정직", "industry": "대학교 행정", "major": "행정학", "project": "장학 신청 서류 검토", "role": "신청 서류 체크리스트와 누락 항목 정리", "result": "신청서 180건의 누락 유형을 분류", "skills": ["교육 행정", "문서 검토", "Excel", "민원 응대", "체크리스트", "개인정보 관리"], "value": "정확한 학생 지원 업무", "gap": "처리 기간과 오류 감소 지표가 부족한", "metric": "서류 보완 요청률과 처리 기간", "scoreBase": 80, "slug": "education-admin-scholarship"},
    {"family": "EDUCATION_PUBLIC", "job": "공무원 민원 담당", "industry": "구청 민원실", "major": "공공관리학", "project": "민원 안내 문구 개선", "role": "반복 민원 질문과 안내 문구 정리", "result": "민원 100건을 7개 유형으로 분류", "skills": ["민원 응대", "문서 작성", "Excel", "공공 행정", "안내문 작성", "갈등 조정"], "value": "명확한 행정 안내", "gap": "법령 근거와 처리 결과 지표가 부족한", "metric": "재문의율과 처리 만족도", "scoreBase": 78, "slug": "civil-service-guide"},
    {"family": "EDUCATION_PUBLIC", "job": "한국어 교육 보조", "industry": "다문화 교육기관", "major": "국어교육학", "project": "초급 한국어 활동지", "role": "어휘 활동과 예문 작성", "result": "생활 어휘 활동지 20개를 제작", "skills": ["한국어 교육", "활동지 제작", "수업 보조", "피드백", "문화 이해", "문서 작성"], "value": "학습자의 생활 의사소통 지원", "gap": "학습자 수준별 조정 사례가 부족한", "metric": "과제 제출률과 어휘 테스트 점수", "scoreBase": 81, "slug": "korean-language-assistant"},
    {"family": "EDUCATION_PUBLIC", "job": "교육 콘텐츠 운영자", "industry": "온라인 강의 플랫폼", "major": "교육공학", "project": "강의 오류 신고 처리 기준", "role": "오류 유형 분류와 처리 가이드 작성", "result": "강의 오류 신고 90건을 6개 유형으로 분류", "skills": ["LMS 운영", "콘텐츠 QA", "Excel", "고객 응대", "운영 가이드", "교육공학"], "value": "학습 콘텐츠 품질 유지", "gap": "오류 처리 시간과 학습자 영향 분석이 부족한", "metric": "평균 처리 시간과 재신고율", "scoreBase": 80, "slug": "lms-content-ops"},
    {"family": "EDUCATION_PUBLIC", "job": "정책 홍보 보조", "industry": "공공기관", "major": "언론정보학", "project": "청년 정책 카드뉴스", "role": "정책 요약과 카드뉴스 문안 작성", "result": "정책 8건을 대상자별로 요약", "skills": ["정책 요약", "카드뉴스", "보도자료", "Canva", "SNS 운영", "문서 작성"], "value": "정책 정보 접근성 향상", "gap": "정책 근거와 홍보 성과 지표가 부족한", "metric": "게시물 도달률과 링크 클릭 수", "scoreBase": 79, "slug": "policy-contents"},
    {"family": "EDUCATION_PUBLIC", "job": "학습 코치", "industry": "입시 교육", "major": "교육심리학", "project": "주간 학습 계획표 개선", "role": "학생 학습 기록 분류와 피드백 작성", "result": "학생 12명의 학습 기록을 과목별로 정리", "skills": ["학습 상담", "기록 관리", "피드백", "Excel", "교육심리", "계획표 작성"], "value": "학생의 자기주도 학습 지원", "gap": "성적 변화나 학습 습관 개선 근거가 부족한", "metric": "계획 이행률과 과목별 점수 변화", "scoreBase": 80, "slug": "learning-coach"},

    # PRODUCTION_LOGISTICS: 10
    {"family": "PRODUCTION_LOGISTICS", "job": "공정관리 보조", "industry": "자동차 부품", "major": "기계공학", "project": "작업 표준서 정비", "role": "작업 단계와 검사 항목 정리", "result": "작업 단계 28개를 표준서 형식으로 정리", "skills": ["공정관리", "작업 표준서", "Excel", "도면 기초", "품질 체크", "현장 소통"], "value": "작업 편차 감소", "gap": "표준서 적용 후 개선 지표가 부족한", "metric": "작업 시간 편차와 불량률", "scoreBase": 80, "slug": "process-standard"},
    {"family": "PRODUCTION_LOGISTICS", "job": "SCM 운영 주니어", "industry": "생활가전", "major": "공급망관리", "project": "수요 예측 오차 정리", "role": "판매 계획과 실제 출고량 비교", "result": "품목 50개의 예측 오차를 계산", "skills": ["SCM", "수요 예측", "Excel", "재고 관리", "출고 데이터", "리포팅"], "value": "재고 부족과 과잉 방지", "gap": "예측 오차 원인 분석이 부족한", "metric": "MAPE와 재고 회전일수", "scoreBase": 81, "slug": "scm-demand-error"},
    {"family": "PRODUCTION_LOGISTICS", "job": "설비보전 보조", "industry": "화학 공장", "major": "전기공학", "project": "설비 점검 기록표", "role": "점검 항목과 이상 징후 기록", "result": "설비 15대의 점검 항목을 정리", "skills": ["설비보전", "전기 기초", "점검표", "Excel", "안전관리", "현장 기록"], "value": "설비 고장 예방", "gap": "고장 이력과 예방 효과 지표가 부족한", "metric": "고장 건수와 평균 수리 시간", "scoreBase": 78, "slug": "maintenance-check"},
    {"family": "PRODUCTION_LOGISTICS", "job": "창고 재고관리", "industry": "의류 물류", "major": "물류유통학", "project": "재고 실사 차이 분석", "role": "전산 재고와 실물 재고 비교", "result": "SKU 300개의 차이 유형을 분류", "skills": ["재고관리", "WMS", "Excel", "SKU 관리", "실사", "바코드"], "value": "재고 정확도 향상", "gap": "차이 발생 원인과 개선 조치가 부족한", "metric": "재고 정확도와 조정 건수", "scoreBase": 80, "slug": "inventory-audit"},
    {"family": "PRODUCTION_LOGISTICS", "job": "품질보증 QA", "industry": "화장품 제조", "major": "화학공학", "project": "원료 입고 검사 기록", "role": "검사 기준과 부적합 사례 정리", "result": "원료 검사 항목 22개를 기준별로 분류", "skills": ["품질보증", "원료 검사", "GMP 기초", "Excel", "문서 관리", "부적합 관리"], "value": "제품 품질 리스크 예방", "gap": "부적합 처리 절차와 개선 결과가 부족한", "metric": "부적합률과 재검사 건수", "scoreBase": 81, "slug": "cosmetic-qa-material"},
    {"family": "PRODUCTION_LOGISTICS", "job": "출고 운영 담당", "industry": "새벽배송", "major": "물류학", "project": "출고 지연 사유 분석", "role": "지연 주문 유형과 시간대 정리", "result": "지연 주문 120건을 5개 원인으로 분류", "skills": ["출고 운영", "WMS", "Excel", "피킹", "패킹", "운영 리포트"], "value": "정시 출고율 향상", "gap": "개선안 적용 후 정시율 변화가 부족한", "metric": "정시 출고율과 피킹 처리량", "scoreBase": 80, "slug": "shipment-delay"},
    {"family": "PRODUCTION_LOGISTICS", "job": "구매 물류 코디네이터", "industry": "수입 식자재", "major": "국제물류학", "project": "수입 일정 관리표", "role": "선적 일정과 입고 예정일 정리", "result": "수입 건 40개의 일정 변동을 정리", "skills": ["국제물류", "구매 일정", "Excel", "인코텀즈 기초", "통관 기초", "납기 관리"], "value": "안정적인 입고 일정 관리", "gap": "지연 원인과 비용 영향 분석이 부족한", "metric": "납기 준수율과 지연 일수", "scoreBase": 79, "slug": "import-schedule"},
    {"family": "PRODUCTION_LOGISTICS", "job": "안전관리 보조", "industry": "건설 현장", "major": "산업안전학", "project": "위험성 평가 체크리스트", "role": "작업별 위험 요인과 예방 조치 정리", "result": "작업 20개의 위험 요인을 분류", "skills": ["산업안전", "위험성 평가", "체크리스트", "Excel", "현장 점검", "안전교육"], "value": "사고 예방", "gap": "점검 후 개선 조치와 사고 감소 지표가 부족한", "metric": "위험요인 조치율과 아차사고 건수", "scoreBase": 81, "slug": "safety-risk-check"},
    {"family": "PRODUCTION_LOGISTICS", "job": "패키징 개발 보조", "industry": "식품 패키징", "major": "포장공학", "project": "포장재 비교 테스트", "role": "포장재별 강도와 비용 비교", "result": "포장재 8종의 특성을 표로 정리", "skills": ["포장재", "비용 비교", "Excel", "품질 테스트", "식품 포장", "리포트 작성"], "value": "제품 보호와 비용 균형", "gap": "테스트 조건과 결과 검증이 부족한", "metric": "파손률과 포장 단가", "scoreBase": 79, "slug": "packaging-test"},
    {"family": "PRODUCTION_LOGISTICS", "job": "라인 리더 후보", "industry": "전자 조립", "major": "산업경영공학", "project": "작업자 배치표 개선", "role": "작업 숙련도와 공정별 배치 기록", "result": "작업자 25명의 공정 경험을 표로 정리", "skills": ["라인 운영", "작업 배치", "Excel", "공정 이해", "현장 소통", "생산성 관리"], "value": "라인 생산성 안정화", "gap": "배치 변경 후 생산성 지표가 부족한", "metric": "시간당 생산량과 병목 시간", "scoreBase": 80, "slug": "line-worker-allocation"},

    # GENERAL: 5
    {"family": "GENERAL", "job": "직무 탐색 중인 신입", "industry": "사무/운영", "major": "사회학", "project": "동아리 운영 기록 정리", "role": "회의록과 예산 사용 내역 정리", "result": "월별 활동 기록 12건을 문서화", "skills": ["문서 작성", "회의록", "Excel", "예산 정리", "협업", "발표"], "value": "조직 운영 지원", "gap": "희망 직무가 넓고 핵심 역량 선택이 부족한", "metric": "처리 문서 수와 일정 준수율", "scoreBase": 57, "slug": "general-office-explore"},
    {"family": "GENERAL", "job": "서비스 직무 탐색자", "industry": "고객 응대", "major": "호텔관광학", "project": "고객 응대 사례 정리", "role": "불편 접수 유형과 응대 문구 정리", "result": "응대 사례 40건을 유형별로 분류", "skills": ["고객 응대", "상황 대응", "Excel", "문서화", "커뮤니케이션", "서비스 마인드"], "value": "고객 경험 개선", "gap": "관심 직무와 산업 선택 이유가 부족한", "metric": "고객 만족도와 재문의율", "scoreBase": 60, "slug": "general-service-explore"},
    {"family": "GENERAL", "job": "콘텐츠/마케팅 탐색자", "industry": "미디어", "major": "문화예술경영", "project": "전시 홍보 게시물 작성", "role": "전시 소개 문구와 이미지 편집", "result": "홍보 게시물 8개를 제작", "skills": ["콘텐츠 작성", "Canva", "SNS", "문화기획", "문서 작성", "이미지 편집"], "value": "문화 콘텐츠 전달", "gap": "마케팅과 콘텐츠 중 목표 직무가 불명확한", "metric": "게시물 반응률과 방문 전환", "scoreBase": 61, "slug": "general-content-explore"},
    {"family": "GENERAL", "job": "데이터 활용 직무 탐색자", "industry": "공공/민간 혼합", "major": "수학", "project": "설문 결과 정리", "role": "응답 데이터 정리와 그래프 작성", "result": "응답 200건을 문항별로 시각화", "skills": ["Excel", "기초 통계", "그래프 작성", "자료 조사", "PowerPoint", "데이터 정리"], "value": "데이터 기반 문제 이해", "gap": "분석 직무로 발전시키기 위한 도구 경험이 부족한", "metric": "분석 질문 수와 인사이트 반영 사례", "scoreBase": 63, "slug": "general-data-explore"},
    {"family": "GENERAL", "job": "공공/교육 직무 탐색자", "industry": "지역사회", "major": "사회복지학", "project": "봉사활동 운영 보조", "role": "참여자 명단과 활동 사진 정리", "result": "봉사 참여자 60명의 활동 기록을 정리", "skills": ["명단 관리", "봉사 운영", "문서 작성", "Excel", "대인 소통", "기록 관리"], "value": "지역사회 활동 지원", "gap": "공공, 교육, 복지 중 세부 방향이 부족한", "metric": "참여율과 활동 만족도", "scoreBase": 62, "slug": "general-public-explore"},
]

AUTO_CASE_POOLS = {
    "DEVELOPMENT_DATA": {
        "jobs": ["백엔드 개발자", "데이터 엔지니어", "프론트엔드 개발자", "QA 엔지니어", "클라우드 운영 엔지니어"],
        "industries": ["교육 플랫폼", "핀테크 서비스", "헬스케어 앱", "물류 SaaS", "공공 데이터"],
        "majors": ["컴퓨터공학", "소프트웨어학", "정보시스템학", "데이터사이언스"],
        "projects": ["사용자 행동 로그 분석", "권한 관리 API 개선", "대시보드 조회 성능 개선", "테스트 자동화 시나리오 정리", "배치 처리 오류 알림"],
        "roles": ["요구사항을 API 명세로 정리하고 구현", "데이터 흐름을 설계하고 SQL을 작성", "화면 상태 관리와 컴포넌트 분리", "테스트 케이스와 결함 재현 절차 작성", "배포 스크립트와 모니터링 항목 정리"],
        "results": ["처리 시간을 줄이는 개선안을 정리", "반복 수작업을 줄이는 자동화 흐름을 구성", "오류 원인을 재현 가능한 문서로 정리", "조회 조건별 결과 정확도를 검증", "운영 담당자가 확인할 수 있는 로그 기준을 마련"],
        "skills": [["Java", "Spring Boot", "MySQL", "REST API", "JUnit", "Git"], ["Python", "SQL", "Pandas", "Airflow", "Linux", "Git"], ["React", "TypeScript", "Vite", "Tailwind", "REST API", "Git"], ["테스트 설계", "Postman", "SQL", "결함 관리", "문서화", "Git"]],
        "values": ["안정적인 서비스 흐름", "데이터 기반 문제 해결", "사용자 경험 개선", "운영 자동화"],
        "gaps": ["성과 수치와 장애 전후 비교가 더 필요함", "설계 의사결정 근거가 더 구체화되어야 함", "테스트 범위와 검증 기준 설명이 부족함"],
        "metrics": ["응답 시간, 오류율, 테스트 통과율", "배치 처리 시간, 실패 건수", "재현 절차 수, 결함 해결률"],
        "scoreBases": [78, 80, 82, 76],
    },
    "SALES_MARKETING": {
        "jobs": ["마케팅 AE", "콘텐츠 마케터", "B2B 영업 지원", "CRM 마케터", "브랜드 커뮤니케이션 담당자"],
        "industries": ["교육 서비스", "구독 커머스", "여행 플랫폼", "HR 솔루션", "생활용품 브랜드"],
        "majors": ["경영학", "광고홍보학", "미디어커뮤니케이션", "통계학"],
        "projects": ["캠페인 성과 리포트", "고객 세그먼트 분류", "제휴 후보 리스트 정리", "뉴스레터 반응 분석", "브랜드 메시지 테스트"],
        "roles": ["채널별 성과를 비교하고 개선안을 작성", "고객 반응을 유형별로 분류", "제휴 조건과 예상 효과를 정리", "콘텐츠 문구와 발송 결과를 비교", "설문 응답을 기반으로 메시지 방향을 도출"],
        "results": ["클릭률과 문의 전환 흐름을 정리", "우선 공략 고객군을 제안", "제휴 후보 40곳을 기준별로 분류", "콘텐츠별 반응 차이를 리포트로 작성", "핵심 메시지 3가지를 선정"],
        "skills": [["Excel", "GA4", "광고 성과 분석", "PowerPoint", "카피라이팅", "시장 조사"], ["CRM", "고객 세그먼트", "SQL 기초", "리포트 작성", "A/B 테스트", "커뮤니케이션"], ["제휴 제안", "시장 조사", "Excel", "영업 자료", "고객 니즈 분석", "PowerPoint"]],
        "values": ["고객 반응을 근거로 한 실행", "매출 전환 가능성 개선", "브랜드 메시지 일관성", "영업 기회 발굴"],
        "gaps": ["실제 전환율과 매출 기여 근거가 부족함", "캠페인 전후 비교 수치가 더 필요함", "고객군 선정 기준이 더 명확해야 함"],
        "metrics": ["CTR, CVR, 문의 전환율", "오픈율, 클릭률, 구독 해지율", "제안 발송 수, 회신율, 미팅 전환율"],
        "scoreBases": [77, 79, 81, 83],
    },
    "DESIGN_CONTENT": {
        "jobs": ["UX/UI 디자이너", "콘텐츠 에디터", "브랜드 디자이너", "영상 콘텐츠 제작자", "서비스 기획 디자이너"],
        "industries": ["모빌리티 앱", "문화 콘텐츠", "F&B 브랜드", "교육 콘텐츠", "커머스 서비스"],
        "majors": ["시각디자인", "산업디자인", "콘텐츠기획", "미디어학"],
        "projects": ["가입 화면 사용성 개선", "브랜드 카드뉴스 제작", "프로모션 영상 편집", "상품 상세 페이지 개편", "사용자 인터뷰 정리"],
        "roles": ["와이어프레임과 프로토타입 제작", "콘텐츠 구조와 문구 작성", "영상 컷 편집과 자막 구성", "시각 요소와 정보 구조 정리", "인터뷰 내용을 문제 유형별로 분류"],
        "results": ["입력 단계를 줄인 개선안을 제안", "카드뉴스 12종을 제작", "30초 홍보 영상 4편을 제작", "상품 정보 가독성을 개선", "사용자 불편 지점 10가지를 도출"],
        "skills": [["Figma", "UX 리서치", "프로토타입", "사용성 테스트", "와이어프레임", "문서화"], ["Photoshop", "Illustrator", "브랜드 디자인", "카피라이팅", "SNS 콘텐츠", "편집 디자인"], ["Premiere Pro", "After Effects", "스토리보드", "자막 편집", "영상 기획", "사운드 편집"]],
        "values": ["사용자가 이해하기 쉬운 화면", "브랜드 경험의 일관성", "콘텐츠 전달력 향상", "시각 정보의 명확성"],
        "gaps": ["사용자 테스트와 개선 전후 지표가 부족함", "디자인 결정 근거가 더 필요함", "콘텐츠 성과 지표 연결이 약함"],
        "metrics": ["완료율, 이탈률, 사용성 점수", "조회수, 저장 수, 공유 수", "시청 유지율, 클릭률"],
        "scoreBases": [76, 78, 80, 82],
    },
    "BUSINESS_OFFICE": {
        "jobs": ["서비스 운영 매니저", "인사 운영 담당자", "경영기획 보조", "총무 담당자", "회계 보조"],
        "industries": ["IT 서비스", "제조업", "공공기관", "스타트업", "비영리 단체"],
        "majors": ["경영학", "행정학", "회계학", "경제학"],
        "projects": ["운영 문의 유형 분류", "월간 지표 리포트", "교육 일정 관리표", "비품 관리 프로세스 정리", "증빙 자료 검토"],
        "roles": ["반복 문의를 유형화하고 처리 가이드를 작성", "핵심 지표를 표로 정리하고 변동 원인을 기록", "참여자 일정과 안내 문구를 관리", "비품 요청과 재고 흐름을 정리", "증빙 누락 항목을 확인하고 분류"],
        "results": ["문의 200건을 8개 유형으로 분류", "월간 지표 변동 사유를 보고서로 작성", "교육 대상자 80명의 참석 현황을 관리", "비품 요청 처리 기준을 문서화", "증빙 300건의 누락 여부를 점검"],
        "skills": [["Excel", "Notion", "운영 가이드", "VOC 분석", "문서화", "커뮤니케이션"], ["PowerPoint", "지표 분석", "Excel", "리포트 작성", "일정 관리", "회의록"], ["회계 기초", "증빙 관리", "Excel", "문서 검토", "ERP 기초", "정확성"]],
        "values": ["반복 업무의 표준화", "정확한 운영 데이터 관리", "조직 내 협업 효율 향상", "업무 누락 방지"],
        "gaps": ["업무 개선 전후 수치가 더 필요함", "우선순위 판단 기준이 부족함", "이해관계자 커뮤니케이션 결과가 더 구체적이어야 함"],
        "metrics": ["처리 시간, 누락률, 재문의율", "참석률, 일정 준수율", "증빙 누락률, 처리 건수"],
        "scoreBases": [77, 79, 81, 82],
    },
    "HEALTHCARE_SERVICE": {
        "jobs": ["병원 원무 담당자", "간호사", "건강 상담원", "의료기기 영업지원", "보건교육 담당자"],
        "industries": ["종합병원", "검진센터", "지역 보건소", "의료기기 회사", "복지 시설"],
        "majors": ["보건행정학", "간호학", "임상병리학", "사회복지학"],
        "projects": ["접수 대기 유형 정리", "환자 안내 자료 제작", "검체 라벨 오류 점검", "건강 상담 스크립트 개선", "보건 교육 캠페인"],
        "roles": ["환자 문의와 대기 사유를 분류", "안내 문구와 체크리스트를 작성", "오류 발생 단계를 기록하고 점검", "반복 상담 내용을 유형화", "교육 대상별 메시지를 구성"],
        "results": ["문의 150건을 7개 유형으로 분류", "안내 자료 5종을 제작", "오류 사례 40건을 단계별로 정리", "상담 스크립트 6종을 개선", "교육 참여자 설문을 분석"],
        "skills": [["원무 행정", "Excel", "환자 응대", "보험 기초", "EMR 기초", "민원 처리"], ["간호 술기", "환자 관찰", "안전 관리", "체크리스트", "기록 관리", "의사소통"], ["상담 응대", "VOC 분석", "스크립트 작성", "감정 노동 관리", "Excel", "보건 지식"]],
        "values": ["환자 안전과 정확한 안내", "반복 오류 예방", "건강 정보 전달력 향상", "서비스 신뢰도 개선"],
        "gaps": ["개인정보 처리 기준 설명이 더 필요함", "오류 감소나 만족도 지표가 부족함", "실제 현장 적용 결과가 더 구체화되어야 함"],
        "metrics": ["대기 시간, 재문의율, 만족도", "오류 건수, 재검률", "상담 시간, 해결률"],
        "scoreBases": [76, 78, 80, 82],
    },
    "EDUCATION_PUBLIC": {
        "jobs": ["교육 운영 담당자", "평생교육사", "공공행정 담당자", "청소년 지도사", "도서관 사서 보조"],
        "industries": ["지자체 교육센터", "온라인 강의 플랫폼", "공공기관", "청소년센터", "공공도서관"],
        "majors": ["교육학", "행정학", "문헌정보학", "사회복지학"],
        "projects": ["강의 오류 신고 처리", "학습자 설문 분석", "프로그램 참여자 관리", "민원 안내 문구 개선", "추천 도서 큐레이션"],
        "roles": ["신고 유형을 분류하고 처리 기준을 정리", "설문 응답을 분석하고 개선점을 도출", "참여자 명단과 출석을 관리", "반복 질문에 대한 안내 문구를 작성", "주제별 도서 목록과 소개문을 작성"],
        "results": ["신고 90건을 6개 유형으로 분류", "설문 180건을 분석해 개선점 8개를 도출", "참여자 120명의 출석 현황을 관리", "민원 안내 문구 10종을 작성", "추천 도서 50권을 주제별로 분류"],
        "skills": [["교육 운영", "LMS", "Excel", "설문 분석", "학습자 응대", "운영 가이드"], ["공공행정", "민원 응대", "문서 작성", "개인정보 관리", "Excel", "안내문 작성"], ["자료 분류", "도서 큐레이션", "이용자 응대", "홍보문 작성", "Excel", "문헌정보"]],
        "values": ["학습자 경험 개선", "정확한 행정 안내", "지역사회 참여 확대", "정보 접근성 향상"],
        "gaps": ["교육 효과 측정 방식이 부족함", "참여자 변화 지표가 더 필요함", "법령 또는 운영 기준 근거가 더 명확해야 함"],
        "metrics": ["수료율, 만족도, 재신고율", "참여율, 출석률, 설문 점수", "대출률, 이용자 피드백"],
        "scoreBases": [76, 78, 80, 82],
    },
    "PRODUCTION_LOGISTICS": {
        "jobs": ["물류 운영 담당자", "품질관리 보조", "생산관리 담당자", "구매 물류 코디네이터", "안전관리 보조"],
        "industries": ["식품 제조", "의류 물류", "자동차 부품", "전자 조립", "건설 현장"],
        "majors": ["산업공학", "물류유통학", "기계공학", "안전공학"],
        "projects": ["재고 실사 차이 분석", "출고 지연 사유 분류", "작업 표준서 정리", "원자재 검수 기록", "위험요인 체크리스트"],
        "roles": ["실사 차이를 유형별로 정리", "지연 주문의 원인을 기록하고 분류", "작업 단계를 표준 문서로 작성", "검수 기준과 부적합 여부를 기록", "작업별 위험요인과 예방 조치를 정리"],
        "results": ["SKU 250개의 차이를 유형화", "지연 주문 100건을 5개 원인으로 분류", "작업 단계 30개를 표준서로 정리", "부적합 사례 35건을 기록", "위험요인 25개를 체크리스트로 구성"],
        "skills": [["WMS", "Excel", "재고 관리", "SKU 관리", "입출고", "바코드"], ["품질관리", "검수 기록", "GMP 기초", "부적합 관리", "문서화", "Excel"], ["생산관리", "작업 표준서", "안전 관리", "현장 소통", "공정 이해", "Excel"]],
        "values": ["현장 오류 감소", "정시 출고율 개선", "품질 리스크 예방", "안전한 작업 환경"],
        "gaps": ["개선 전후 수치가 부족함", "원인 분석과 재발 방지 조치가 더 구체적이어야 함", "현장 적용 결과가 더 필요함"],
        "metrics": ["재고 정확도, 지연 건수, 정시 출고율", "부적합률, 재검수 건수", "사고 건수, 조치 완료율"],
        "scoreBases": [76, 78, 80, 82],
    },
    "GENERAL": {
        "jobs": ["직무 탐색 중인 신입", "사무/운영 직무 탐색자", "콘텐츠 직무 탐색자", "데이터 활용 직무 탐색자", "공공/교육 직무 탐색자"],
        "industries": ["일반 사무", "고객 응대", "문화 콘텐츠", "공공 서비스", "교육 지원"],
        "majors": ["사회학", "인문학", "경영학", "심리학"],
        "projects": ["동아리 운영 기록 정리", "고객 응대 사례 정리", "홍보 게시물 제작", "설문 결과 정리", "봉사활동 운영 보조"],
        "roles": ["회의록과 예산 사용 내역을 정리", "불편 접수 내용을 유형화", "이미지와 문구를 제작", "응답 데이터를 표로 정리", "참여자 명단과 활동 기록을 관리"],
        "results": ["월별 활동 기록을 문서화", "응대 사례 50건을 분류", "홍보 게시물 10개를 제작", "응답 160건을 표와 그래프로 정리", "참여자 70명의 활동 기록을 관리"],
        "skills": [["문서 작성", "Excel", "회의록", "일정 관리", "협업", "발표"], ["고객 응대", "상황 대응", "문서화", "커뮤니케이션", "Excel", "서비스 마인드"], ["Canva", "SNS", "콘텐츠 작성", "이미지 편집", "홍보", "문서 작성"]],
        "values": ["기본 업무 역량 정리", "희망 직무 탐색", "협업 경험 정리", "직무 방향성 구체화"],
        "gaps": ["희망 직무가 아직 넓고 선택 근거가 부족함", "경험을 직무 역량으로 연결하는 설명이 약함", "성과 지표와 역할 구분이 부족함"],
        "metrics": ["처리 건수, 일정 준수율", "응답 수, 만족도", "게시물 반응 수, 참여율"],
        "scoreBases": [58, 60, 62, 64],
    },
}


def row_job_family(row: dict[str, Any]) -> str:
    user_input = json.loads(row["messages"][1]["content"])
    return str(user_input.get("jobFamily", "GENERAL"))


def count_job_families(rows: list[dict[str, Any]]) -> Counter:
    return Counter(row_job_family(row) for row in rows)


def pick(pool: dict[str, Any], key: str, serial: int):
    values = pool[key]
    return values[serial % len(values)]


def build_auto_case(family: str, family_serial: int, global_index: int) -> dict[str, Any]:
    pool = AUTO_CASE_POOLS[family]
    skills = list(pick(pool, "skills", family_serial))
    project_topic = pick(pool, "projects", family_serial)
    industry = pick(pool, "industries", family_serial + 1)
    job = pick(pool, "jobs", family_serial + 2)
    metric = pick(pool, "metrics", family_serial + 3)
    project_name = f"{project_topic} {family_serial:03d}"

    return {
        "family": family,
        "job": job,
        "industry": industry,
        "major": pick(pool, "majors", family_serial + 4),
        "project": project_name,
        "role": pick(pool, "roles", family_serial + 5),
        "result": f"{pick(pool, 'results', family_serial + 6)} ({family_serial:03d}차 사례)",
        "skills": skills,
        "value": pick(pool, "values", family_serial + 7),
        "gap": pick(pool, "gaps", family_serial + 8),
        "metric": metric,
        "scoreBase": pick(pool, "scoreBases", family_serial + 9),
        "slug": f"auto-{family.lower()}-{family_serial:03d}-{global_index:03d}",
        "careerRole": f"{job} 실습 보조",
        "company": f"{industry} 실습기관 {family_serial:03d}",
        "duties": f"{project_topic} 관련 자료 정리와 {metric} 기준 기록",
        "period": f"2025-{(family_serial % 9) + 1:02d}~2025-{(family_serial % 9) + 3:02d}",
        "goalDelta": (family_serial % 5) - 2,
        "experienceDelta": (family_serial % 4) - 1,
        "achievementDelta": family_serial % 6,
        "skillDelta": (family_serial % 3) - 1,
        "documentDelta": family_serial % 5,
        "improvementDelta": (family_serial % 4) - 1,
    }


def extend_rows_to_targets(module, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    counts = count_job_families(rows)
    next_index = len(rows) + 1

    while sum(counts[family] for family in TARGET_COUNTS) < sum(TARGET_COUNTS.values()):
        for family, target_count in TARGET_COUNTS.items():
            if counts[family] >= target_count:
                continue

            family_serial = counts[family] + 1
            case = build_auto_case(family, family_serial, next_index)
            rows.append(build_sample(module, case, next_index))
            counts[family] += 1
            next_index += 1

    return rows


def main() -> None:
    module = load_base_generator()
    rows = list(module.SAMPLES)
    start_index = len(rows) + 1
    rows.extend(build_sample(module, case, start_index + offset) for offset, case in enumerate(CASES))
    rows = extend_rows_to_targets(module, rows)
    ensure_unique(rows)
    with OUTPUT_PATH.open("w", encoding="utf-8", newline="\n") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"wrote {len(rows)} unique samples to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()

"""
면접 합성 학습용 브리핑 조립기 (BRIEFING_CONTRACT.md 구현 · 코드, LLM 아님).

company_analysis / job_analysis (실DB 또는 합성 가짜) -> 면접 브리핑 텍스트.
현재 백엔드 런타임 프롬프트 조립과는 별도 계약이다. 자체 QGEN을 기본 런타임으로 승격하기 전
Java parity adapter와 고정 fixture 비교 테스트가 필요하다.

압축 규칙: facts 5 / inferences 3 / preferred_skills 8 / 트렁케이트 / 분석 메타 서술 컷 / 옵셔널 폴백.
"""
import re

MODE_DESC = {
    "BASIC": "기본 — 자기소개·지원동기·장단점, 국비 주니어 수준",
    "JOB": "직무 — 필수 스킬별 기술 질문 위주, 국비 주니어 수준",
    "PERSONALITY": "인성 — 협업·갈등·책임감 + (가능하면) 컬처핏",
    "PRESSURE": "압박 — 본질문 후 반박 꼬리질문으로 약점 추궁",
    "RESUME": "자소서 — 자기소개서 문장 기반 질문",
    "COMPANY": "기업 맞춤 — 회사 이해도·지원동기",
}

# 모드별 포함 섹션 (BRIEFING_CONTRACT 6장 레시피)
_USES_COMPANY = {"PERSONALITY", "COMPANY"}
_USES_JOB = {"JOB", "PRESSURE"}
_USES_POSTING = {"JOB", "PRESSURE", "COMPANY"}

# 분석 메타 제거: (1) 노이즈 문장 통째 컷, (2) 분석 머리말 구절만 컷(뒤 사실은 살림)
_META_SENT = re.compile(r"[^.。\n]*(외부\s*검색|검색을\s*하지\s*않았|확인하지\s*않았)[^.。\n]*[.。]")
_META_PHRASE = re.compile(r"(입력\s*공고\s*기준|공고\s*기준으로는?|공고에\s*따르면)\s*")


def _clean(text, maxlen=200):
    if not text:
        return ""
    text = _META_SENT.sub("", text)
    text = _META_PHRASE.sub("", text)
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) > maxlen:
        text = text[:maxlen].rstrip() + "…"
    return text


def _top(arr, n):
    return (arr or [])[:n]


def _line(label, raw, maxlen=200):
    """_clean 후 내용이 남을 때만 줄 생성(빈 줄 방지)."""
    v = _clean(raw, maxlen)
    return f"- {label}: {v}" if v else None


def build_briefing(seed, company=None, job=None, self_intro=None, posting=None, count=6):
    """시드 + (가짜/실)분석 -> 브리핑 텍스트. 빈 섹션은 통째 생략(폴백)."""
    mode = seed["mode"]
    out = ["# 면접 브리핑",
           f"회사명: {seed['company_name']}",
           f"직무명: {seed['job_title']}",
           f"면접 모드: {MODE_DESC.get(mode, mode)}",
           f"질문 수: {count}"]

    # 회사 정보
    if company and mode in _USES_COMPANY:
        sec = ["", "## 회사 정보"]
        for line in [_line("업종", company.get("industry"), 80),
                     _line("개요", company.get("company_summary")),
                     _line("최근 이슈", company.get("recent_issues")) if mode == "COMPANY" else None,
                     _line("면접 포인트", company.get("interview_points"))]:
            if line:
                sec.append(line)
        comp = company.get("competitors") or []
        if comp:
            sec.append(f"- 경쟁사: {', '.join(comp[:5])}")
        for fact in _top(company.get("verified_facts"), 5):
            sec.append(f"- [확인된 사실] {fact.get('fact', '')} (출처: {fact.get('source', '')})")
        for inf in _top(company.get("ai_inferences"), 3):
            sec.append(f"- [AI 추론] {_clean(inf.get('inference', ''), 120)}")
        if len(sec) > 2:
            out += sec

    # 직무 정보
    if job and mode in _USES_JOB:
        sec = ["", "## 직무 정보"]
        req = job.get("required_skills") or []
        if req:
            sec.append(f"- 필수 스킬: {', '.join(req)}")
        pref = job.get("preferred_skills") or []
        if pref:
            sec.append(f"- 우대 스킬: {', '.join(pref[:8])}")
        if job.get("duties"):
            sec.append(f"- 주요 업무: {_clean(job['duties'], 300)}")
        if job.get("difficulty"):
            sec.append(f"- 난이도: {job['difficulty']}")
        for a in _top(job.get("ambiguous_conditions"), 2):
            sec.append(f"- [모호 조건] {a.get('condition', '')} → {a.get('assumption', '')}")
        if len(sec) > 2:
            out += sec

    # 자소서 (RESUME 게이트)
    if mode == "RESUME":
        if self_intro:
            out += ["", "## 자기소개서", f"- {_clean(self_intro, 400)}"]
        else:
            out += ["", "## 자기소개서", "- (자소서 미입력 — 일반 인성 질문으로 진행)"]

    # 공고 원문 (트렁케이트)
    if posting and mode in _USES_POSTING:
        out += ["", "## 채용공고 (요지)", _clean(posting, 1500)]

    return "\n".join(out)


# ── 자체 검증: GC녹십자 실데이터(축약)로 압축 규칙 확인 ──
if __name__ == "__main__":
    demo_company = {
        "industry": "생명공학·제약 (백신·혈액분획·희귀질환치료제)",
        "company_summary": "GC녹십자는 백신, 희귀질환치료제, 항암제, 혈액분획 및 Plasma 생산시설을 갖춘 "
                           "제약·바이오 기업으로 국내영업, 해외영업, 품질, 사업개발 직무를 채용 중이다.",
        "recent_issues": "외부 검색을 하지 않았으므로 최근 이슈는 확인하지 않았다. 입력 공고 기준 "
                         "2026년 하반기 채용전제형 인턴 및 2Q 경력 채용을 진행한다.",
        "interview_points": "제약·바이오 산업 내 연구개발·생산·품질·영업·글로벌 사업의 연결 구조 이해가 중요하다.",
        "competitors": [],
        "verified_facts": [
            {"fact": "회사명은 GC녹십자이다", "source": "회사명"},
            {"fact": "세계 최초 유행성출혈열 백신을 개발했다", "source": "채용공고"},
            {"fact": "세계 5위 Plasma 생산시설을 보유한다", "source": "채용공고"},
            {"fact": "종합병원 191개·의원 25,000여개 영업망을 갖췄다", "source": "채용공고"},
            {"fact": "전 세계 6개국 10개 해외법인, 약 5,300명 직원", "source": "채용공고"},
            {"fact": "세계 2번째 헌터증후군 치료제를 개발했다", "source": "채용공고"},
            {"fact": "접수기간은 2026년 6월 16일부터 7월 1일까지이다", "source": "채용공고"},
        ],
        "ai_inferences": [
            {"basis": "백신·혈액분획·희귀질환 사업 병렬", "inference": "연구개발·생산·품질·글로벌영업이 연결된 사업구조로 해석된다"},
            {"basis": "해외영업에 계약협상·커머셜 전략 명시", "inference": "글로벌 시장 확장과 제품 상업화 역량을 중시하는 직무로 보인다"},
            {"basis": "품질 업무에 GMP·밸리데이션 제시", "inference": "규제 환경의 데이터·문서 기반 품질관리 역량을 중요하게 본다"},
            {"basis": "경쟁사명이 자료에 없음", "inference": "특정 기업을 경쟁사로 단정할 수 없다"},
        ],
    }
    demo_job = {
        "required_skills": ["해외영업", "시장조사", "계약협상", "커머셜 전략"],
        "preferred_skills": ["영어", "제약 도메인", "오더관리", "매출계획", "마케팅", "GMP", "QMS", "Veeva", "LIMS", "밸리데이션"],
        "duties": "희귀질환치료제·항암제 해외영업, 시장 조사/분석, 거래처 발굴, 계약 협상, 오더 관리, 매출 계획, 커머셜 전략 수립 및 마케팅 활동",
        "difficulty": "HARD",
        "ambiguous_conditions": [
            {"condition": "필수 경력 연수 미기재", "assumption": "관련 경험 중심으로 해석"},
        ],
    }

    print("=" * 60, "\n[CASE 1] COMPANY 모드 — verified_facts 7개→5, inferences 4개→3, 메타서술 컷\n", "=" * 60)
    s1 = {"company_name": "GC녹십자", "job_title": "해외영업/마케팅", "mode": "COMPANY"}
    print(build_briefing(s1, company=demo_company, job=demo_job,
                         posting="(주)GC녹십자 채용공고 — 해외영업/마케팅 모집. 희귀질환치료제·항암제 해외 영업..."))

    print("\n", "=" * 60, "\n[CASE 2] JOB 모드 — 직무 정보만(회사정보 섹션 제외), 우대스킬 10개→8\n", "=" * 60)
    s2 = {"company_name": "GC녹십자", "job_title": "해외영업/마케팅", "mode": "JOB"}
    print(build_briefing(s2, company=demo_company, job=demo_job,
                         posting="(주)GC녹십자 채용공고 — 해외영업/마케팅 모집..."))

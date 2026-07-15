"""NCS 능력단위 기반 결정론 유틸 — 직무→요구 능력단위 매핑 + 학습 로드맵.

**설계 원칙(R2 시리즈 결론 반영):** NCS 를 3B 생성 프롬프트에 그냥 주입하면 conflation(요구를 보유로 착각)을
오히려 키운다. 그래서 NCS 의 안전한 값은 **결정론 계층**에 있다:
  1. required_units_for: 공고 텍스트 ↔ NCS 능력단위(이름·기술) lexical 매핑 → '이 직무가 요구하는 표준 역량'.
  2. roadmap_from_units: 요구 능력단위를 학습 로드맵으로 구조화. **전부 요구/학습 대상 라벨이며 '보유'가 아니다.**

**의도적으로 하지 않는 것(정직 표기):** 사용자 스킬 ↔ NCS 기술 자동 매칭으로 '보유 역량' 판정. NCS 기술은
한국어 역량 서술('~ 분석 능력')이고 사용자 스킬은 흔히 영문 기술명(Spark)이라 언어·입도 격차로 신뢰할 수
없다. 보유 판정은 기존 규칙엔진(profileLower.contains)이 소유하고, NCS↔기술명 온톨로지는 후속 과제.
"""
import re

TOKEN_RE = re.compile(r"[A-Za-z0-9가-힣]+")
# 매칭 신호를 흐리는 흔한 접미/불용 토큰(능력단위·기술 서술에 반복)
STOP = {"능력", "기술", "관리", "수립", "분석", "개발", "구축", "설계", "이해", "및", "등", "직무", "역량"}


def _tokens(s):
    return [t.lower() for t in TOKEN_RE.findall(str(s or ""))]


def _content_tokens(s):
    return {t for t in _tokens(s) if t not in STOP and len(t) >= 2}


def unit_relevance(job_text, unit):
    """공고 텍스트와 능력단위(이름+기술)의 content-token 겹침 비율(0~1). 불용어 제외."""
    q = _content_tokens(job_text)
    if not q:
        return 0.0
    doc = _content_tokens(unit.get("unitName", ""))
    for sk in unit.get("skills", []):
        doc |= _content_tokens(sk)
    if not doc:
        return 0.0
    return round(len(q & doc) / len(q), 4)


def required_units_for(job_text, units, *, top_k=5, min_score=0.05):
    """공고가 요구하는 NCS 능력단위 top_k. 결정론(점수·이름 tie-break)."""
    scored = []
    for u in units:
        s = unit_relevance(job_text, u)
        if s >= min_score:
            scored.append({**u, "relevance": s})
    scored.sort(key=lambda r: (-r["relevance"], r.get("subName", ""), r.get("unitName", "")))
    return scored[:top_k]


def roadmap_from_units(required_units):
    """요구 능력단위 → 학습 로드맵 항목. 전부 '요구/학습 대상' 라벨 — 보유 주장 아님."""
    items = []
    for u in required_units:
        items.append({
            "ncsSubName": u.get("subName"),
            "unitName": u.get("unitName"),
            "level": u.get("level"),
            "requiredSkills": u.get("skills", []),
            "status": "REQUIRED_STANDARD",  # 직무 표준 요구 — 지원자 보유 아님
            "label": "직무 표준 요구 역량(NCS)",
        })
    return items


def as_job_requirement_texts(required_units):
    """evidence bucket(jobRequirements)에 넣을 텍스트 목록. '요구' 프레이밍 고정."""
    out = []
    for u in required_units:
        skills = ", ".join(u.get("skills", [])) or "(세부 기술 미수록)"
        out.append(f"[NCS 표준 요구] '{u.get('subName')}' 직무의 능력단위 '{u.get('unitName')}'"
                   f"(수준 {u.get('level')})가 요구하는 기술: {skills}.")
    return out

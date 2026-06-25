"""semantic skill judge — stage 1: 결정론적 정규화(LLM 불필요).

배경: `eval_fit_model.py` 의 HALLUCINATED_SKILL 은 learningTaskReasons[].skill 을
allowedSkills 와 '공백제거+소문자 정확매칭'으로만 비교한다(#124). 그래서 모델이
in-scope 스킬을 **콤마 나열·접미구·괄호**로 풀어쓰면 over-count 된다
(reports/38 의 gray-zone: `협업, 코드리뷰, 커뮤니케이션`·`Figma 사용법`·`MSA (Microservices Architecture)`).

이 모듈은 기존 하니스를 **대체하지 않고**(병렬), flagged skill 을 결정론 규칙으로
재분류한다. LLM 판정이 필요 없는 명백한 오탐만 걸러내고, 나머지는 'unresolved' 로
남겨 stage 2(AI judge) 로 넘긴다. **보수적: 명백한 오탐만 내리고, 절대 valid_error 로
자동 단정하지 않는다**(범위밖 단정은 의미 판정이라 judge/사람 몫).

상태:
  exact           — 정규화하면 allowedSkills 와 정확일치(원시 하니스의 NFKC/공백 누락 오탐)
  false_positive  — 콤마/및 나열의 모든 조각이 매칭 or 접미구/괄호 제거 후 정확매칭
  soft_match      — allowedSkill 이 flagged 안에 부분문자열로 존재(여분 토큰이 있어 judge 확인 필요)
  unresolved      — 결정론으로 못 풂(부분 나열·매칭 0건) → 진짜 judge 후보

CLI 자가점검: python scripts/skill_normalizer.py
"""
import re
import unicodedata

# '~에 대한 지식/사용법' 류 wrapper 명사 — flagged 끝에서 반복 제거해 핵심 스킬을 노출.
# 주의: '관리·운영·분석·설계·수립' 등 **스킬 헤드**는 제외(헤드까지 깎으면 무관 스킬과 오매칭).
#       그런 경우는 exact/공백 정규화 또는 substring(soft_match)으로만 인정한다.
SUFFIX_NOUNS = [
    "방법론", "프레임워크", "솔루션", "시스템", "플랫폼", "도구", "툴",
    "사용법", "활용법", "활용", "실습", "경험", "이해", "지식", "능력", "기술", "스킬",
]
# 나열 구분자: 콤마류 + 한국어 접속 '및' + 슬래시/중점/'&'. (영문 'and' 는 단어 내 오분할 위험으로 공백 경계만)
SPLIT_RE = re.compile(r"\s*(?:,|、|·|/|&|및|\band\b)\s*")
PAREN_RE = re.compile(r"[\(\（][^\)\）]*[\)\）]")


def _nfkc(s):
    return unicodedata.normalize("NFKC", str(s or ""))


def _key(s):
    """공백 제거 + 소문자 + NFKC — 하니스 비교를 NFKC 까지 확장한 정규화 키."""
    return re.sub(r"\s+", "", _nfkc(s)).lower()


def _strip_suffix_nouns(s):
    """flagged 끝의 wrapper 명사를 반복 제거. 'X 프레임워크 이해'→'X'."""
    cur = _nfkc(s).strip()
    changed = True
    while changed:
        changed = False
        for suf in SUFFIX_NOUNS:
            if cur.endswith(suf) and len(cur) > len(suf):
                cur = cur[: -len(suf)].strip()
                changed = True
                break
    return cur


def _variants(part):
    """한 조각의 정규화 변형들(정확매칭 시도용): 원형·괄호제거·접미제거·괄호안 내용."""
    out = []
    base = _nfkc(part).strip()
    if not base:
        return out
    out.append(base)
    noparen = PAREN_RE.sub("", base).strip()
    if noparen and noparen != base:
        out.append(noparen)
    inner = " ".join(m.strip("()（） ") for m in PAREN_RE.findall(base)).strip()
    if inner:
        out.append(inner)
    for v in list(out):
        stripped = _strip_suffix_nouns(v)
        if stripped and stripped not in out:
            out.append(stripped)
    return out


def _match_part(part, allowed_keys):
    """조각이 allowed 와 정확(공백/NFKC 무시)매칭되면 그 allowed 키 반환, 아니면 None."""
    for v in _variants(part):
        if _key(v) in allowed_keys:
            return _key(v)
    return None


def _substring_hit(part, allowed_items):
    """allowedSkill 이 조각 안에 부분문자열로 들어있으면 그 allowed 원문 반환(soft)."""
    pk = _key(part)
    if not pk:
        return None
    for a in allowed_items:
        ak = _key(a)
        if ak and ak in pk and ak != pk:
            return a
    return None


def classify_flagged_skill(flagged, allowed_skills):
    """flagged skill 1건을 allowedSkills 대비 결정론 분류.

    반환 dict: flagged, status, method, parts, matchedAllowed, unmatchedParts, softHits
    """
    allowed_items = [a for a in (allowed_skills or []) if str(a).strip()]
    allowed_keys = {_key(a) for a in allowed_items}
    flagged = _nfkc(flagged)

    # 0) 전체가 그대로(공백/NFKC만 차이) allowed 와 일치 — 원시 하니스의 정규화 누락 오탐
    if _key(flagged) in allowed_keys:
        return {"flagged": flagged, "status": "exact", "method": "exact_normalized",
                "parts": [flagged], "matchedAllowed": [flagged], "unmatchedParts": [], "softHits": []}

    # 1) 통째로 괄호/접미 제거 후 정확매칭(단일 스킬 + wrapper: 'Figma 사용법'→Figma)
    whole = _match_part(flagged, allowed_keys)
    if whole is not None:
        return {"flagged": flagged, "status": "false_positive", "method": "suffix_or_paren",
                "parts": [flagged], "matchedAllowed": [whole], "unmatchedParts": [], "softHits": []}

    # 2) 나열 분할 후 조각별 매칭
    raw_parts = [p.strip() for p in SPLIT_RE.split(flagged) if p.strip()]
    # 분할이 의미 없으면(=1조각) 통짜로 두고 substring 검사로
    parts = raw_parts if len(raw_parts) > 1 else [flagged]
    matched, unmatched, soft = [], [], []
    for p in parts:
        # 접미 wrapper 만 남은 조각('사용법' 단독)은 filler 로 무시
        if _strip_suffix_nouns(p) == "" and _key(p) not in allowed_keys:
            continue
        m = _match_part(p, allowed_keys)
        if m is not None:
            matched.append(p)
            continue
        sh = _substring_hit(p, allowed_items)
        if sh is not None:
            soft.append({"part": p, "allowed": sh})
        else:
            unmatched.append(p)

    if matched and not unmatched and not soft:
        return {"flagged": flagged, "status": "false_positive",
                "method": "list_all_match" if len(parts) > 1 else "normalized_match",
                "parts": parts, "matchedAllowed": matched, "unmatchedParts": [], "softHits": []}

    if soft and not unmatched:
        # 매칭/soft 만 — allowed 가 안에 들어있으나 여분 토큰 존재 → judge 가 acceptable/오탐 확정
        return {"flagged": flagged, "status": "soft_match", "method": "substring",
                "parts": parts, "matchedAllowed": matched, "unmatchedParts": [], "softHits": soft}

    # 매칭 0건이거나, 일부만 매칭되고 나머지가 미매칭 → 진짜 judge 후보
    return {"flagged": flagged, "status": "unresolved",
            "method": "partial_list" if matched else "no_match",
            "parts": parts, "matchedAllowed": matched, "unmatchedParts": unmatched, "softHits": soft}


# stage 2(judge)로 넘겨야 하는 상태
JUDGE_STATUSES = {"soft_match", "unresolved"}
# 결정론으로 오탐 처리된 상태(hallucination 카운트에서 제외)
RESOLVED_FP_STATUSES = {"exact", "false_positive"}


def _selfcheck():
    """reports/38 의 실제 gray-zone 사례로 자가점검(모델/네트워크 불필요)."""
    cases = [
        # (flagged, allowed, expected_status)
        ("협업, 코드리뷰, 커뮤니케이션",
         ["협업", "코드리뷰", "커뮤니케이션", "Spring Boot"], "false_positive"),
        ("재무 설계 및 세무 지식", ["재무 설계", "세무 지식", "법인영업"], "false_positive"),
        ("영업 관리 및 실적 분석", ["영업관리", "실적 분석", "채널 관리"], "false_positive"),
        ("Figma 사용법", ["UI 디자인", "Figma", "UX 리서치"], "false_positive"),
        ("UX 리서치 방법론", ["Figma", "UX 리서치"], "false_positive"),
        ("MSA (Microservices Architecture)", ["Java", "MSA", "Kafka"], "false_positive"),
        ("임직원 인터뷰 기술", ["조직문화 기획", "임직원 인터뷰"], "false_positive"),
        ("신규점 오픈 경험", ["점포 운영관리", "신규점 오픈", "SV 경험"], "false_positive"),
        ("급여 정산, 4대보험 실무, 근태 관리, 협업, 문서화, 커뮤니케이션",
         ["급여 정산", "4대보험 실무", "근태 관리", "협업", "문서화", "커뮤니케이션"], "false_positive"),
        ("커뮤니케이션 프레임워크 이해", ["교육 운영", "커뮤니케이션"], "false_positive"),
        # soft_match — allowed 가 부분문자열, 여분 토큰 존재 → judge
        ("데이터 기반 수요예측", ["수요예측", "SCM 시스템 운영"], "soft_match"),
        ("수요예측 기반 발주 최적화", ["수요예측", "SAP WMS", "위험물 운송"], "soft_match"),
        ("위험물 운송 관리", ["재고 운영", "위험물 운송", "무역 영어"], "soft_match"),
        # unresolved — 매칭 0건(범위밖 후보) 또는 부분 나열 → judge
        ("헬프데스크 솔루션 이해", ["고객 상담", "VOC 관리", "클레임 응대"], "unresolved"),
        ("LMS 솔루션 선택 및 사용법", ["교육과정 기획", "교육 운영", "커뮤니케이션"], "unresolved"),
        ("사내 안전관리 시스템 운영", ["산업안전 관리", "위험성 평가", "안전보건 법규"], "unresolved"),
        ("물류 관리 및 KPI 분석", ["물류 KPI 분석", "WMS 운영", "입출고 관리"], "unresolved"),
    ]
    ok = 0
    for flagged, allowed, exp in cases:
        r = classify_flagged_skill(flagged, allowed)
        mark = "OK " if r["status"] == exp else "XX "
        if r["status"] == exp:
            ok += 1
        else:
            print(f"  {mark}{flagged!r}: got {r['status']} (method={r['method']}), want {exp}")
    print(f"[skill_normalizer selfcheck] {ok}/{len(cases)} 통과")
    return ok == len(cases)


if __name__ == "__main__":
    import sys
    sys.exit(0 if _selfcheck() else 1)

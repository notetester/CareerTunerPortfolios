"""골든셋 케이스 유틸 — 규칙엔진 일관 필드 계산 + 검증.

matched/missing 은 규칙엔진(MockFitAnalysisAiService)과 동일하게 profileSkills 와 '정확 일치
(대소문자 무시)'로 계산한다. profileCertificates 는 매칭에서 제외(= #116 cert 오탐의 근원이며,
규칙엔진도 cert 를 스킬 매칭에 쓰지 않음).
fitScore/applyDecision 은 경계 테스트를 위해 작성자가 밴드 내에서 정하고, 여기선 밴드 정합만 검증한다
(기존 골든셋의 fitScore 도 공식 계산이 아니라 손으로 작성된 값이라, 사실 필드만 강제한다).

사용:
  python scripts/golden_case_tools.py eval/golden_fit_cases.jsonl   # 전체 검증(0 오류여야 함)
"""
import json
import sys

DECISIONS = {"APPLY", "HOLD", "COMPLEMENT_BEFORE_APPLY"}
STD_REQUIRED_KEYS = ["fitSummary", "strengths", "risks", "strategyActions", "learningTaskReasons"]
STD_FORBIDDEN_KEYS = ["fitScore", "score", "applyDecision", "decision"]
# 하니스 substring 오탐(#112) 유발: forbiddenClaims 에 bare 형태 금지 — 전체 명령형("즉시 지원하세요")만 허용
BARE_FALSE_POSITIVE = {"즉시 지원", "바로 지원", "지금 지원", "지원하세요"}


def _norm(s):
    return (s or "").strip().lower()


def compute_matched_missing(required, preferred, profile_skills):
    """규칙엔진과 동일: profileSkills 정확 일치(대소문자 무시). cert 는 제외."""
    prof = {_norm(s) for s in (profile_skills or [])}
    matched_req = [s for s in (required or []) if _norm(s) in prof]
    missing_req = [s for s in (required or []) if _norm(s) not in prof]
    matched_pref = [s for s in (preferred or []) if _norm(s) in prof]
    missing_pref = [s for s in (preferred or []) if _norm(s) not in prof]
    return matched_req + matched_pref, missing_req, missing_pref


def decision_band_ok(decision, fit_score, missing_required_count):
    """규칙엔진 밴드(경계 케이스 허용폭 포함). APPLY 는 필수 미충족 0 전제."""
    if decision == "APPLY":
        return fit_score >= 70 and missing_required_count == 0
    if decision == "COMPLEMENT_BEFORE_APPLY":
        return 45 <= fit_score <= 72
    if decision == "HOLD":
        return fit_score <= 60
    return False


def validate_case(case):
    """케이스 1건의 구조·사실 필드·밴드 정합 검증. 오류 문자열 리스트 반환(빈 리스트=정상)."""
    errs = []
    cid = case.get("id", "?")
    inp = case.get("input") or {}
    exp = case.get("expected") or {}
    dec = case.get("expectedDecision")

    if dec not in DECISIONS:
        errs.append(f"{cid}: expectedDecision '{dec}' 비정상")
    if inp.get("applyDecision") != dec:
        errs.append(f"{cid}: input.applyDecision({inp.get('applyDecision')}) != expectedDecision({dec})")

    req = inp.get("requiredSkills") or []
    pref = inp.get("preferredSkills") or []
    prof = inp.get("profileSkills") or []
    matched, miss_req, miss_pref = compute_matched_missing(req, pref, prof)

    def _set(xs):
        return {_norm(x) for x in (xs or [])}

    if _set(inp.get("matchedSkills")) != _set(matched):
        errs.append(f"{cid}: matchedSkills 불일치 — 규칙엔진 기대 {matched}")
    if _set(inp.get("missingRequiredSkills")) != _set(miss_req):
        errs.append(f"{cid}: missingRequiredSkills 불일치 — 기대 {miss_req}")
    if _set(inp.get("missingPreferredSkills")) != _set(miss_pref):
        errs.append(f"{cid}: missingPreferredSkills 불일치 — 기대 {miss_pref}")

    fs = inp.get("fitScore")
    if not isinstance(fs, int) or not (0 <= fs <= 100):
        errs.append(f"{cid}: fitScore 범위 오류({fs})")
    elif not decision_band_ok(dec, fs, len(miss_req)):
        errs.append(f"{cid}: fitScore {fs} 가 {dec} 밴드와 불일치(필수 미충족 {len(miss_req)})")

    if _set(exp.get("allowedSkills")) != _set(req + pref):
        errs.append(f"{cid}: allowedSkills != requiredSkills+preferredSkills")
    if (exp.get("requiredKeys") or STD_REQUIRED_KEYS) != STD_REQUIRED_KEYS:
        errs.append(f"{cid}: requiredKeys 비표준")
    if (exp.get("forbiddenKeys") or STD_FORBIDDEN_KEYS) != STD_FORBIDDEN_KEYS:
        errs.append(f"{cid}: forbiddenKeys 비표준")
    for fc in exp.get("forbiddenClaims") or []:
        if fc.strip() in BARE_FALSE_POSITIVE:
            errs.append(f"{cid}: forbiddenClaims '{fc}' bare 형태 — 전체 명령형 사용(오탐 방지)")
    # allowedSkills 외 스킬을 mustMention 하면 모순
    for m in exp.get("mustMention") or []:
        if _norm(m) not in _set(req + pref):
            errs.append(f"{cid}: mustMention '{m}' 가 allowedSkills 에 없음")
    # allowedSkills(=required+preferred)에 있는 스킬을 mustNotMention 하면 모순(필수인데 언급 금지)
    for m in exp.get("mustNotMention") or []:
        if _norm(m) in _set(req + pref):
            errs.append(f"{cid}: mustNotMention '{m}' 가 allowedSkills 에 있음(모순)")
    return errs


def assemble_input(required, preferred, profile_skills, profile_certificates,
                   fit_score, decision, company, job, desired_job, experience, duties):
    """사실 필드(matched/missing)는 계산, 점수/판단은 인자값으로 input 블록 구성."""
    matched, miss_req, miss_pref = compute_matched_missing(required, preferred, profile_skills)
    return {
        "companyName": company, "jobTitle": job, "desiredJob": desired_job or job,
        "experienceLevel": experience, "requiredSkills": required, "preferredSkills": preferred,
        "duties": duties, "profileSkills": profile_skills, "profileCertificates": profile_certificates or [],
        "matchedSkills": matched, "missingRequiredSkills": miss_req, "missingPreferredSkills": miss_pref,
        "fitScore": fit_score, "applyDecision": decision,
    }


def _main():
    path = sys.argv[1] if len(sys.argv) > 1 else "eval/golden_fit_cases.jsonl"
    cases, ids = [], set()
    for ln, line in enumerate(open(path, encoding="utf-8"), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        cases.append((ln, json.loads(line)))
    all_errs = []
    for ln, c in cases:
        cid = c.get("id")
        if cid in ids:
            all_errs.append(f"중복 id: {cid}")
        ids.add(cid)
        all_errs += validate_case(c)
    print(f"검증: {len(cases)}건, 오류 {len(all_errs)}건")
    for e in all_errs:
        print("  -", e)
    sys.exit(1 if all_errs else 0)


if __name__ == "__main__":
    _main()

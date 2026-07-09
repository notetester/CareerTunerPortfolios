"""
C_FIT_EXPLAIN raw 데이터 자동 검증 (범용 직군 대응).

raw([{seed, fit_explain}])를 받아 뉴로-심볼릭 계약을 검증한다.
규칙엔진 정합성은 seed_profiles.compute_fit 를 **오라클**로 재호출해 데이터 내부 일관성을 확인한다.

검증(fatal=problems):
  - 구조 / 필수 키 / 금지 키(fitScore/score/applyDecision/decision)
  - 규칙엔진 정합(matched/missing/decision 재계산 일치, fitScore 0~100)
  - 환각(스킬): learningTaskReasons.skill 이 시드 역량 집합 안인지 (비IT 직군도 동일 적용)
  - 모순: missingRequiredSkills 를 strengths 에서 보유한 것처럼 언급(학습 프레이밍·자격증 인용 제외)
경고(warnings, 비fatal):
  - it_leak: 비IT 직군 응답에 IT 전용 표현이 시드 역량 밖에서 섞임
분포:
  - domainGroup / jobFamily / 지원판단 / IT·비IT 비율 / fitScore 통계

사용:
    python validate_dataset.py --raw ../data/raw....json --summary ../data/validate.summary....json
"""
import argparse
import json
import re

from seed_profiles import compute_fit, norm, DOMAIN_FAMILIES, domain_group_of, is_it_group

REQUIRED_KEYS = ["fitSummary", "strengths", "risks", "strategyActions", "learningTaskReasons"]
FORBIDDEN_KEYS = {"fitscore", "score", "applydecision", "decision", "fit_score", "apply_decision"}
LEARN_MARKERS = ("학습", "전환", "습득", "익히", "토대", "기반", "출발점", "전제", "수월", "빠르", "적응")
# 비IT 직군에 섞이면 안 되는 IT 전용 표현(보수적 목록)
IT_LEAK_TOKENS = ["GitHub", "Docker", "Dockerfile", "컨테이너", "Kubernetes", "PyTorch", "TensorFlow",
                  "Spring Boot", "백엔드", "프론트엔드", "리팩터", "코드 리뷰", "REST API", "배포 파이프라인"]


def mentions(text, skill):
    """text 안에 skill 이 토큰 경계로 등장하는지(Java 가 JavaScript 에 오탐되지 않게)."""
    s = skill.strip().lower()
    if not s:
        return False
    t = text.lower()
    if re.fullmatch(r"[a-z0-9.+/# ]+", s):
        return re.search(r"(?<![a-z0-9])" + re.escape(s) + r"(?![a-z0-9])", t) is not None
    return s in t


def _seed_skill_norm(seed):
    bag = ((seed.get("requiredSkills") or []) + (seed.get("preferredSkills") or [])
           + (seed.get("profileSkills") or []) + (seed.get("matchedSkills") or [])
           + (seed.get("missingRequiredSkills") or []) + (seed.get("missingPreferredSkills") or []))
    return {norm(x) for x in bag}


def check_row(row):
    """(problems, warnings, info). problems 비면 통과(필터 유지)."""
    problems, warnings = [], []
    seed = row.get("seed") or {}
    fit = row.get("fit_explain")
    group = seed.get("domainGroup") or domain_group_of(seed.get("jobFamily"))
    info = {"decision": seed.get("applyDecision"), "family": seed.get("jobFamily"),
            "domainGroup": group, "fitScore": seed.get("fitScore")}

    if not isinstance(fit, dict):
        problems.append("fit_explain_not_dict")
        return problems, warnings, info

    for k in fit:
        if k.lower() in FORBIDDEN_KEYS:
            problems.append(f"forbidden_key:{k}")
    for k in REQUIRED_KEYS:
        if k not in fit:
            problems.append(f"missing_key:{k}")
    if isinstance(fit.get("fitSummary"), str) and not fit["fitSummary"].strip():
        problems.append("empty:fitSummary")
    for lk in ["strengths", "risks", "strategyActions", "learningTaskReasons"]:
        if lk in fit and not isinstance(fit[lk], list):
            problems.append(f"not_list:{lk}")
    for r in fit.get("learningTaskReasons") or []:
        if not (isinstance(r, dict) and "skill" in r and "why" in r):
            problems.append("bad_learningTaskReason")
            break

    # 규칙엔진 정합(오라클 재계산)
    req = seed.get("requiredSkills") or []
    pref = seed.get("preferredSkills") or []
    fam_certs = DOMAIN_FAMILIES.get(seed.get("jobFamily"), {}).get("certs", [])
    m, mr, mp, score, decision = compute_fit(
        req, pref, seed.get("profileSkills") or [], seed.get("profileCertificates") or [],
        fam_certs, seed.get("experienceLevel"))
    if {norm(x) for x in m} != {norm(x) for x in (seed.get("matchedSkills") or [])}:
        problems.append("rule_inconsistent:matched")
    if {norm(x) for x in mr} != {norm(x) for x in (seed.get("missingRequiredSkills") or [])}:
        problems.append("rule_inconsistent:missingRequired")
    sc = seed.get("fitScore")
    if not isinstance(sc, int) or not (0 <= sc <= 100):
        problems.append("fitScore_out_of_range")
    else:
        exp_dec = "APPLY" if sc >= 80 else "COMPLEMENT_BEFORE_APPLY" if sc >= 60 else "HOLD"
        if seed.get("applyDecision") != exp_dec:
            problems.append("decision_mismatch")

    # 환각(스킬): learning skill 이 시드 역량 집합 안인지 (직군 무관)
    allowed = _seed_skill_norm(seed)
    for r in fit.get("learningTaskReasons") or []:
        skv = (r or {}).get("skill", "")
        if skv and norm(skv) not in allowed:
            problems.append(f"halluc_skill:{skv}")

    # 모순: 부족 필수역량을 강점에서 보유 주장(학습 프레이밍·보유 자격증 인용 제외)
    certs_join = " ".join(seed.get("profileCertificates") or [])
    for item in (fit.get("strengths") or []):
        if not isinstance(item, str) or any(mk in item for mk in LEARN_MARKERS):
            continue
        for s in (seed.get("missingRequiredSkills") or []):
            if mentions(item, s) and s not in certs_join:
                problems.append(f"contradiction_strength:{s}")

    # 경고: 비IT 직군에 IT 전용 표현 누출
    if group and not is_it_group(group):
        full = " ".join([fit.get("fitSummary", "")]
                        + [x for x in (fit.get("strengths") or []) if isinstance(x, str)]
                        + [x for x in (fit.get("risks") or []) if isinstance(x, str)]
                        + [x for x in (fit.get("strategyActions") or []) if isinstance(x, str)]
                        + [(r or {}).get("why", "") for r in (fit.get("learningTaskReasons") or [])])
        for tok in IT_LEAK_TOKENS:
            if mentions(full, tok) and norm(tok) not in allowed:
                warnings.append(f"it_leak:{tok}")

    return problems, warnings, info


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", required=True)
    ap.add_argument("--summary", default=None)
    args = ap.parse_args()

    with open(args.raw, "r", encoding="utf-8") as f:
        rows = json.load(f)

    total = len(rows)
    ok = 0
    prob_counter, warn_counter = {}, {}
    dec_counter, fam_counter, group_counter = {}, {}, {}
    it_rows = nonit_rows = 0
    scores = []
    fail_examples, warn_examples = [], []
    for row in rows:
        problems, warnings, info = check_row(row)
        d, fam, grp = info.get("decision"), info.get("family"), info.get("domainGroup")
        if d:
            dec_counter[d] = dec_counter.get(d, 0) + 1
        if fam:
            fam_counter[fam] = fam_counter.get(fam, 0) + 1
        if grp:
            group_counter[grp] = group_counter.get(grp, 0) + 1
            if is_it_group(grp):
                it_rows += 1
            else:
                nonit_rows += 1
        if isinstance(info.get("fitScore"), int):
            scores.append(info["fitScore"])
        for w in warnings:
            warn_counter[w.split(":")[0]] = warn_counter.get(w.split(":")[0], 0) + 1
        if warnings and len(warn_examples) < 10:
            warn_examples.append({"seedId": (row.get("seed") or {}).get("id"), "warnings": warnings})
        if not problems:
            ok += 1
        else:
            for p in problems:
                prob_counter[p.split(":")[0]] = prob_counter.get(p.split(":")[0], 0) + 1
            if len(fail_examples) < 12:
                fail_examples.append({"seedId": (row.get("seed") or {}).get("id"), "problems": problems})

    summary = {
        "total": total, "ok": ok, "failed": total - ok,
        "ok_rate": round(ok / total, 4) if total else 0,
        "problem_counts": prob_counter,
        "warning_counts": warn_counter,
        "distribution": {
            "domainGroup": group_counter,
            "decision": dec_counter,
            "family": fam_counter,
            "it_rows": it_rows, "nonit_rows": nonit_rows,
            "it_ratio": round(it_rows / total, 4) if total else 0,
            "nonit_ratio": round(nonit_rows / total, 4) if total else 0,
        },
        "fitScore": {
            "avg": round(sum(scores) / len(scores), 1) if scores else None,
            "min": min(scores) if scores else None,
            "max": max(scores) if scores else None,
        },
        "fail_examples": fail_examples,
        "warning_examples": warn_examples,
    }

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if args.summary:
        with open(args.summary, "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)
        print(f"\n요약 저장 -> {args.summary}")


if __name__ == "__main__":
    main()

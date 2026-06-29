"""R2e deterministic evidence gate post-filter (reports/58).

R2d 결론: 모델 prompt 로 conflation 을 못 막는다. 진짜 레버는 **출력을 서버측에서 결정론으로 거르는 것**.
이 모듈은 R2d evidence audit 을 단순 측정이 아니라 **gate** 로 쓴다: 모델 출력에 userEvidence 가 뒷받침하지
않는 보유 claim 이 있으면 reject / review / rewrite 한다. **LLM 재호출 없음**(전부 결정론 규칙).

원칙(엄수):
  - fitScore/applyDecision/matchedSkills/missingSkills 는 절대 변경하지 않는다.
  - free-text(fitSummary/strengths)의 unsupported 보유 표현만 rewrite 대상.
  - filteredOutput 은 originalOutput 을 덮어쓰지 않고 별도 보관.
  - JSON parse 실패를 rewrite 로 숨기지 않는다(PARSE_FAIL 로 드러냄).

서버측 post-filter 의 오프라인 프록시다(backend 통합 아님).
"""
import copy
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))  # ml/.../scripts
from compare_lora_with_evidence_gated_rag import (  # noqa: E402  (R2d audit 재사용)
    detect_unsupported_user_owned_claims, _bucket_text, _norm,
)
from eval_fit_model import extract_json_span, GROUNDING_SPLIT_RE, _grounding_violation_in_sentence  # noqa: E402

MODES = ("reject", "review", "rewrite")
# 보유 표현(unsupported)을 대체할 안전 문구. '보유' 등 possession 단어를 쓰지 않는다 —
# 쓰면 재-audit 에서 '{skill} … 보유' 가 다시 보유 주장으로 잡혀 violation 이 남는다(R2f 에서 발견).
_SAFE_TMPL = "{skill}은(는) 공고가 요구하는 역량이므로 학습·보완이 필요합니다."


def _classify_source(skill, buckets):
    if _bucket_text(buckets, "jobRequirements") and skill_in(buckets, "jobRequirements", skill):
        return "requirement_as_owned", "jobRequirements"
    if _bucket_text(buckets, "catalogFacts") and skill_in(buckets, "catalogFacts", skill):
        return "catalog_as_owned", "catalogFacts"
    if _bucket_text(buckets, "companyContext") and skill_in(buckets, "companyContext", skill):
        return "company_context_as_owned", "companyContext"
    return "unsupported_user_owned", "none"


def skill_in(buckets, key, skill):
    from eval_fit_model import term_in_text
    return term_in_text(_bucket_text(buckets, key), skill)


def _sentence_asserts(skill, sentence):
    return _grounding_violation_in_sentence(sentence, [skill], "rewrite") is not None


def _rewrite_text(text, unsupported):
    """문장 단위로, unsupported 스킬을 '보유'로 단정한 문장을 안전 문구로 치환(결정론)."""
    if not text:
        return text, 0
    parts = GROUNDING_SPLIT_RE.split(text)
    changed = 0
    out = []
    for s in parts:
        hit = next((sk for sk in unsupported if s.strip() and _sentence_asserts(sk, s)), None)
        if hit is not None:
            out.append(_SAFE_TMPL.format(skill=hit))
            changed += 1
        elif s.strip():
            out.append(s.strip())
    return (". ".join(out) + ".") if out else text, changed


def _rewrite_output(parsed, unsupported):
    """fitSummary/strengths 의 unsupported 보유 표현만 안전 문구로. 점수/판단/스킬필드 불변."""
    filtered = copy.deepcopy(parsed)
    total = 0
    if isinstance(filtered.get("fitSummary"), str):
        filtered["fitSummary"], c = _rewrite_text(filtered["fitSummary"], unsupported)
        total += c
    if isinstance(filtered.get("strengths"), list):
        new_items = []
        for item in filtered["strengths"]:
            txt = str(item)
            hit = next((sk for sk in unsupported if _sentence_asserts(sk, txt)), None)
            if hit is not None:
                new_items.append(_SAFE_TMPL.format(skill=hit))
                total += 1
            else:
                new_items.append(item)
        filtered["strengths"] = new_items
    return filtered, total


def gate_reasons(parsed, case, buckets):
    """unsupported 보유 claim 마다 gate 사유(type/claim/source/reason)."""
    unsup = detect_unsupported_user_owned_claims(json.dumps(parsed, ensure_ascii=False), case, buckets)
    reasons = []
    for sk in unsup:
        gtype, source = _classify_source(sk, buckets)
        reasons.append({"type": gtype, "claim": sk, "source": source,
                        "reason": f"'{sk}' 는 userEvidence 가 뒷받침하지 않는 보유 주장({source})"})
    return reasons, unsup


def apply_gate(content, case, buckets, mode="reject"):
    """모델 출력 content(str) → gate 결과. parse 실패는 PARSE_FAIL 로 드러내고 rewrite 로 숨기지 않는다."""
    assert mode in MODES, f"unknown mode: {mode}"
    try:
        parsed = json.loads(extract_json_span(content))
        if not isinstance(parsed, dict):
            raise ValueError("not object")
    except Exception:  # noqa: BLE001 — parse 실패는 은폐하지 않음
        return {"gateStatus": "PARSE_FAIL", "needsHumanReview": True, "gateReasons": [],
                "originalOutput": content, "filteredOutput": None}

    reasons, unsup = gate_reasons(parsed, case, buckets)
    base = {"originalOutput": parsed, "gateReasons": reasons}
    if not reasons:
        return {**base, "gateStatus": "PASSED", "needsHumanReview": False, "filteredOutput": parsed}
    if mode == "reject":
        return {**base, "gateStatus": "REJECTED", "needsHumanReview": True, "filteredOutput": parsed}
    if mode == "review":
        return {**base, "gateStatus": "REVIEW_REQUIRED", "needsHumanReview": True, "filteredOutput": parsed}
    # rewrite
    filtered, n = _rewrite_output(parsed, unsup)
    # 점수/판단 불변 단언(절대 변경 금지)
    for k in ("fitScore", "applyDecision", "matchedSkills", "missingSkills"):
        assert filtered.get(k) == parsed.get(k), f"gate 가 {k} 를 변경함(금지)"
    return {**base, "gateStatus": "REWRITTEN", "needsHumanReview": False,
            "filteredOutput": filtered, "rewriteCount": n}


def gate_summary(results):
    """gate 결과 리스트 → reject/review/rewrite/pass 카운트 + violation before."""
    out = {"n": len(results), "passed": 0, "rejected": 0, "review_required": 0, "rewritten": 0,
           "parse_fail": 0, "violation_claims": 0}
    for r in results:
        st = r.get("gateStatus")
        key = {"PASSED": "passed", "REJECTED": "rejected", "REVIEW_REQUIRED": "review_required",
               "REWRITTEN": "rewritten", "PARSE_FAIL": "parse_fail"}.get(st)
        if key:
            out[key] += 1
        out["violation_claims"] += len(r.get("gateReasons") or [])
    out["gate_pass_rate_before"] = round(out["passed"] / out["n"], 4) if out["n"] else 1.0
    return out

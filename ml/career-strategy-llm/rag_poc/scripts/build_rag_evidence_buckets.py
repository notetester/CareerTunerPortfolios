"""R2d evidence-gated retrievedContext 빌더 — D 변형 (reports/57).

R2c 결론: contextRole/claimPolicy + 프롬프트 가드를 넣어도 3B 모델은 conflation 을 못 줄였다(catalog
B=C=8). 그래서 R2d 는 '모델에게 구분을 맡기는' 방향을 버리고, **근거를 evidence bucket 으로 물리 분리**하고
**출력을 evidence audit 으로 사후 검증**한다. D 변형은 retrievedContext 를 4 버킷으로 나눠 전달한다:
  userEvidence(지원자 보유 근거) / jobRequirements(공고 요구) / catalogFacts(정의) / companyContext(회사 맥락).
프롬프트엔 'userEvidence 의 것만 보유로 말할 수 있다'는 Evidence Rules 를 명시한다.

**중복 구현 금지** — A/B/C pair 는 build_rag_scoped_context.build_abc_pairs 재사용, D 만 추가. 점수/판단 불변,
score/vectorDistance·값-수준 누수는 기존 가드 재사용. synthetic 전용. (audit 검출기는 비교 러너에 있음.)
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))  # ml/.../scripts
from build_retrieved_context import scan_text_for_score_leak  # noqa: E402  (값-수준 누수 가드 재사용)
from build_rag_scoped_context import build_abc_pairs  # noqa: E402  (A/B/C pair 재사용)
from build_rag_hard_cases import load_hard_cases  # noqa: E402

try:
    from synth_prompts import FIT_EXPLAIN_SYS  # noqa: E402
except Exception:  # noqa: BLE001
    FIT_EXPLAIN_SYS = ("너는 CareerTuner 의 커리어 전략 설명 모델이다. 입력에 없는 사실을 만들지 않고, "
                       "점수·applyDecision 은 서버 제공값을 그대로 둔다. 지정한 JSON 만 반환한다.")

# sourceType → evidence bucket. userEvidence 만 '보유 근거'가 될 수 있다.
BUCKET_MAP = {
    "user_profile_summary": "userEvidence",
    "job_posting": "jobRequirements",
    "job_requirement": "jobRequirements",
    "skill_catalog": "catalogFacts",
    "certification_catalog": "catalogFacts",
    "company_research_summary": "companyContext",
}
BUCKET_KEYS = ("userEvidence", "jobRequirements", "catalogFacts", "companyContext")
DEFAULT_BUCKET = "companyContext"  # 미지정 sourceType 은 보수적으로 맥락(보유 근거 아님)
ITEM_KEYS = ("sourceType", "sourceId", "text")
SCOPED_VARIANT = "lora_with_scoped_context"
EVIDENCE_VARIANT = "lora_with_evidence_gated_context"

EVIDENCE_RULES = (
    "\n[Evidence Rules] retrievedContext 는 evidenceBuckets 로 분리돼 있다. 엄수:"
    "\n1. userEvidence 에 있는 항목만 지원자가 '보유'한 역량으로 말할 수 있다."
    "\n2. jobRequirements 는 공고의 요구사항이다. 지원자 보유 역량이라고 말하지 마라."
    "\n3. catalogFacts 는 기술/자격증 정의다. 지원자 보유 근거가 아니다."
    "\n4. companyContext 는 회사/직무 맥락이다. 지원자 보유 근거가 아니다."
    "\n5. fitScore 와 applyDecision 은 서버 입력값 그대로 유지한다."
    "\n6. 근거가 불충분하면 '보유'가 아니라 '보완 필요'·'학습 추천'·'요구사항'으로 표현한다. userEvidence 가 비면 새 보유 역량 claim 을 만들지 마라."
)


def _clean_item(c):
    text = c.get("text", "")
    leak = scan_text_for_score_leak(text)
    if leak:
        raise AssertionError(f"evidenceBuckets text 값에 점수/판단 누수: {leak!r}")
    return {"sourceType": c.get("sourceType"), "sourceId": c.get("sourceId"), "text": text}


def _synth_user_evidence(case_input):
    """base input 의 matchedSkills(지원자 실제 보유)를 userEvidence 항목으로 재표현(새 정보 아님)."""
    matched = [s for s in (case_input.get("matchedSkills") or []) if str(s).strip()]
    if not matched:
        return []
    return [{"sourceType": "user_profile_summary", "sourceId": "profile-matched",
             "text": "지원자는 " + ", ".join(matched) + " 경험이 있습니다."}]


def to_evidence_buckets(retrieved_context, case_input=None):
    """retrievedContext(+matchedSkills) → {userEvidence, jobRequirements, catalogFacts, companyContext}.

    각 항목은 sourceType/sourceId/text 만(score/vector 제외, 값-수준 누수 차단). userEvidence 는
    user_profile_summary ctx + matchedSkills 재표현(보유 근거를 명시적으로 분리)."""
    buckets = {k: [] for k in BUCKET_KEYS}
    for c in (retrieved_context or []):
        bucket = BUCKET_MAP.get(c.get("sourceType"), DEFAULT_BUCKET)
        buckets[bucket].append(_clean_item(c))
    if case_input is not None:
        buckets["userEvidence"] = _synth_user_evidence(case_input) + buckets["userEvidence"]
    return buckets


def _render_evidence_user(case_input, buckets):
    """기존 입력 직렬화 + evidenceBuckets 블록. fitScore/applyDecision 그대로 노출(불변)."""
    base = {k: case_input.get(k) for k in (
        "profileSnapshot", "jobPostingSummary", "fitScore", "applyDecision",
        "matchedSkills", "missingSkills") if k in case_input}
    lines = [json.dumps(base, ensure_ascii=False), "evidenceBuckets:",
             json.dumps(buckets, ensure_ascii=False)]
    return "\n".join(lines)


def build_evidence_input(case_input, retrieved_context):
    inp = dict(case_input or {})
    inp["evidenceBuckets"] = to_evidence_buckets(retrieved_context, case_input)
    return inp


def build_evidence_messages(case_input, retrieved_context):
    sys_msg = FIT_EXPLAIN_SYS + EVIDENCE_RULES
    user = _render_evidence_user(case_input, to_evidence_buckets(retrieved_context, case_input))
    return [{"role": "system", "content": sys_msg}, {"role": "user", "content": user}]


def build_abcd_pairs(path=None):
    """A/B/C(build_abc_pairs 재사용) + D(evidence-gated) 4변형. 같은 base input, D 만 evidenceBuckets + Evidence Rules."""
    pairs = build_abc_pairs(path) if path else build_abc_pairs()
    cases = {c["caseId"]: c for c in (load_hard_cases(path) if path else load_hard_cases())}
    for p in pairs:
        c = cases[p["caseId"]]
        ctx = c["retrievedContext"]
        p["variants"][EVIDENCE_VARIANT] = {
            "input": build_evidence_input(c["input"], ctx),
            "messages": build_evidence_messages(c["input"], ctx),
        }
    return pairs


def main():
    pairs = build_abcd_pairs()
    print(f"A/B/C/D pairs={len(pairs)} (4변형: lora_only / +RAG / +scoped / +evidence-gated)")
    for p in pairs:
        b = p["variants"][EVIDENCE_VARIANT]["input"]["evidenceBuckets"]
        counts = {k: len(b[k]) for k in BUCKET_KEYS}
        print(f"  {p['caseId']} [{p.get('hardType')}]: buckets {counts}")


if __name__ == "__main__":
    main()

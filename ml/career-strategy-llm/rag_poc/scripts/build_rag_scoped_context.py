"""R2c scoped/guarded retrievedContext 빌더 — grounding conflation 완화 실험 (reports/56).

R2b 실측에서 RAG 의 net wash + **E1 grounding 악화**가 확인됐다. 원인은 모델이 retrievedContext 의
'직무 요구/catalog 정의'를 '지원자 보유 역량'으로 혼동(conflation)하는 것(예: catalog 가
"정보처리기사는 자료구조·알고리즘 검증"이라 했을 뿐인데 지원자가 자료구조·알고리즘을 보유한다고 서술).

C 변형은 retrievedContext 각 항목에 **역할/소유/주장정책(contextRole/ownership/claimPolicy)** 을 붙이고
시스템 프롬프트에 **claim guard** 를 추가해 그 혼동을 직접 차단한다. 기존 A/B(build_rag_hard_cases)는
그대로 재사용하고 C 변형만 추가한다(중복 구현 금지). **fitScore/applyDecision·점수/판단 불변**, score/
vectorDistance 등 금지 키와 text 값-수준 점수 누수는 기존 가드를 재사용해 막는다. synthetic 전용.

A = lora_only · B = lora_with_retrieved_context(기존 ctx) · C = lora_with_scoped_context(역할/가드).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))  # ml/.../scripts
from build_retrieved_context import scan_text_for_score_leak  # noqa: E402  (값-수준 누수 가드 재사용)
from build_rag_hard_cases import build_hard_pairs, load_hard_cases  # noqa: E402  (A/B pair 재사용)

try:
    from synth_prompts import FIT_EXPLAIN_SYS  # noqa: E402  (train/serve 일관성)
except Exception:  # noqa: BLE001
    FIT_EXPLAIN_SYS = ("너는 CareerTuner 의 커리어 전략 설명 모델이다. 입력에 없는 사실을 만들지 않고, "
                       "점수·applyDecision 은 서버 제공값을 그대로 둔다. 지정한 JSON 만 반환한다.")

# sourceType → (contextRole, ownership, claimPolicy). 모르는 sourceType 은 보수적으로 '보유로 보지 마라'.
ROLE_MAP = {
    "job_posting":               ("job_requirement", "employer_required", "do_not_treat_as_user_owned"),
    "job_requirement":           ("job_requirement", "employer_required", "do_not_treat_as_user_owned"),
    "skill_catalog":             ("catalog_fact", "global_fact", "definition_only_not_user_owned"),
    "certification_catalog":     ("catalog_fact", "global_fact", "definition_only_not_user_owned"),
    "company_research_summary":  ("company_context", "global_fact", "context_only_not_user_owned"),
    "user_profile_summary":      ("user_evidence", "user_owned", "may_treat_as_user_owned_if_text_supports"),
}
DEFAULT_ROLE = ("unknown_context", "unknown", "do_not_treat_as_user_owned")
SCOPED_KEYS = ("sourceType", "sourceId", "text", "contextRole", "ownership", "claimPolicy")

# C 변형 전용 프롬프트 가드 — '공고에 있음/정의됨' ≠ '지원자 보유' 를 명시.
SCOPED_RAG_ADDENDUM = (
    "\n[RAG scoped 지침] retrievedContext 의 각 항목은 contextRole/ownership/claimPolicy 를 가진다. 엄수:"
    "\n- contextRole=job_requirement 는 공고가 '요구'하는 역량이다. 거기 나온 기술을 지원자가 '보유'한다고 서술하지 마라."
    "\n- contextRole=catalog_fact 는 정의/설명 근거일 뿐, 지원자의 보유 역량 증거가 아니다(정의를 보유로 바꾸지 마라)."
    "\n- contextRole=company_context 는 회사/직무 배경일 뿐, 지원자 보유 역량이 아니다."
    "\n- contextRole=user_evidence 의 내용만 지원자 보유 역량으로 서술할 수 있다."
    "\n- fitScore 와 applyDecision 은 서버 입력값 그대로 두고 절대 바꾸지 않는다. retrievedContext 는 설명 근거이며 점수/판단을 바꾸지 않는다."
)


def role_for(source_type):
    """sourceType → (contextRole, ownership, claimPolicy). 미지정은 보수 기본값."""
    return ROLE_MAP.get(source_type, DEFAULT_ROLE)


def scope_item(c):
    """retrievedContext 한 항목에 역할/소유/주장정책을 부여(원본 키는 sourceType/sourceId/text 만 사용).

    score/vectorDistance 등은 화이트리스트(SCOPED_KEYS)로 자동 제외되고, text 값-수준 점수/판단 누수는
    scan_text_for_score_leak 로 차단(점수/판단은 rule engine 소유 — ctx 로 새면 안 됨)."""
    st = c.get("sourceType")
    role, own, policy = role_for(st)
    text = c.get("text", "")
    leak = scan_text_for_score_leak(text)
    if leak:
        raise AssertionError(f"scoped retrievedContext text 값에 점수/판단 누수: {leak!r}")
    return {"sourceType": st, "sourceId": c.get("sourceId"), "text": text,
            "contextRole": role, "ownership": own, "claimPolicy": policy}


def sanitize_scoped_context(retrieved_context):
    """retrievedContext 를 SCOPED_KEYS(역할 포함)로만 정제. 점수/판단 키·값 누수 차단."""
    return [scope_item(c) for c in (retrieved_context or [])]


def _render_scoped_user(case_input, scoped_context):
    """기존 입력 직렬화 + (있으면) scoped retrievedContext 블록. fitScore/applyDecision 그대로 노출(불변)."""
    base = {k: case_input.get(k) for k in (
        "profileSnapshot", "jobPostingSummary", "fitScore", "applyDecision",
        "matchedSkills", "missingSkills") if k in case_input}
    lines = [json.dumps(base, ensure_ascii=False)]
    if scoped_context:
        lines.append("retrievedContext:")
        lines.append(json.dumps(sanitize_scoped_context(scoped_context), ensure_ascii=False))
    return "\n".join(lines)


def build_scoped_input(case_input, scoped_context):
    """case 입력 + scoped retrievedContext → 모델 입력 dict(원본 비파괴, fitScore/applyDecision 보존)."""
    inp = dict(case_input or {})
    inp["retrievedContext"] = sanitize_scoped_context(scoped_context)
    return inp


def build_scoped_messages(case_input, scoped_context):
    """[system(FIT_EXPLAIN_SYS + claim guard), user(scoped ctx)] 메시지(C 변형 전용)."""
    sys_msg = FIT_EXPLAIN_SYS + SCOPED_RAG_ADDENDUM
    user = _render_scoped_user(case_input, scoped_context)
    return [{"role": "system", "content": sys_msg}, {"role": "user", "content": user}]


SCOPED_VARIANT = "lora_with_scoped_context"


def build_abc_pairs(path=None):
    """A/B(build_hard_pairs 재사용) + C(scoped) 3변형 pair. 같은 caseId·base input,
    C 만 scoped retrievedContext + claim guard 를 가진다(차이는 그것뿐)."""
    pairs = build_hard_pairs(path) if path else build_hard_pairs()
    cases = {c["caseId"]: c for c in (load_hard_cases(path) if path else load_hard_cases())}
    for p in pairs:
        c = cases[p["caseId"]]
        ctx = c["retrievedContext"]
        p["variants"][SCOPED_VARIANT] = {
            "input": build_scoped_input(c["input"], ctx),
            "messages": build_scoped_messages(c["input"], ctx),
        }
        p["hardType"] = c.get("hardType")
        p["ragGoal"] = c.get("ragGoal")
    return pairs


def main():
    pairs = build_abc_pairs()
    print(f"A/B/C pairs={len(pairs)} (각 pair=3변형: lora_only / lora_with_retrieved_context / {SCOPED_VARIANT})")
    for p in pairs:
        c = p["variants"][SCOPED_VARIANT]["input"].get("retrievedContext") or []
        roles = [f"{i.get('contextRole')}({i.get('claimPolicy')})" for i in c]
        print(f"  {p['caseId']} [{p.get('hardType')}]: scoped ctx {len(c)}건 → {roles}")


if __name__ == "__main__":
    main()

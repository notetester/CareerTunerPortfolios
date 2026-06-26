"""RAG prompt builder — stage R2: 3B LoRA 입력에 retrievedContext 를 부가 근거로만 주입.

원칙(reports/50 §9, 53):
  - fitScore/applyDecision 은 서버 입력값 그대로 유지(변경/생성 금지).
  - retrievedContext 는 설명 근거로만. sourceType/sourceId/text 만 포함(score/vector/ranking metadata 금지).
  - 기존 입력 구조를 깨지 않고 retrievedContext 만 부가.
system 메시지는 기존 synth_prompts.FIT_EXPLAIN_SYS 재사용(train/serve/eval skew 방지) + RAG 지침 addendum(B 변형만).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))  # ml/.../scripts
from build_retrieved_context import FORBIDDEN_KEYS  # noqa: E402

try:
    from synth_prompts import FIT_EXPLAIN_SYS  # noqa: E402  (train/serve 일관성)
except Exception:  # noqa: BLE001 — 경로 환경에 따라 import 실패 시 최소 시스템 메시지
    FIT_EXPLAIN_SYS = ("너는 CareerTuner 의 커리어 전략 설명 모델이다. 입력에 없는 사실을 만들지 않고, "
                       "점수·applyDecision 은 서버 제공값을 그대로 둔다. 지정한 JSON 만 반환한다.")

RAG_SYS_ADDENDUM = (
    "\n[RAG 지침] 아래 retrievedContext 는 근거로만 사용한다. retrievedContext 에 없는 회사 사실·제품·자격을 "
    "만들지 않는다. fitScore 와 applyDecision 은 입력값을 그대로 두고 절대 바꾸지 않는다."
)

CONTEXT_KEYS = ("sourceType", "sourceId", "text")


def sanitize_context(retrieved_context):
    """retrievedContext 를 sourceType/sourceId/text 로만 정제(score/vector/ranking metadata 제거·금지키 가드)."""
    out = []
    for c in retrieved_context or []:
        item = {"sourceType": c.get("sourceType"), "sourceId": c.get("sourceId"), "text": c.get("text", "")}
        bad = FORBIDDEN_KEYS & set(item.keys())
        if bad:
            raise AssertionError(f"retrievedContext 금지 키: {bad}")
        out.append(item)
    return out


def build_rag_input(case_input, retrieved_context=None):
    """case 입력(+선택 retrievedContext) → 모델 입력 dict. fitScore/applyDecision 보존."""
    inp = dict(case_input or {})           # 원본 비파괴 복사
    if retrieved_context is not None:
        inp["retrievedContext"] = sanitize_context(retrieved_context)
    return inp


def _render_user(case_input, retrieved_context):
    """기존 입력 직렬화 + (있으면) retrievedContext 블록. fitScore/applyDecision 그대로 노출."""
    base = {k: case_input.get(k) for k in (
        "profileSnapshot", "jobPostingSummary", "fitScore", "applyDecision",
        "matchedSkills", "missingSkills") if k in case_input}
    lines = [json.dumps(base, ensure_ascii=False)]
    if retrieved_context:
        lines.append("retrievedContext:")
        lines.append(json.dumps(sanitize_context(retrieved_context), ensure_ascii=False))
    return "\n".join(lines)


def build_messages(case_input, retrieved_context=None, with_context=False):
    """[system, user] 메시지. with_context=False 면 retrievedContext 미포함(변형 A)."""
    sys_msg = FIT_EXPLAIN_SYS + (RAG_SYS_ADDENDUM if with_context else "")
    user = _render_user(case_input, retrieved_context if with_context else None)
    return [{"role": "system", "content": sys_msg}, {"role": "user", "content": user}]

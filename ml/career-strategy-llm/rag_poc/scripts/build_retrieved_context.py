"""retrievedContext builder — retrieval 결과를 3B LoRA prompt 주입용 구조로 조립.

원칙(reports/50 §9):
  - retrievedContext 는 **설명 근거로만** 쓴다.
  - **fitScore/applyDecision 을 절대 생성하지 않는다**(점수·판단은 rule engine/server 소유).
  - 각 항목은 sourceType/sourceId/text 만 유지(score 등 내부값은 prompt 에 넣지 않음).
"""

# prompt context 에 절대 들어가면 안 되는 점수/판단 키(가드)
FORBIDDEN_KEYS = {"fitScore", "score", "applyDecision", "decision", "fitscore", "applydecision"}


def build_retrieved_context(results, *, max_items=8):
    """retrieve() 결과 → {"retrievedContext": [{sourceType, sourceId, text}, ...]}.

    점수/판단 키는 생성하지 않는다. score 등 retrieval 내부 메타는 prompt 컨텍스트에서 제외.
    """
    items = []
    for r in results[:max_items]:
        items.append({
            "sourceType": r.get("sourceType"),
            "sourceId": r.get("sourceId"),
            "text": r.get("text", ""),
        })
    ctx = {"retrievedContext": items}
    _assert_no_score_keys(ctx)
    return ctx


def _assert_no_score_keys(ctx):
    """방어적 자가검증: 조립 결과 어디에도 점수/판단 키가 없어야 한다."""
    for item in ctx.get("retrievedContext", []):
        bad = FORBIDDEN_KEYS & set(item.keys())
        if bad:
            raise AssertionError(f"retrievedContext 에 금지 키 포함: {bad}")
    return True

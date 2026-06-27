"""retrievedContext builder — retrieval 결과를 3B LoRA prompt 주입용 구조로 조립.

원칙(reports/50 §9):
  - retrievedContext 는 **설명 근거로만** 쓴다.
  - **fitScore/applyDecision 을 절대 생성하지 않는다**(점수·판단은 rule engine/server 소유).
  - 각 항목은 sourceType/sourceId/text 만 유지(score 등 내부값은 prompt 에 넣지 않음).

방어 계층(reports/55, 2026-06-27):
  1차(구조) = **화이트리스트 재구성**: 항상 sourceType/sourceId/text 3키로만 조립 → 점수/판단 키 원천 제외.
  2차(회귀가드) = `_assert_no_score_keys`: 출력 항목 키가 정확히 화이트리스트인지 + text 값에 점수/판단이
       기계식(JSON 유사)으로 박혀 있지 않은지 검사. 누군가 builder 를 pass-through 로 리팩터하거나 text 에
       'fitScore: 92' 가 새어들면 여기서 발화한다(과거엔 재구성 뒤 키만 봐서 절대 발화 못 하는 dead guard 였음).
"""
import re

# prompt context 에 절대 들어가면 안 되는 점수/판단/랭킹 키
FORBIDDEN_KEYS = {
    "fitScore", "score", "applyDecision", "decision", "fitscore", "applydecision",
    "vectorDistance", "vectordistance", "rank", "ranking", "distance",
}
# 출력 항목에 허용되는 키(화이트리스트)
CONTEXT_KEYS = ("sourceType", "sourceId", "text")
# 값-수준 누수: text 안에 점수/판단이 기계식 키:값('fitScore: 92', 'applyDecision":"REJECT"')으로 박힌 경우.
_VALUE_LEAK_RE = re.compile(r'(?i)\b(fitScore|applyDecision)\b\s*["”]?\s*[:=]')


def scan_text_for_score_leak(text):
    """text 값에 fitScore/applyDecision 이 기계식 키:값 형태로 박혀 있으면 매치 문자열 반환(없으면 None).
    점수·판단은 rule engine 소유라 retrievedContext text 로 새면 '점수 불변식'을 우회한다.
    자유 산문의 '적합도' 같은 한국어 표현엔 매칭 안 됨(camelCase 토큰 + 콜론/등호 형태만)."""
    m = _VALUE_LEAK_RE.search(str(text or ""))
    return m.group(0) if m else None


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
    """출력 불변식 회귀 가드(화이트리스트가 1차 방어). 각 항목 키가 정확히 화이트리스트이고
    text 값에 점수/판단 누수가 없어야 한다. 재구성을 우회/리팩터해도 여기서 잡힌다."""
    allow = set(CONTEXT_KEYS)
    for item in ctx.get("retrievedContext", []):
        extra = set(item.keys()) - allow
        if extra:
            raise AssertionError(f"retrievedContext 항목에 허용되지 않은 키: {extra}")
        leak = scan_text_for_score_leak(item.get("text", ""))
        if leak:
            raise AssertionError(f"retrievedContext text 값에 점수/판단 누수: {leak!r}")
    return True

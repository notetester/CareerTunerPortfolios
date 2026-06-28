"""RAG offline retrieval PoC — stage R1 (reports/50·51).

운영 복잡도 최소화: 대형 embedding/벡터DB 없이 **결정론 lexical(token-overlap) scorer**로 시작.
이 R1의 핵심 검증은 검색 품질이 아니라 **scope filter(global / user_private / application_private)가
fail-closed 로 동작하는가**다. embedding(bge-m3/multilingual-e5) + FAISS/Chroma 확장은 후속
R1b/R2(README 참조). **실제 개인정보 미사용 — synthetic fixture 전용.**

scope 규칙(reports/50 §8):
  - global              → 모든 요청 검색 가능
  - user_private        → 요청 userId == chunk userId 일 때만(요청 userId 없으면 0건)
  - application_private → 요청 userId·applicationId 둘 다 chunk 와 일치할 때만(둘 중 없으면 0건)
  - 그 외/미상 visibility → fail-closed(False)
"""
import json
import re

VISIBILITIES = {"global", "user_private", "application_private"}
TOKEN_RE = re.compile(r"[A-Za-z0-9가-힣]+")


def _tokens(s):
    return [t.lower() for t in TOKEN_RE.findall(str(s or ""))]


def load_chunks(path):
    return [json.loads(line) for line in open(path, encoding="utf-8") if line.strip()]


def in_scope(chunk, *, user_id=None, application_id=None):
    """scope filter — fail-closed. 필요한 id 가 없으면 False(검색 불가)."""
    vis = chunk.get("visibility")
    if vis == "global":
        return True
    if vis == "user_private":
        return bool(user_id) and chunk.get("userId") == user_id
    if vis == "application_private":
        return (bool(user_id) and bool(application_id)
                and chunk.get("userId") == user_id
                and chunk.get("applicationId") == application_id)
    return False  # 미상 visibility → fail-closed


def _score(query, text):
    """결정론 token-overlap(query coverage). 0~1. R1 한정 — 의미검색은 R1b embedding 으로 대체 예정."""
    q = set(_tokens(query))
    d = set(_tokens(text))
    if not q or not d:
        return 0.0
    return round(len(q & d) / len(q), 4)


def retrieve(chunks, query, *, user_id=None, application_id=None, top_k=5, min_score=0.0):
    """scope filter 를 **검색 전에** 적용한 뒤 lexical 점수로 top_k 반환.

    반환 item: {chunkId, sourceType, sourceId, visibility, score, text}
    """
    allowed = [c for c in chunks if in_scope(c, user_id=user_id, application_id=application_id)]
    scored = []
    for c in allowed:
        s = _score(query, c.get("text", ""))
        if s > min_score:
            scored.append({
                "chunkId": c.get("chunkId"),
                "sourceType": c.get("sourceType"),
                "sourceId": c.get("sourceId"),
                "visibility": c.get("visibility"),
                "score": s,
                "text": c.get("text", ""),
            })
    scored.sort(key=lambda r: (-r["score"], str(r["chunkId"])))
    return scored[:top_k]

"""RAG offline PoC — stage R1b: local embedding + vector(cosine) search.

R1(lexical token-overlap) 위에 **의미검색 구조**를 얹는다. 핵심은 검색 품질 SOTA 가 아니라:
  - scope filter(R1 규칙)를 **vector search 전에** 적용 → 격리·fail-closed 유지
  - retrievedContext builder 불변(점수/판단 미생성)
  - **외부 API·대형 다운로드 없이 로컬·오프라인 재현**

임베딩 백엔드(우선순위):
  1) sentence-transformers(다국어 소형) — 환경에 설치돼 있고 `CT_RAG_ST_MODEL` 지정 시에만. (자동 대형 다운로드 안 함)
  2) **HashEmbedder(기본 fallback)** — 순수 파이썬 char n-gram + 토큰 해시 임베딩(결정론, 의존성 0, 오프라인).
     의미 SOTA 는 아니지만 부분일치(SQL↔SQLD 등)를 n-gram 으로 포착해 vector-search '구조'를 검증.
HashEmbedder 는 hashlib(md5) 사용 — 파이썬 내장 hash()는 프로세스마다 salt 가 달라 재현 불가라 금지.
실제 의미검색(bge-m3 / multilingual-e5)은 R2/production 경로(reports/52 참조). 개인정보는 로컬 임베딩만(외부 전송 금지).
"""
import hashlib
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from offline_retriever import in_scope  # noqa: E402  (R1 의 scope 규칙 재사용 — 단일 출처)

TOKEN_RE = re.compile(r"[A-Za-z0-9가-힣]+")


def _features(text, ngram_min=2, ngram_max=3):
    """토큰 + char n-gram 피처(부분일치 포착용)."""
    toks = TOKEN_RE.findall(str(text or "").lower())
    feats = list(toks)
    for t in toks:
        for n in range(ngram_min, ngram_max + 1):
            if len(t) >= n:
                feats += [f"{n}:{t[i:i+n]}" for i in range(len(t) - n + 1)]
    return feats


def _hash_idx_sign(feature, dim):
    d = hashlib.md5(feature.encode("utf-8")).digest()   # 결정론(내장 hash() 금지)
    idx = int.from_bytes(d[:4], "big") % dim
    sign = 1.0 if (d[4] & 1) else -1.0
    return idx, sign


class HashEmbedder:
    name = "hash-ngram (deterministic, offline, no-download)"

    def __init__(self, dim=256):
        self.dim = dim

    def embed(self, text):
        v = [0.0] * self.dim
        for f in _features(text):
            i, s = _hash_idx_sign(f, self.dim)
            v[i] += s
        norm = math.sqrt(sum(x * x for x in v)) or 1.0
        return [x / norm for x in v]


class SentenceTransformerEmbedder:
    """설치돼 있고 CT_RAG_ST_MODEL 지정 시에만 사용(자동 대형 다운로드 안 함)."""

    def __init__(self, model_name):
        from sentence_transformers import SentenceTransformer  # noqa
        self.model = SentenceTransformer(model_name)
        self.name = f"sentence-transformers:{model_name}"

    def embed(self, text):
        v = self.model.encode([str(text or "")], normalize_embeddings=True)[0]
        return [float(x) for x in v]


def get_embedder(dim=256):
    """ST 가 가능하고 CT_RAG_ST_MODEL 이 지정됐으면 ST, 아니면 HashEmbedder(기본)."""
    model_name = os.environ.get("CT_RAG_ST_MODEL")
    if model_name:
        try:
            return SentenceTransformerEmbedder(model_name)
        except Exception as e:  # noqa: BLE001 — 설치/다운로드 실패 시 안전 fallback
            print(f"[embedding_retriever] ST 사용 불가({e}) → HashEmbedder fallback", file=sys.stderr)
    return HashEmbedder(dim=dim)


def cosine(a, b):
    # 둘 다 L2 정규화돼 있으므로 dot = cosine
    return round(sum(x * y for x, y in zip(a, b)), 6)


class EmbeddingRetriever:
    """scope filter(검색 전) → 허용 chunk 만 cosine 랭킹 → top_k."""

    def __init__(self, chunks, embedder=None):
        self.embedder = embedder or get_embedder()
        self.chunks = chunks
        self.vectors = {c.get("chunkId"): self.embedder.embed(c.get("text", "")) for c in chunks}

    def allowed_ids(self, *, user_id=None, application_id=None):
        return {c.get("chunkId") for c in self.chunks
                if in_scope(c, user_id=user_id, application_id=application_id)}

    def retrieve(self, query, *, user_id=None, application_id=None, top_k=5, min_score=None):
        # ① scope filter 를 **검색 전에** 적용 — 허용 chunk 만 후보로
        allowed = [c for c in self.chunks if in_scope(c, user_id=user_id, application_id=application_id)]
        # ② 허용 후보에 대해서만 임베딩 cosine 랭킹
        qv = self.embedder.embed(query)
        scored = []
        for c in allowed:
            s = cosine(qv, self.vectors[c.get("chunkId")])
            if min_score is None or s > min_score:
                scored.append({
                    "chunkId": c.get("chunkId"), "sourceType": c.get("sourceType"),
                    "sourceId": c.get("sourceId"), "visibility": c.get("visibility"),
                    "score": s, "text": c.get("text", ""),
                })
        scored.sort(key=lambda r: (-r["score"], str(r["chunkId"])))
        return scored[:top_k]

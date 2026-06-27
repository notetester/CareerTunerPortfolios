"""lexical(R1) vs semantic(R1b) 검색 비교 — 재현 가능한 형태로 출력.

실행: python rag_poc/scripts/compare_lexical_semantic.py
동일 scope(user-a/app-a, 관련 chunk 가 모두 in-scope)에서 품질 query 별 top-k 를 양쪽으로 출력.
HashEmbedder 는 결정론이라 출력이 재현 가능. (실제 의미 우월성은 ST/e5 백엔드에서 평가 — reports/52.)
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from offline_retriever import load_chunks, retrieve as lexical_retrieve  # noqa: E402
from embedding_retriever import EmbeddingRetriever, get_embedder  # noqa: E402

FIX = os.path.join(HERE, "..", "fixtures")

QUERIES = [
    "SQL 자격증과 데이터 모델링",
    "Java 백엔드 프레임워크",
    "REST API 서버 개발",
    "데이터 파이프라인 경험",
]
# 비교는 동일 scope 에서(user-a/app-a) — 관련 chunk 가 후보에 들어오도록
SCOPE = {"user_id": "user-a", "application_id": "app-a"}


def _ids_scores(results, k=3):
    return [(r["chunkId"], r["score"]) for r in results[:k]]


def main():
    chunks = load_chunks(os.path.join(FIX, "sample_chunks.jsonl"))
    emb = get_embedder()
    sem = EmbeddingRetriever(chunks, embedder=emb)
    print(f"semantic backend: {getattr(emb, 'name', '?')}  (scope={SCOPE})\n")
    out = []
    for q in QUERIES:
        lex = lexical_retrieve(chunks, q, top_k=3, **SCOPE)
        se = sem.retrieve(q, top_k=3, **SCOPE)
        row = {"query": q, "lexical_top3": _ids_scores(lex), "semantic_top3": _ids_scores(se)}
        out.append(row)
        print(f"### {q}")
        print(f"  lexical : {row['lexical_top3']}")
        print(f"  semantic: {row['semantic_top3']}")
        print()
    # 재현 가능한 JSON 도 출력(테스트/문서 비교용)
    print("REPRODUCIBLE_JSON:", json.dumps(out, ensure_ascii=False))


if __name__ == "__main__":
    main()

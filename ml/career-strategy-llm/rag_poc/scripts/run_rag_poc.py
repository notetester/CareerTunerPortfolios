"""RAG offline PoC 드라이버 — fixture 로 지원 건별 retrieve → retrievedContext 조립 출력.

실행: python rag_poc/scripts/run_rag_poc.py
(Ollama/3B LoRA 호출 없음 — retrievedContext 조립까지만. 실제 prompt 실험은 R2.)
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from offline_retriever import load_chunks, retrieve  # noqa: E402
from build_retrieved_context import build_retrieved_context  # noqa: E402

FIX = os.path.join(HERE, "..", "fixtures")


def main():
    chunks = load_chunks(os.path.join(FIX, "sample_chunks.jsonl"))
    queries = [json.loads(l) for l in open(os.path.join(FIX, "sample_queries.jsonl"), encoding="utf-8") if l.strip()]
    print(f"chunks={len(chunks)}  queries={len(queries)}\n")
    for q in queries:
        res = retrieve(chunks, q["query"], user_id=q.get("userId"),
                       application_id=q.get("applicationId"), top_k=5)
        ctx = build_retrieved_context(res)
        print(f"### {q['queryId']}  (userId={q.get('userId')} applicationId={q.get('applicationId')})")
        print(f"  note: {q.get('note')}")
        print(f"  retrieved {len(res)} chunks(scope 통과 후): "
              f"{[(r['chunkId'], r['visibility'], r['score']) for r in res]}")
        print(f"  retrievedContext(sourceType/sourceId/text only): "
              f"{json.dumps(ctx, ensure_ascii=False)[:300]}...")
        print()


if __name__ == "__main__":
    main()

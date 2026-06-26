"""vector index 생성 — chunk 임베딩을 out/(gitignore) 에 캐시.

실행: python rag_poc/scripts/build_vector_index.py
산출물(rag_poc/out/vector_index.json)은 **gitignore 대상 — 커밋 금지**(생성된 embedding).
retriever 는 이 캐시가 없어도 on-the-fly 임베딩으로 동작하므로, 이 스크립트는 인덱싱 파이프라인 시연/캐시용.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from offline_retriever import load_chunks  # noqa: E402
from embedding_retriever import get_embedder  # noqa: E402

FIX = os.path.join(HERE, "..", "fixtures")
OUT = os.path.join(HERE, "..", "out")


def main():
    os.makedirs(OUT, exist_ok=True)
    chunks = load_chunks(os.path.join(FIX, "sample_chunks.jsonl"))
    emb = get_embedder()
    index = {"backend": getattr(emb, "name", "unknown"), "dim": len(emb.embed("x")), "vectors": {}}
    for c in chunks:
        index["vectors"][c["chunkId"]] = {
            "visibility": c.get("visibility"), "userId": c.get("userId"),
            "applicationId": c.get("applicationId"), "vector": emb.embed(c.get("text", "")),
        }
    out_path = os.path.join(OUT, "vector_index.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False)
    print(f"backend={index['backend']} dim={index['dim']} chunks={len(index['vectors'])}")
    print(f"  → {out_path}  (gitignore 대상 — 커밋 금지)")


if __name__ == "__main__":
    main()

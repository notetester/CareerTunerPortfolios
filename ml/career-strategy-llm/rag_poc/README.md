# RAG offline retrieval PoC (stage R1)

reports/50 RAG 설계의 **오프라인 검색 PoC**. backend 통합 전, 지원 건 1개 기준으로 핵심 가설을 코드로 검증한다.
**범위: 오프라인만.** Spring Boot API·runtime prompt·Vector DB 서버·실제 embedding·Ollama 호출·실서비스 설정은 **변경/사용하지 않는다.** 실제 개인정보 없이 **synthetic fixture 전용.**

## 검증 목표 (reports/50 → 코드)
1. chunk metadata 설계가 실제 코드로 표현 가능한가 (`fixtures/sample_chunks.jsonl`)
2. global / user_private / application_private **scope filter** 가 동작하는가
3. 필요한 id 누락 시 **fail-closed** 인가
4. `retrievedContext[]` 를 3B LoRA prompt 주입 형태로 조립 가능한가
5. RAG 가 **점수/applyDecision 을 침범하지 않는가**

## 구조
```text
rag_poc/
  fixtures/sample_chunks.jsonl     synthetic chunk(5유형: job_posting/user_profile_summary/
                                   company_research_summary/certification_catalog/skill_catalog)
  fixtures/sample_queries.jsonl    지원 건별 query(user-a/app-a, user-b/app-b, no-user 등)
  scripts/offline_retriever.py     scope filter(fail-closed) + 결정론 token-overlap scorer
  scripts/build_retrieved_context.py  retrievedContext 조립(점수/판단 키 금지 가드)
  scripts/run_rag_poc.py           드라이버(검색→컨텍스트 조립 출력, Ollama 호출 없음)
  scripts/embedding_retriever.py   [R1b] local embedding(HashEmbedder fallback / ST optional) + cosine, scope-before-search
  scripts/build_vector_index.py    [R1b] chunk 임베딩 캐시 → out/(gitignore, 커밋 금지)
  scripts/compare_lexical_semantic.py [R1b] lexical vs semantic 비교(결정론·재현)
  tests/test_scope_filter.py       scope/fail-closed (lexical)
  tests/test_context_builder.py    sourceType/sourceId/text 유지·점수/판단 미생성
  tests/test_embedding_retriever_scope.py   [R1b] semantic scope/fail-closed(검색 전 필터)
  tests/test_embedding_retriever_quality.py [R1b] 재현성·격리불변·컨텍스트 불변
  out/                             [R1b] 생성 embedding/index 캐시 — gitignore, 커밋 금지
```

## 실행
```bash
cd ml/career-strategy-llm
python rag_poc/scripts/run_rag_poc.py            # 검색·컨텍스트 조립 데모
python rag_poc/tests/test_scope_filter.py        # 보안(scope/fail-closed) 테스트
python rag_poc/tests/test_context_builder.py     # 컨텍스트 빌더 테스트
```
stdlib만 사용(네트워크·대형 모델·벡터DB 불필요).

## R1 한정 사항 & 다음 단계
- R1: **결정론 lexical(token-overlap)** — scope 격리·fail-closed 검증.
- **R1b(완료)**: local embedding + cosine vector search. 환경에 numpy/ST/sklearn 부재 → **순수 파이썬 HashEmbedder(결정론·오프라인) 기본**, `CT_RAG_ST_MODEL` 지정 시 sentence-transformers 사용(자동 대형 다운로드 안 함). 동일 scope filter·fixture 유지, **scope filter 를 vector search 전에** 적용. 실제 의미모델(`bge-m3`/`multilingual-e5`)은 환경 구비 시 동일 인터페이스로 교체(reports/52).
- **R2**: `retrievedContext` 를 3B LoRA prompt 에 실제 주입해 A(LoRA) vs B(LoRA+RAG) 오프라인 비교(reports/50 §10).
- **R3+**: Spring(Spring AI/LangChain4j) backend service 통합(점수/판단·E1/E2 불변).

## 안전
실제 개인정보 미사용. raw retrieved chunk·embedding·private data 를 repo/로그에 커밋하지 않는다. 점수·applyDecision 은 rule engine/server 소유(이 PoC 는 생성하지 않음).

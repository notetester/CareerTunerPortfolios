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
  tests/test_scope_filter.py       scope/fail-closed 8케이스
  tests/test_context_builder.py    sourceType/sourceId/text 유지·점수/판단 미생성
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
- 검색은 **결정론 lexical(token-overlap)** 로 시작(운영 복잡도 최소화). 이 단계의 핵심은 검색 품질이 아니라 **scope 격리·fail-closed** 검증이다.
- **R1b**: 로컬 embedding(`bge-m3` / `multilingual-e5`) + 임베디드 vector store(FAISS/Chroma)로 의미검색 확장 — 동일 scope filter·fixture 유지, 외부 API·개인정보 전송 없음.
- **R2**: `retrievedContext` 를 3B LoRA prompt 에 실제 주입해 A(LoRA) vs B(LoRA+RAG) 오프라인 비교(reports/50 §10).
- **R3+**: Spring(Spring AI/LangChain4j) backend service 통합(점수/판단·E1/E2 불변).

## 안전
실제 개인정보 미사용. raw retrieved chunk·embedding·private data 를 repo/로그에 커밋하지 않는다. 점수·applyDecision 은 rule engine/server 소유(이 PoC 는 생성하지 않음).

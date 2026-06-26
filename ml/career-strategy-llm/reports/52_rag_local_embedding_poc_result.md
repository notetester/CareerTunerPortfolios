# R1b — RAG local embedding + vector search PoC 결과 (2026-06-26)

> R1(lexical) 위에 **로컬 의미검색 구조**를 얹어 검증. **backend 통합 아님** — Spring API·runtime prompt·서비스 설정·Ollama/3B LoRA 호출·OpenAI embedding 없음. **synthetic fixture 전용(실제 개인정보 미사용).**
> 핵심: 임베딩/vector search 를 붙여도 **scope filter 가 검색 전에 적용**돼 격리·fail-closed 유지, `retrievedContext` 는 점수/판단 미생성, **외부 API·다운로드 없이 로컬·오프라인 재현**.

## 1. #148 merge 확인 결과
PR #148(R1 offline retrieval PoC) **merged=True** (merge_commit `f6b78837`), dev에 `rag_poc/`(offline_retriever·build_retrieved_context·fixtures·tests) + `reports/51` 반영 확인. dev 최신에서 LEE-JEONG-GUCK 동기화 후 작업.

## 2. R1b 구현 범위
포함: `embedding_retriever.py`(local embedding + cosine, scope-before-search) · `build_vector_index.py`(임베딩 캐시→out/, gitignore) · `compare_lexical_semantic.py`(재현 비교) · semantic scope 회귀 테스트 · semantic 재현성/격리불변 테스트 · 이 보고서. 기존 R1 fixture 재사용.
미포함: backend API/기본모델 변경 · LangChain/Spring AI 통합 · runtime prompt 변경 · Ollama/3B LoRA 호출 · OpenAI embedding API · 실제 사용자 데이터 · production Vector DB 서버 · 생성 embedding/index 커밋.

## 3. 사용한 embedding / vector search 방식
- **embedding**: 환경에 numpy/sentence-transformers/sklearn **모두 부재** → 사용자 허용대로 **순수 파이썬 HashEmbedder(결정론 char n-gram + 토큰 해시, hashlib 기반)** 를 기본 백엔드로. `CT_RAG_ST_MODEL` 환경변수 지정 + 라이브러리 존재 시에만 sentence-transformers 사용(**자동 대형 다운로드 안 함**).
- **vector search**: **순수 파이썬 cosine**(임베딩 L2 정규화 → dot=cosine). FAISS/Chroma/numpy 미도입(의존성 0, fresh checkout 재현).
- HashEmbedder 는 의미 SOTA 가 아니라 **vector-search 구조·부분일치(SQL↔SQLD) 검증용 스탠드인**. 실제 의미검색은 §13 R2/환경 구비 시 `bge-m3`/`multilingual-e5` 로 동일 인터페이스 교체.

## 4. dependency 추가 여부
**추가 없음.** stdlib(hashlib/math/re/json)만 사용 — 새 pip 의존성·모델 다운로드·외부 API 호출 0. (sentence-transformers 경로는 *옵션*이며 기본 비활성.)

## 5. fixture / queries 구성
- chunk fixture: R1의 `sample_chunks.jsonl`(9 synthetic chunk, 5유형, user-a/b·app-a/b·global) **재사용**.
- 품질 비교 query(`compare_lexical_semantic.py`): "SQL 자격증과 데이터 모델링" · "Java 백엔드 프레임워크" · "REST API 서버 개발" · "데이터 파이프라인 경험". 비교는 동일 scope(user-a/app-a)에서.

## 6. scope filter 유지 방식 (검색 *전* 적용)
`EmbeddingRetriever.retrieve`는 **① in_scope(R1과 동일 규칙) 로 허용 chunk 를 먼저 거른 뒤 ② 허용 집합에만 cosine 랭킹**한다. 전체 인덱스 검색 후 후처리하지 않는다.
```text
request → metadata scope filter(in_scope) → allowed chunks only → embed query → cosine top-k → context builder
```
- in_scope 는 R1 `offline_retriever.py` 에서 **그대로 import**(단일 출처) — global / user_private(userId) / application_private(userId+applicationId) / 미상=False / id 누락=0건(fail-closed).

## 7. lexical vs semantic 비교 결과 (결정론·재현)
동일 scope(user-a/app-a) top-3:
| query | lexical(R1) top1 | semantic(R1b) top1 | 관찰 |
| --- | --- | --- | --- |
| SQL 자격증과 데이터 모델링 | cert-sqld(0.75) | cert-sqld(0.433) | 둘 다 정답 1위 |
| Java 백엔드 프레임워크 | job-a·springboot **동률 0.667** | **skill-springboot(0.517)** | semantic 이 동률을 분해 |
| REST API 서버 개발 | job-a·springboot 동률 0.5 | job-a(0.382) | 유사 |
| 데이터 파이프라인 경험 | cert-sqld·skill-spark **동률 0.333** | **skill-spark(0.511)** | semantic 이 'spark'(파이프라인)를 정확히 1위 |
- HashEmbedder 만으로도 **lexical 동률을 의미상 더 적절한 chunk 로 분해**하는 신호(파이프라인→spark, Java 프레임워크→springboot). 실제 의미 우월성 정량평가는 ST/e5 백엔드 + 더 큰 셋에서(R2).
- 출력은 `REPRODUCIBLE_JSON` 으로 재현 가능(결정론 embedding).

## 8. retrievedContext 예시 (semantic, q="SQL 자격증과 데이터 모델링", user-a/app-a)
```json
{"retrievedContext": [
  {"sourceType": "certification_catalog", "sourceId": "cert-sqld", "text": "SQLD는 SQL 기본 이해와 데이터 모델링 역량을 검증하는 국가공인 자격입니다."},
  {"sourceType": "user_profile_summary", "sourceId": "profile-user-a", "text": "지원자 A는 SQL, JPA, Git 보유. ..."}
]}
```
- `score`/`fitScore`/`applyDecision` **없음** — builder 불변(점수/판단은 rule engine/server 소유).

## 9. 테스트 결과 (stdlib unittest, 전부 통과)
- `test_embedding_retriever_scope.py` (6): semantic 에서도 ① user-a→user-b 불가 ② app-a→app-b 불가 ③ userId 없는 user_private 0건 ④ appId 없는 application_private 0건 ⑤ global 가능 ⑥ **scope filter 가 vector search 전에 적용**(top_k=100·min_score=-1 로 전부 통과시켜도 결과 ⊆ 허용집합, out-of-scope 0).
- `test_embedding_retriever_quality.py` (4): 재현성(동일 query→동일 랭킹) · self-cosine≈1 · **retrievedContext 에 score/fitScore/applyDecision 없음** · n-gram 부분일치(SQL↔SQLD) 양의 cosine.
- R1 회귀: `test_scope_filter.py` 7 · `test_context_builder.py` 3 **여전히 통과**.

## 10. 생성된 embedding/index 파일 커밋 여부
**커밋 안 함.** `rag_poc/out/vector_index.json`(생성 embedding)은 기존 `.gitignore`의 `out/` 규칙으로 추적 제외(`git check-ignore` 확인). PR diff 에 out/·embedding 파일 없음.

## 11. 개인정보 / 보안 검증
- **교차 사용자/지원 건 검색 0건**(테스트 ①②⑥), **fail-closed**(③④, id 누락·미상 visibility).
- **scope filter 가 vector search 전에** 적용 — 전체 인덱스 검색 후 후처리 아님(⑥ 구조 검증).
- 임베딩은 **로컬만**(외부 API 전송 0), 실제 개인정보 미사용(synthetic), 생성 embedding 미커밋.

## 12. 한계
- HashEmbedder 는 의미 SOTA 아님(동의어·문맥 약함) — 부분일치/구조 검증용. 실제 의미 품질은 ST(`bge-m3`/`multilingual-e5`) 환경에서 평가.
- fixture 소규모(9 chunk)·단일 tenant — 스케일/정밀도/재현성 부하 미검증.
- 환경에 임베딩 스택 부재 — 실제 모델 적용은 4090 등 GPU/라이브러리 구비 환경에서(모델 크기: bge-m3 ~2GB, multilingual-e5-small ~470MB; 필요 시 사전 보고).

## 13. 다음 단계
- **(환경 구비 시) ST 백엔드 실측**: `CT_RAG_ST_MODEL=intfloat/multilingual-e5-small` 등으로 동일 PoC 재실행(코드 변경 없이 백엔드만 교체) — 로컬·오프라인, 외부 API 없음.
- **R2**: `retrievedContext` 를 3B LoRA prompt 에 주입해 **A(LoRA) vs B(LoRA+RAG)** 오프라인 비교(reports/50 §10) — 별도 합의/구현 단계.
- **R3+**: Spring(Spring AI/LangChain4j) backend service 통합(점수/판단·E1/E2 불변).

## 자체 검증
- ✅ PR diff 에 backend 파일 없음(rag_poc/ + reports/52 + README).
- ✅ fixture synthetic(실제 개인정보 없음).
- ✅ embedding/vector index/out 파일 미커밋(out/ gitignore).
- ✅ OpenAI embedding API 호출 없음(로컬 hash/ST only).
- ✅ scope filter 가 vector search 전에 적용(테스트 ⑥).
- ✅ retrievedContext 에 score/fitScore/applyDecision 없음.
- ✅ fresh checkout 에서 fixture 누락 없이 테스트 실행(fixture 는 #148 로 dev 에 tracked).

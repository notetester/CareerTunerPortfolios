# R1 — RAG offline retrieval PoC 결과 (2026-06-26)

> reports/50 RAG 설계의 **오프라인 검색 PoC** 구현·검증. **backend 통합 아님** — Spring API·runtime prompt·Vector DB 서버·실제 embedding·Ollama 호출·실서비스 설정 변경 없음. **synthetic fixture 전용(실제 개인정보 미사용).**
> 핵심 결론: chunk metadata·scope filter(global/user_private/application_private)가 코드로 동작하고 **fail-closed** 이며, `retrievedContext[]` 가 점수/판단을 만들지 않고 조립됨.

## 1. #147 merge 확인 결과
PR #147(reports/50 RAG 설계) **merged=True** (merge_commit `9080d05`), dev에 `reports/50_rag_design_plan.md` 반영 확인. dev 최신에서 LEE-JEONG-GUCK 동기화 후 작업.

## 2. 구현 범위
포함: 오프라인 retriever·retrievedContext builder·synthetic fixture·scope/filter 보안 테스트·드라이버·이 보고서.
미포함: Spring backend API 변경 · LangChain/Spring AI runtime 통합 · Vector DB 서버 설치 · 실제 embedding 파일 · 실제 사용자 데이터 · Ollama/3B LoRA 호출 · RAG 서비스 기본 경로 연결.

## 3. fixture 데이터 구성 (`rag_poc/fixtures/sample_chunks.jsonl`, 9 chunk)
5 유형 × 스코프 커버:
| chunkId | sourceType | visibility | userId / applicationId |
| --- | --- | --- | --- |
| chunk-job-a-001 | job_posting | application_private | user-a / app-a |
| chunk-research-a-001 | company_research_summary | application_private | user-a / app-a |
| chunk-profile-a-001 | user_profile_summary | user_private | user-a / — |
| chunk-job-b-001 | job_posting | application_private | user-b / app-b |
| chunk-profile-b-001 | user_profile_summary | user_private | user-b / — |
| chunk-cert-sqld, chunk-cert-info | certification_catalog | global | — |
| chunk-skill-springboot, chunk-skill-spark | skill_catalog | global | — |
- reports/50 metadata 스키마 반영: tenantId/userId/applicationId/sourceType/sourceId/visibility/domainGroup/createdAt/expiresAt/text.
- **실제 개인정보 없음** — 모두 가상 지원자(user-a/b)·가상 지원 건(app-a/b).

## 4. retriever 방식
- **결정론 token-overlap(lexical) scorer** — 운영 복잡도 최소화(대형 embedding/벡터DB 없이). 한·영·숫자 토큰 집합의 query-coverage 비율.
- 이 R1의 초점은 검색 *품질*이 아니라 **scope 격리·fail-closed** 검증.
- 후속 **R1b**: 로컬 `bge-m3`/`multilingual-e5` + FAISS/Chroma 의미검색으로 확장(외부 API·개인정보 전송 없음). 동일 scope filter·fixture 재사용.

## 5. scope filter 규칙 (검색 *전* 적용, fail-closed)
- `global` → 모든 요청 검색 가능
- `user_private` → 요청 userId == chunk userId 일 때만 (요청 userId 없으면 0건)
- `application_private` → 요청 userId·applicationId 둘 다 chunk와 일치할 때만 (둘 중 없으면 0건)
- 미상 visibility → **fail-closed(False)**

## 6. 테스트 결과
`rag_poc/tests/` — **stdlib unittest, 전부 통과.**
- `test_scope_filter.py` (7): ① user-a 본인 검색 가능 ② user-a→user-b 불가 ③ app-a→app-b 불가 ④ userId 없는 user_private 0건 ⑤ applicationId 없는 application_private 0건 ⑥ global 검색 가능 ⑦ `in_scope` fail-closed 직접검증(id 누락·타 사용자·타 지원건·미상 visibility 전부 False).
- `test_context_builder.py` (3): ⑦ sourceType/sourceId/text 유지 ⑧ **fitScore/applyDecision/score 미생성**(키 `{sourceType,sourceId,text}`만) + max_items 제한.
- 드라이버 `run_rag_poc.py` 실측: q-no-user → global 1건만, q-user-a-noapp → application_private 제외, q-app-a/app-b → 본인 스코프만(교차 사용자/지원건 0건).

## 7. retrievedContext 예시 (q-app-a, user-a/app-a)
```json
{
  "retrievedContext": [
    {"sourceType": "job_posting", "sourceId": "job-001", "text": "백엔드 개발자는 Java, Spring Boot, REST API, SQL, JPA 역량을 요구합니다. ..."},
    {"sourceType": "skill_catalog", "sourceId": "skill-springboot", "text": "Spring Boot는 Java 기반 백엔드 프레임워크로 ..."},
    {"sourceType": "user_profile_summary", "sourceId": "profile-user-a", "text": "지원자 A는 SQL, JPA, Git 보유. ..."},
    {"sourceType": "certification_catalog", "sourceId": "cert-sqld", "text": "SQLD는 SQL 기본 이해와 ..."}
  ]
}
```
- `fitScore`/`applyDecision`/`score` **없음** — 설명 근거만. 점수·판단은 rule engine/server 소유(불변).

## 8. 개인정보/보안 검증 결과
- **교차 사용자/지원 건 검색 0건** 확인(테스트 ②③, 드라이버).
- **fail-closed** 확인: 필요한 userId/applicationId 누락 시 해당 스코프 0건(테스트 ④⑤⑦).
- fixture는 synthetic — 실제 이력서/지원 건 미사용. embedding/벡터DB 파일 미생성·미커밋. retrievedContext에 점수/판단 키 없음.

## 9. 한계
- lexical scorer는 의미검색이 아님(동의어·패러프레이즈 약함) → R1b embedding으로 보강 예정.
- fixture 소규모(9 chunk)·단일 tenant — 스케일/멀티테넌트 부하는 미검증.
- 실제 backend 권한·인증 연동(요청 컨텍스트의 userId/applicationId 신뢰성)은 R3에서 검증.

## 10. 다음 단계
- **R1b**: 로컬 embedding(bge-m3/multilingual-e5) + FAISS/Chroma 의미검색, 동일 scope filter·fixture 유지(외부 전송 없음).
- **R2**: `retrievedContext`를 3B LoRA prompt에 주입해 A(LoRA) vs B(LoRA+RAG) 오프라인 비교(reports/50 §10) — 별도 합의/구현 단계.

## 자체 검증
- ✅ PR diff에 backend 파일 없음(rag_poc/ + reports/51만).
- ✅ fixture에 실제 개인정보 없음(synthetic).
- ✅ embedding/vector DB 파일 미커밋.
- ✅ retrievedContext에 점수/applyDecision 생성 로직 없음(가드 + 테스트).
- ✅ scope filter 테스트가 fail-closed 검증(④⑤⑦).

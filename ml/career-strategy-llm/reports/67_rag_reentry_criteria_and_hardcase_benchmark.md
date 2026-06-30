# RAG 재도입 기준과 hard-case benchmark 설계

## 1. 왜 RAG runtime 자동 통합이 보류됐는지
초기 판단은 reports/49 이후 "3B LoRA 유지 + RAG 우선"이었다. 그러나 R2b~R2f 실측에서 production prompt 에 retrievedContext 를 자동 주입하는 방식은 기대만큼 안전하지 않았다.

- R2b: hard-case A/B 에서 RAG 는 success/CJK 를 일부 개선했지만 rag_improvement 3 = rag_regression 3 으로 net wash 였다.
- R2c/R2d: scoped prompt guard 와 evidence bucket framing 도 3B 모델의 grounding conflation 을 충분히 막지 못했다.
- R2f: review/reject gate 는 실제 출력 위반을 robust 하게 표면화했지만, rewrite 는 의미손실과 malformed 문제가 남았다.

따라서 현재 production path 는 RAG runtime 자동 연결이 아니라 no-context 3B LoRA + E1 hard guard + R3 review-first evidence gate 로 둔다.

## 2. R2b~R2f에서 확인한 핵심 실패 모드
- retrievedContext 가 직무 요구사항/카탈로그 사실을 사용자 보유 역량처럼 혼동할 위험이 있다.
- prompt guard 만으로 3B 모델의 grounding conflation 을 충분히 막지 못했다.
- evidence bucket 으로 분리해도 모델 단계에서는 no-context baseline 을 명확히 이기지 못했다.
- rewrite 는 unsupported possession 표현을 제거할 수 있지만, 문장 단위 치환으로 정당한 정보까지 삭제하거나 malformed 문장을 만들 수 있다.
- raw output capture 없이는 실제 사용자 노출 텍스트에서 어떤 claim 이 발생했는지 검증이 불완전하다.

## 3. RAG가 다시 필요할 수 있는 이유
RAG 자체가 폐기된 것은 아니다. 모델이 모르는 자격증, 회사/공고 사실, 기술 카탈로그 구분을 설명 텍스트에 안전하게 반영하려면 여전히 retrieval 이 필요할 수 있다.

- 7B base 전환은 reports/49 기준으로 3B LoRA 를 대체할 근거가 부족했다.
- MSSQL/SQL 같은 catalog disambiguation 은 모델 크기보다 근거 제공과 source attribution 의 문제다.
- 비IT 직군의 정밀 자격증/역량 catalog 는 Phase 2 이후 grounding 소스로 다시 필요하다.
- 단, RAG 는 점수/지원판단이 아니라 설명 근거 보강 후보로만 다룬다.

## 4. 재도입 전 반드시 만족해야 할 조건
- retrieved evidence bucket 이 `userEvidence` / `jobRequirements` / `catalogFacts` / `companyContext` 로 물리 또는 구조적으로 분리되어야 한다.
- 모델 출력 후 R3 gate 를 반드시 통과해야 한다.
- unsupported possession claim 비율이 LoRA-only 대비 감소해야 한다.
- `fitScore` / `applyDecision` 변동은 별도로 관찰하되, RAG 성공 기준으로 단독 사용하지 않는다.
- 개인정보, 사용자 원본, 공고 요구, 카탈로그 사실의 source attribution 이 분리되어야 한다.
- raw retrieved chunk, raw output, 개인정보 포함 artifact 는 main repo 에 커밋하지 않는다.

## 5. hard-case benchmark 설계
비교군은 최소 A/B 로 둔다.

| Variant | 구성 | 목적 |
| --- | --- | --- |
| A | 3B LoRA only + E1/R3 평가 | 현재 production baseline |
| B | 3B LoRA + structured RAG buckets + E1/R3 평가 | RAG 재도입 후보 |

필수 hard-case 후보:

- JavaScript vs Java
- Apache Spark vs Spark
- SQL vs MSSQL/MySQL/PostgreSQL
- React vs React Native
- Spring vs Spring Boot
- Next.js vs JavaScript
- 직무 요구 기술을 사용자 보유 기술처럼 말하는 케이스
- 회사/공고 정보와 사용자 프로필 정보를 혼동하는 케이스
- `missingSkills` 를 `matchedSkills` 처럼 말하는 케이스

각 케이스는 같은 base input 에서 retrieved evidence bucket 유무만 달라야 한다. retrieved context 에는 score, fitScore, applyDecision 을 넣지 않는다.

## 6. 성공 기준
- unsupported possession claim 감소.
- R3 `REVIEW_REQUIRED` 비율 감소 또는 severity 개선.
- semantic judge 기준 hallucinated skill 감소.
- normalized hallucination 감소.
- raw hallucination 감소.
- 기존 3B LoRA 대비 사용자 보유 역량 단정 오류 감소.
- contract success, JSON parse, CJK leak, latency, 개인정보 scope isolation 이 기존 production 기준을 깨지 않는다.

## 7. 실패 기준
- unsupported possession claim 증가.
- retrieved job requirement 를 user-owned evidence 로 혼동.
- gate 통과율만 높아지고 실제 품질은 낮아짐.
- alias/normalizer 에 의존해 위험이 가려짐.
- 응답 길이만 늘고 actionable recommendation 이 나빠짐.
- source attribution 이 흐려져 운영자가 reason 을 재현할 수 없음.

## 8. RAG 재도입 시 금지할 것
- RAG runtime 을 바로 production prompt 에 연결하지 말 것.
- gate 없이 RAG 출력만으로 사용자에게 노출하지 말 것.
- rewrite 결과를 gate 재검증 없이 노출하지 말 것.
- fitScore 상승만으로 RAG 성공으로 판단하지 말 것.
- retrievedContext 를 하나의 긴 문자열로 합쳐 모델에게 던지지 말 것.
- sourceType/sourceId 없이 raw chunk 텍스트만 넘기지 말 것.
- user-private chunk 를 외부 embedding API 로 보내지 말 것.

## 9. 다음 작업 후보
- R3 gate reason 샘플에서 반복 false-positive/false-negative 후보를 수집한다.
- reports/54 hard-case fixture 를 R3 이후 기준으로 재구성한다.
- structured evidence bucket builder 를 offline benchmark 전용으로 먼저 만든다.
- semantic judge packet 에 R3 reason type, severity, source attribution 평가 필드를 추가한다.
- RAG 재평가는 production 연결 PR 이 아니라 offline benchmark PR 로 시작한다.

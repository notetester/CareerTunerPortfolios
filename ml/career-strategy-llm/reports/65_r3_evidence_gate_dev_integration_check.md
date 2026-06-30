# R3 evidence gate dev 통합 검토

## 1. dev 최신 기준 포함 PR 목록

검토 기준 HEAD:

```text
47905f61 Merge pull request #184 from notetester/LEE-JEONG-GUCK
```

dev 최신에는 R3 evidence gate 계열과 후속 안정화 PR이 다음 순서로 포함되어 있다.

- #174 R3 review-first evidence gate 적용
- #175 userEvidence 기준 정정
- #180 skill alias 정규화 추가
- #182 skill alias mention-boundary 보강
- #183 ProfileAiService primary bean 충돌 수정
- #184 Spring provider 계약 회귀 방지

## 2. backend 전체 테스트 결과

실행 명령:

```bash
cd backend
./gradlew test
```

결과:

```text
BUILD SUCCESSFUL
```

첫 실행 기준 Gradle task 는 `UP-TO-DATE` 로 판정되었고, 이후 targeted test 를 별도 실행해 R3 관련 경로를 다시 확인했다.

## 3. evidence gate targeted test 결과

실행 명령:

```bash
./gradlew test --tests com.careertuner.fitanalysis.service.EvidenceGateServiceTest
./gradlew test --tests com.careertuner.fitanalysis.service.FitAnalysisServiceImplTest
./gradlew test --tests com.careertuner.fitanalysis.ai.OssFitAnalysisAiServiceTest
```

결과:

- `EvidenceGateServiceTest`: 성공
- `FitAnalysisServiceImplTest`: 성공
- `OssFitAnalysisAiServiceTest`: 성공

첨부 지시문에는 `com.careertuner.fitanalysis.service.OssFitAnalysisAiServiceTest` 로 적혀 있었지만, 현재 실제 테스트 패키지는 `com.careertuner.fitanalysis.ai.OssFitAnalysisAiServiceTest` 이므로 실제 패키지명 기준으로 실행했다.

## 4. Spring provider convention test 결과

실행 명령:

```bash
./gradlew test --tests com.careertuner.SpringBeanConventionTests
```

결과:

```text
BUILD SUCCESSFUL
```

`ProfileAiService` primary 충돌은 #183 기준으로 해소된 상태이며, #184 의 convention test 도 최신 dev에서 통과한다.

## 5. 프런트 typecheck 실행 여부

admin fit-analysis 상세 표시와 타입을 확인 대상에 포함했으므로 최신 dev 기준으로 typecheck 를 실행했다.

실행 명령:

```bash
cd frontend
npm run typecheck
```

결과:

```text
tsc --noEmit
성공
```

## 6. 확인한 핵심 불변식

- `userEvidence` 는 여전히 `profileSkills + profileCertificates` 만 기준으로 한다.
- `ai.matchedSkills` 는 user evidence 가 아니라 `derivedMatchedSkills` evidence bucket 으로만 취급한다.
- evidence gate 는 `fitScore`, `applyDecision`, `matchedSkills`, `missingSkills` 를 변경하지 않는다.
- 구조 계약 누락 또는 점수 범위 위반은 `REJECTED` 로 분류한다.
- 근거 없는 matched skill 또는 보유 단정 claim 은 `REVIEW_REQUIRED` 로 분류한다.
- 이유가 없으면 `PASSED` 로 분류한다.
- `SkillAliasNormalizer` 는 explicit alias map 과 ASCII boundary 기반 mention 만 사용하며 substring/fuzzy matching 을 추가하지 않는다.
- blocked compound phrase 정책은 `react -> react native`, `spring -> spring boot`, `java -> javascript`, `sql -> mysql/mssql/postgresql/postgres` 를 차단한다.
- dotted short alias suffix 정책으로 `Next.js` 의 `.js` 를 standalone `JS` mention 으로 보지 않는다.
- RAG runtime 자동 주입은 계속 꺼져 있다.
- rewrite 자동 노출도 계속 꺼져 있다.
- E1 grounding guard 코드는 변경하지 않았다.

## 7. 확인한 반례 목록

첨부 지시문의 10개 반례는 현재 테스트에 있거나 동등 테스트로 보장된다.

| # | 반례 | 현재 보장 |
| --- | --- | --- |
| 1 | `profileSkills=["Java"]`, `requiredSkills=["Spark"]`, `ai.matchedSkills=["Spark"]` -> `REVIEW_REQUIRED` | `aiMatchedSkillWithoutUserEvidenceIsReviewCritical` |
| 2 | `profileSkills=["Apache Spark"]`, `requiredSkills=["Spark"]`, `ai.matchedSkills=["Spark"]` -> `PASSED` | `apacheSparkAliasPassesMatchedAudit` |
| 3 | `profileSkills=["JavaScript"]`, `requiredSkills=["Java"]`, `ai.matchedSkills=["Java"]` -> `REVIEW_REQUIRED` | `javascriptDoesNotSatisfyJava` |
| 4 | `profileSkills=["MSSQL"]`, `requiredSkills=["SQL"]`, `ai.matchedSkills=["SQL"]` -> `REVIEW_REQUIRED` | `genericSqlDoesNotPassWithMssql` |
| 5 | `text="Next.js 경험을 보유"`, `requiredSkills=["JavaScript"]` -> `PASSED` | `nextJsDoesNotSatisfyJavascriptMention` |
| 6 | `text="JS 경험을 보유"`, `requiredSkills=["JavaScript"]` -> `REVIEW_REQUIRED` | `standaloneJsSatisfiesJavascriptMention` |
| 7 | `text="React Native 경험을 보유"`, `requiredSkills=["React"]` -> `PASSED` | `reactNativeDoesNotSatisfyReactMention` |
| 8 | `text="React 경험을 보유"`, `requiredSkills=["React"]` -> `REVIEW_REQUIRED` | `standaloneReactSatisfiesReactMention` |
| 9 | `text="Spring Boot 경험을 보유"`, `requiredSkills=["Spring"]` -> `PASSED` | `springBootDoesNotSatisfySpringMention` |
| 10 | `text="SQL 경험을 보유"`, `requiredSkills=["SQL"]` -> `REVIEW_REQUIRED` | `standaloneSqlSatisfiesGenericSqlMention` |

## 8. 발견한 문제

코드 보정이 필요한 결함은 발견하지 못했다.

주의점은 하나 있다. 지시문에 적힌 OSS targeted test 패키지명이 현재 소스와 달랐다. 현재 테스트 클래스는 `backend/src/test/java/com/careertuner/fitanalysis/ai/OssFitAnalysisAiServiceTest.java` 에 있으며, 실제 실행도 `com.careertuner.fitanalysis.ai.OssFitAnalysisAiServiceTest` 로 했다.

## 9. 수정한 내용

런타임 코드, 테스트 코드는 변경하지 않았다.

이번 PR에는 통합 검토 결과 문서만 추가한다.

```text
ml/career-strategy-llm/reports/65_r3_evidence_gate_dev_integration_check.md
```

## 10. 남은 휴먼 테스트 포인트

- R3 이전 분석 row 에서 `gateStatus = null` 인 경우 관리자 상세 화면이 gate 블록 없이 정상 표시되는지 실제 DB 데이터로 확인한다.
- `reviewRequiredOnly=true` 필터가 운영 데이터 규모에서 기대 성능을 내는지 확인한다.
- REVIEW_REQUIRED 분석의 `gateReasons` 문구가 운영자가 이해하기에 충분한지 화면에서 확인한다.
- PASSED/REVIEW_REQUIRED/REJECTED 가 섞인 실제 분석 목록에서 뱃지 색과 필터 UX가 혼동되지 않는지 확인한다.
- OSS provider 사용 환경에서 E1 hard guard 이후 R3 soft review 가 중복 또는 과도하게 느껴지지 않는지 샘플 분석으로 확인한다.

# R3 evidence gate — skill alias normalizer 후속 보완 (#175 후속, C 영역)

> PR #175 `fix: evidence gate user evidence 기준 정정` 이후 남긴 한계인 skill 표면형 불일치 false-positive 를 줄이기 위한 보수 보완.
> 되돌리기가 아니라 C `fitanalysis` evidence gate 내부 비교용 canonical key 를 추가한 작업이다.

## 1. #175 이후 남은 문제
#175는 `userEvidence = profileSkills + profileCertificates` 원칙을 바로잡아 AI `matchedSkills` 순환 신뢰를 끊었다.
다만 비교가 lower-case exact matching 이라 다음처럼 사용자가 실제 근거를 가진 경우도 과검토될 수 있었다.

| profileSkills | requiredSkills | ai.matchedSkills | 기존 결과 |
| --- | --- | --- | --- |
| `Apache Spark` | `Spark` | `Spark` | `REVIEW_REQUIRED` 가능 |

이는 false-negative 를 피하기 위한 보수 정책의 의도된 한계였지만, 명확한 별칭까지 과검토하는 UX 부담이 남았다.

## 2. exact matching의 장점과 한계
- 장점: `JavaScript`가 `Java`를, `MSSQL`이 `SQL`을 보유한 것처럼 통과하는 conflation false-negative 를 막는다.
- 한계: `Apache Spark`/`Spark`, `Postgres`/`PostgreSQL`, `K8s`/`Kubernetes` 같은 명확한 표면형 차이도 통과하지 못한다.

## 3. unsafe substring matching을 채택하지 않은 이유
양방향 `contains`, `startsWith`/`endsWith`, fuzzy matching 은 이번에도 사용하지 않았다.

대표 위험:

| 잘못된 일반화 | 위험 |
| --- | --- |
| `JavaScript` -> `Java` | 프런트 스크립트 경험이 Java 서버 경험으로 통과 |
| `MSSQL` -> `SQL` | 특정 DB 제품 경험이 일반 SQL 요구를 자동 충족 |
| `Spring` -> `Spring Boot` | 프레임워크 하위 생태계를 무단 승격 |
| `React` -> `React Native` | 웹 React 경험이 모바일 React Native 경험으로 승격 |

review-first gate 는 false-negative 보다 false-positive 를 선호해야 하므로, 안전하지 않은 자동 완화는 넣지 않는다.

## 4. curated alias map 방식
`backend/src/main/java/com/careertuner/fitanalysis/service/SkillAliasNormalizer.java` 를 추가했다.

정규화 단계:

1. `null`/blank 제거
2. trim
3. NFKC normalize
4. lowercase
5. 공백, 하이픈, 일부 구두점 표준화
6. curated alias map 정확 조회
7. alias 가 없으면 정규화된 원문 반환

원본 profile/API/DB 문자열은 바꾸지 않고, `EvidenceGateService` 내부 비교에만 canonical key 를 쓴다.

## 5. 1차 alias 목록
| alias | canonical |
| --- | --- |
| `apache spark`, `spark` | `spark` |
| `postgres`, `postgresql` | `postgresql` |
| `k8s`, `kubernetes` | `kubernetes` |
| `js`, `javascript` | `javascript` |
| `ts`, `typescript` | `typescript` |
| `node.js`, `nodejs` | `nodejs` |
| `spring boot`, `springboot` | `spring boot` |
| `react.js`, `reactjs`, `react` | `react` |
| `vue.js`, `vuejs`, `vue` | `vue` |

## 6. 명시적으로 alias 처리하지 않은 위험 케이스
- `java != javascript`
- `sql != mysql`
- `sql != mssql`
- `sql != postgresql`
- `sql != oracle`
- `spring != spring boot`
- `node != nodejs`
- `react native != react`

이들은 substring/fuzzy 로 통합하면 검토 누락으로 이어질 수 있어 테스트로 고정했다.

## 7. JavaScript != Java 테스트
추가 테스트 `javascriptDoesNotSatisfyJava`:

```text
profileSkills = ["JavaScript"]
requiredSkills = ["Java"]
ai.matchedSkills = ["Java"]
ai.strategy = "Java 역량을 보유하고 있습니다."
```

기대 및 결과: `REVIEW_REQUIRED`.

## 8. SQL != MSSQL/MySQL/PostgreSQL 일반화 테스트
추가 테스트 `genericSqlDoesNotPassWithMssql`:

```text
profileSkills = ["MSSQL"]
requiredSkills = ["SQL"]
ai.matchedSkills = ["SQL"]
ai.strategy = "SQL 역량을 보유하고 있습니다."
```

기대 및 결과: `REVIEW_REQUIRED`.

`PostgreSQL`/`Postgres` 는 명시 alias 로만 통합하고, `SQL` 일반 요구와는 통합하지 않는다.

## 9. Spring != Spring Boot 테스트
추가 테스트 `springDoesNotSatisfySpringBoot`:

```text
profileSkills = ["Spring"]
requiredSkills = ["Spring Boot"]
ai.matchedSkills = ["Spring Boot"]
```

기대 및 결과: `REVIEW_REQUIRED`.

## 10. React != React Native 테스트
추가 테스트 `reactDoesNotSatisfyReactNative`:

```text
profileSkills = ["React"]
requiredSkills = ["React Native"]
ai.matchedSkills = ["React Native"]
```

기대 및 결과: `REVIEW_REQUIRED`.

## 11. EvidenceGateService 반영 방식
- `userEvidenceLower`/`requiredLower` 비교를 `userEvidenceKeys`/`requiredKeys` canonical set 비교로 변경.
- `derivedMatchedSkills` audit 도 canonical key 로 비교하되, reason 의 `claim` 은 기존 AI 원문 문자열을 유지.
- text-claim audit 은 문장 내 skill 언급을 alias map 기반 canonical mention 으로 확인한다.
- 문장 검색은 ASCII letter/digit 경계를 확인해 `JavaScript` 안의 `Java`, `MSSQL` 안의 `SQL` 같은 내부 substring 을 skill 언급으로 보지 않는다.

## 12. DB 변경 여부와 이유
DB 변경 없음.

이번 보완은 C evidence gate 내부 비교 정책이며, 관리자 alias CRUD 나 운영 중 동적 taxonomy 가 아직 요구되지 않는다.
따라서 C 전용 코드 상수 map 이 가장 좁고 되돌리기 쉬운 구현이다.

## 13. 테스트 결과
- `cd backend && .\gradlew.bat test --tests com.careertuner.fitanalysis.service.EvidenceGateServiceTest`: `BUILD SUCCESSFUL`
- `cd backend && .\gradlew.bat test`: `BUILD SUCCESSFUL`
- frontend 변경 없음: `npm run typecheck` 는 실행하지 않음

## 14. 유지한 불변식
- `ApiResponse` record 변경 없음
- 기존 E1 guard 변경 없음
- `fitScore`/`applyDecision`/`matchedSkills`/`missingSkills` 변경 없음
- RAG runtime 자동 통합 없음
- rewrite 자동 노출 없음
- 기본 모델 변경 없음
- naive substring/fuzzy matching 없음
- alias map 에 등록된 명시 별칭만 canonicalize
- 원본 데이터 문자열을 canonical 값으로 덮어쓰지 않음

## 15. 후속 과제
- 팀 차원의 skill taxonomy / alias registry 설계
- alias 추가 기준과 리뷰 책임자 정의
- alias 변경 이력/audit 이 필요해지는 시점에 C 전용 additive alias table 검토
- 실제 REVIEW_REQUIRED 분포를 모니터링해 alias 후보를 데이터 기반으로 추가

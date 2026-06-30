# R3 skill alias mention-boundary 보강

## 1. #180 이후 남은 mention-boundary 문제

PR #180 에서 curated alias canonical key 비교가 들어갔지만, 사용자 노출 텍스트의 mention 탐지에는 다음 false-positive 여지가 남아 있었다.

- `Next.js` 의 `.js` suffix 가 `JavaScript` 의 짧은 alias `JS` 로 잡힐 수 있음
- `React Native` 가 일반 `React` 보유 단정으로 잡힐 수 있음
- `Spring Boot` 가 일반 `Spring` 보유 단정으로 잡힐 수 있음
- `PostgreSQL`/`MSSQL`/`MySQL` 같은 구체 DB명이 generic `SQL` 보유 단정으로 잡힐 수 있음

## 2. 짧은 alias(js/ts)의 위험

`js`, `ts` 처럼 2글자 alias 는 ASCII boundary 만으로는 충분하지 않다. 특히 `Next.js`, `Nuxt.js` 처럼 dot 뒤에 붙는 suffix 는 앞 문자가 ASCII letter/digit 이 아니므로 기존 boundary 규칙을 통과할 수 있다.

이번 보강에서는 짧은 alias 가 dot suffix 형태로 등장한 경우 canonical mention 으로 인정하지 않도록 했다. 이 정책은 `JS 경험을 보유` 같은 standalone alias 는 유지하면서, `Next.js 경험` 같은 framework/runtime 이름의 suffix 를 차단한다.

## 3. 복합 기술명 non-alias 문제

복합 기술명은 포함된 단어가 독립 기술과 이름을 공유해도 같은 보유 근거로 보면 안 된다.

- `React Native` != `React`
- `Spring Boot` != `Spring`
- `JavaScript` != `Java`
- `PostgreSQL`/`MSSQL`/`MySQL` != generic `SQL`

이 케이스들은 alias canonicalization 이 아니라 mention policy 의 문제이므로, canonical key 비교 구조는 유지하고 mention detection 에서만 차단했다.

## 4. 선택한 구현 방식

`SkillAliasNormalizer` 에 blocked compound phrase map 을 추가했다. `containsCanonicalMention(text, canonicalKey)` 는 alias 후보가 boundary 를 만족하더라도, 해당 canonical key 에 대해 차단된 복합 문구 내부에 포함되면 mention 으로 인정하지 않는다.

또한 `EvidenceGateService` 의 사용자 노출 텍스트 분리에서 dot 뒤가 ASCII letter/digit 인 dotted 기술명은 쪼개지 않도록 조정했다. `Next.js` 를 `Next` / `js 경험...` 으로 잘라버리면 normalizer 가 dotted token 문맥을 복구할 수 없기 때문에, dotted 기술명은 원문 token 형태를 보존한 뒤 normalizer policy 로 판단한다. 일반 문장 마침표는 계속 문장 경계로 취급한다.

## 5. blocked compound phrase 또는 mention policy

추가한 blocked phrase 정책은 다음과 같다.

```text
java   -> javascript
node   -> node.js, nodejs
react  -> react native
spring -> spring boot
sql    -> mysql, mssql, postgresql, postgres
```

짧은 alias 정책:

```text
alias length <= 2 이고 바로 앞 문자가 '.' 이면 canonical mention 으로 인정하지 않음
```

## 6. 추가한 테스트 목록

- `nextJsDoesNotSatisfyJavascriptMention`
- `standaloneJsSatisfiesJavascriptMention`
- `reactNativeDoesNotSatisfyReactMention`
- `standaloneReactSatisfiesReactMention`
- `springBootDoesNotSatisfySpringMention`
- `standaloneSpringSatisfiesSpringMention`
- `postgresqlDoesNotSatisfyGenericSqlMention`
- `standaloneSqlSatisfiesGenericSqlMention`

## 7. 기존 alias 통과 테스트 유지 여부

기존 #175/#180 테스트는 유지했다.

- Apache Spark/Spark alias 통과
- Postgres/PostgreSQL alias 통과
- K8s/Kubernetes alias 통과
- JavaScript 가 Java 를 만족하지 않는 테스트
- MSSQL 이 generic SQL 을 만족하지 않는 테스트
- Spring 이 Spring Boot 를 만족하지 않는 테스트
- React 가 React Native 를 만족하지 않는 테스트

## 8. 테스트 결과

실행한 명령:

```bash
cd backend
./gradlew test --tests com.careertuner.fitanalysis.service.EvidenceGateServiceTest
./gradlew test
```

결과:

- `./gradlew test --tests com.careertuner.fitanalysis.service.EvidenceGateServiceTest`: 성공
- `./gradlew test`: 실패

전체 테스트 실패 원인:

- `CareerTunerApplicationTests` 4개가 앱 컨텍스트 로딩 단계에서 실패
- 원인 bean: `com.careertuner.profile.ai.ProfileAiService`
- 실패 메시지: `more than one 'primary' bean found among candidates: [anthropicProfileAiService, fallbackProfileAiService, fineTunedProfileAiService, openAiProfileAiService, ruleBasedProfileAiService]`

이번 변경은 `backend/src/main/java/com/careertuner/fitanalysis/service/**` 와 해당 테스트에 한정되어 있으며, 실패 지점인 `profile` AI service bean 구성은 수정하지 않았다.

## 9. 유지한 불변식

- `fitScore` 변경 없음
- `applyDecision` 변경 없음
- `matchedSkills` 변경 없음
- `missingSkills` 변경 없음
- `userEvidence = profileSkills + profileCertificates` 유지
- `ai.matchedSkills` 는 derived bucket 으로만 취급
- substring/fuzzy matching 추가 없음
- RAG runtime 자동 주입 없음
- rewrite 자동 노출 없음
- E1 guard 변경 없음
- DB/schema 변경 없음

## 10. 남은 후속 과제

- generic 기술명과 vendor/framework 명이 충돌하는 케이스가 늘어나면 blocked phrase map 을 curated policy 로 계속 확장해야 한다.
- 전체 backend test 실패는 C fitanalysis 범위 밖인 `profile` AI service primary bean 구성 문제라 별도 PR에서 정리하는 것이 안전하다.

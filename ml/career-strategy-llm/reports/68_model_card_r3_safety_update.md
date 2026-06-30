# model-card R3 safety 반영 보고

## 1. 작업 목적
C Career Strategy AI 의 최신 dev 상태를 `model-card.md` 에 반영했다. 핵심은 production path 를 모델 단독 출력 품질이 아니라 `3B LoRA / provider output -> E1 grounding hard guard -> R3 review-first evidence gate -> admin review state / safety response` 로 설명하는 것이다.

## 2. model-card.md 변경 요약
- 검증 방식에 E1 grounding hard guard 와 R3 review-first evidence gate 를 추가했다.
- fallback 표기를 실제 fitanalysis 경로인 `FallbackFitAnalysisAiService` 기준으로 정정했다.
- `Production safety layer`, `Current deployment decision`, `Known limitations after R3`, `Re-evaluation triggers` 섹션을 추가했다.
- 3B LoRA 유지, 7B base 전환 보류, RAG runtime 자동 통합 보류, rewrite 자동 노출 보류를 현재 배포 판단으로 정리했다.

## 3. R3 production safety 반영 내용
- 모델 출력은 그대로 신뢰하지 않는다.
- E1 guard 는 명백한 grounding violation 을 hard guard/retry/fallback 성격으로 다룬다.
- R3 gate 는 사용자 원본 근거와 AI 파생 결과를 분리해 `REVIEW_REQUIRED` 를 표시한다.
- R3 gate 는 `fitScore`, `applyDecision`, `matchedSkills`, `missingSkills` 를 변경하지 않는다.
- `userEvidence` 는 `profileSkills + profileCertificates` 기준이고, `matchedSkills` 는 derived evidence 로 취급한다.
- alias normalizer 는 curated alias map 기반이며 substring/fuzzy matching 은 사용하지 않는다.
- mention-boundary 보강으로 `Next.js`, `React Native`, `Spring Boot`, `PostgreSQL` 류 false-positive 를 줄인다.

## 4. RAG 재도입 기준 문서 경로
- [reports/67](67_rag_reentry_criteria_and_hardcase_benchmark.md)

해당 문서는 RAG 를 감으로 다시 붙이지 않도록 재도입 조건, hard-case benchmark 후보, 성공/실패 기준, 금지 사항을 정리한다.

## 5. AI_ROADMAP_CHECKLIST.md 보정 내용
- 기준 PR 범위에 PR #187 을 추가했다.
- R3 자동 검증 보강 상태를 reports/66 과 연결했다.
- model-card R3 safety 반영을 완료 상태로 표시했다.
- RAG 재도입 조건 문서를 reports/67 로 연결했다.
- next candidates 를 "문서화"에서 "benchmark 실행/운영 데이터 반영" 중심으로 갱신했다.

## 6. 런타임 코드 변경 여부
없음. backend/frontend runtime code, production prompt, model 설정, RAG runtime, rewrite 자동 노출, `EvidenceGateService`, `SkillAliasNormalizer`, API 응답 구조를 변경하지 않았다.

## 7. 테스트/검증 결과
문서 작업 중심이지만 dev 최신 R3 기준 확인을 위해 최소 diff check 와 targeted test 를 실행했다.

```bash
git diff --check

cd backend
./gradlew.bat test --tests com.careertuner.fitanalysis.service.EvidenceGateServiceTest
./gradlew.bat test --tests com.careertuner.SpringBeanConventionTests
```

결과:

- `git diff --check`: 공백 오류 없음.
- `EvidenceGateServiceTest`: `BUILD SUCCESSFUL`.
- `SpringBeanConventionTests`: `BUILD SUCCESSFUL`.

## 8. 다음 작업 후보
- reports/67 기준으로 RAG hard-case benchmark fixture 를 재구성한다.
- R3 gate reason 샘플에서 반복 alias 후보와 false-positive 패턴을 수집한다.
- model-card 다음 개정에는 운영 `REVIEW_REQUIRED` 분포와 gate reason 처리 결과를 반영한다.
- RAG 재평가는 production 연결이 아니라 offline benchmark PR 로 시작한다.

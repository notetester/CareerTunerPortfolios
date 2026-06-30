# R3 자동 검증 보강 + C AI 체크리스트 최신화

## 1. 목적
R3 evidence gate 계열 작업이 dev 에 누적된 상태에서, 런타임 정책을 바꾸지 않고 자동 검증을 보강했다. 초점은 legacy `gateStatus=null` 데이터, `gateReasonsJson` 파싱 안전성, 서버 `reviewRequiredOnly` 필터, 관리자 홈/대시보드의 최신 지원 건 기준 카운트를 회귀 테스트로 고정하는 것이다.

## 2. 추가한 자동 검증
- `AdminFitAnalysisServiceImplTest`: legacy `gateStatus=null` 상세 조회, PASSED/REVIEW_REQUIRED/REJECTED/null 혼합 목록 변환, `gateReasonsJson` null/`[]`/정상 JSON/깨진 JSON 파싱을 검증한다.
- `AdminFitAnalysisR3MapperXmlTest`: `reviewRequiredOnly=true` SQL 이 `REVIEW_REQUIRED` 만 반환하고 legacy null row 를 포함하지 않는지 정적 검증한다.
- `AdminFitAnalysisR3MapperXmlTest`: 관리자 홈/대시보드의 검토 대기 카운트가 application case 별 최신 `fit_analysis` 만 기준으로 하는지 정적 검증한다.
- 프런트 타입/렌더링 경계: 관리자 상세의 `gateReasons` 를 optional/null-safe 로 처리해 오래된 응답이나 깨진 응답에서도 상세 카드가 깨지지 않도록 했다.

## 3. 테스트 파일 변경
- `backend/src/test/java/com/careertuner/admin/fitanalysis/service/AdminFitAnalysisServiceImplTest.java`
- `backend/src/test/java/com/careertuner/admin/fitanalysis/mapper/AdminFitAnalysisR3MapperXmlTest.java`
- `frontend/src/admin/features/fit-analysis/types/adminFitAnalysis.ts`
- `frontend/src/admin/features/fit-analysis/pages/AdminFitAnalysis.tsx`

## 4. 체크리스트 경로
- 최상위 체크리스트: [`../AI_ROADMAP_CHECKLIST.md`](../AI_ROADMAP_CHECKLIST.md)
- 이 파일은 C career strategy AI 의 production runtime, model/benchmark, RAG, evidence gate/safety, data/training backlog, admin/observability, next candidates 를 한 곳에서 추적한다.

## 5. reports / PR 범위
dev 기준 R3 관련 최신 범위는 다음 흐름으로 본다.

- PR #174 — R3 review-first evidence gate 적용, [reports/61](61_rag_r3_review_first_gate_implementation.md)
- PR #175 — userEvidence 기준 정정, [reports/62](62_rag_r3_evidence_gate_user_evidence_hotfix.md)
- PR #180 — skill alias normalizer, [reports/63](63_rag_r3_evidence_gate_skill_alias_normalizer.md)
- PR #182 — mention-boundary 보강, [reports/64](64_rag_r3_skill_alias_mention_boundary.md)
- PR #183 — ProfileAiService primary 충돌 수정
- PR #184 — Spring provider 계약 회귀 방지
- PR #186 — dev 통합 검토, [reports/65](65_r3_evidence_gate_dev_integration_check.md)
- 이번 후속 — 자동 검증 보강 + 체크리스트 최신화, [reports/66](66_r3_auto_verification_and_ai_checklist.md)

## 6. stale doc banner
`docs/AI_CAREER_STRATEGY_EVIDENCE_GATE_DESIGN.md` 상단에 경고 배너를 추가했다. 해당 문서는 R3 이전 설계 초안이므로 최신 구현 상태는 `AI_ROADMAP_CHECKLIST.md` 와 reports/61~65 기준으로 보도록 명시했다. 또한 PR #175 이후 `userEvidence` 가 `profileSkills + profileCertificates` 로 고정되었고 AI 파생 `matchedSkills` 를 보유 근거로 신뢰하지 않는다는 점을 함께 표시했다.

## 7. 테스트 결과
실행한 명령과 결과:

```bash
cd backend
./gradlew.bat test --tests com.careertuner.admin.fitanalysis.service.AdminFitAnalysisServiceImplTest --tests com.careertuner.admin.fitanalysis.mapper.AdminFitAnalysisR3MapperXmlTest
./gradlew.bat test
./gradlew.bat test --tests com.careertuner.fitanalysis.service.EvidenceGateServiceTest
./gradlew.bat test --tests com.careertuner.fitanalysis.service.FitAnalysisServiceImplTest
./gradlew.bat test --tests com.careertuner.fitanalysis.ai.OssFitAnalysisAiServiceTest
./gradlew.bat test --tests com.careertuner.SpringBeanConventionTests
```

모두 `BUILD SUCCESSFUL`.

```bash
cd frontend
npm run typecheck
```

`tsc --noEmit` 통과.

## 8. 남은 자동화
- 실제 DB fixtures 로 `reviewRequiredOnly=true` 를 통합 테스트하는 경로는 아직 없다. 현재는 mapper XML 정적 검증으로 회귀를 고정했다.
- 관리자 홈/대시보드 카운트도 현재는 SQL 구조 정적 검증이다. H2/MySQL 호환 fixture 기반 통합 테스트는 별도 후보로 남긴다.
- gate status 분포와 false-positive 처리 결과를 운영 리포트로 집계하는 자동화는 아직 없다.

## 9. next candidates
- 관리자 gate review workflow: 검토 완료, 재분석 요청, memo/reason 연결.
- R3 gate reason 샘플 기반 false-positive review 및 alias 후보 triage.
- RAG 재도입 전용 hard-case benchmark 재구성: scoped context, 개인정보 격리, unsupported claim 감소 기준.
- model-card 에 R3 production safety 상태 반영.

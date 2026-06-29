# R3 — review-first evidence gate 백엔드/관리자 구현 보고 (C 영역)

> R3-pre 설계([reports/60](60_rag_r3_pre_backend_gate_design.md) · [docs/AI_CAREER_STRATEGY_EVIDENCE_GATE_DESIGN.md](../../../docs/AI_CAREER_STRATEGY_EVIDENCE_GATE_DESIGN.md))를
> 실제 backend service layer + 관리자 검토 큐/대시보드로 구현한 작업 보고. RAG runtime 자동주입·rewrite 자동노출은 보류(설계 비목표 준수).
> 브랜치 `feat/c-r3-review-first-evidence-gate`(origin/dev 기준). 자체 LLM 실험 아카이브(reports/54~60)의 결론을 제품 구조로 옮긴 첫 backend 적용이다.

## 1. 목적
적합도 분석 AI 설명 출력이 **미보유 역량을 '보유'로 단정**(grounding conflation)하는 위험을, 모델을 바꾸지 않고
**출력 후 결정론 검사 → 검토 상태 표시(review-first)** 로 완화한다. R2b~R2f 실측에서 retrievedContext 주입은 3B 모델 grounding을
개선하지 못했고(아래 §3), review/reject gate는 출력을 변형하지 않고 위험 claim을 안정적으로 잡았다(정보손실 0, 점수/판단·계약 불변).

## 2. 결론 요약
- **구현 완료**: `EvidenceGateService`(결정론) + fitanalysis 응답 DTO `safety` 블록 + C-only 테이블 2종 +
  `FitAnalysisServiceImpl` 후처리 배선 + 관리자 적합도(gateStatus 표시·REVIEW_REQUIRED 필터) + 관리자 홈/대시보드 검토 큐 카운트 + 프런트 admin 반영.
- **불변 보장**: gate는 `fitScore`/`applyDecision`/`matchedSkills`/`missingSkills`를 읽기만 한다(단위·서비스 테스트로 단언).
- **기존 E1 grounding guard 미변경**: OSS 서비스 내부 hard guard(retry→fallback)는 그대로, gate는 그 위의 soft 층(이중 안전).
- **공통 영역 미변경**: 공통 `ApiResponse` record·`ai_usage_log` 스키마·routes·schema.sql 공통구조 불변. A/B/D/E/F 원본·AI 출력 JSON 구조·기존 API 계약·라우트 불변.
- **검증**: 백엔드 전체 테스트 `BUILD SUCCESSFUL`(gate 10 + service 3 + E1 9 포함), 프런트 `tsc --noEmit` 무오류.

## 3. 배경 — RAG 실험 아카이브가 가리킨 결론(reports/54~60)
| 단계 | 실험 | 결과 |
| --- | --- | --- |
| R2b | flat retrievedContext A/B(hard cases) | grounding 개선 없음 |
| R2c | scoped/guarded context(contextRole/ownership) | 개선 없음 |
| R2d | evidence-bucket context | 개선 없음 |
| R2e | 결정론 evidence gate post-filter(reject/review/rewrite) | gate가 위험 claim을 결정론으로 분류 |
| R2f | output-capture end-to-end(실모델 출력 캡처) | review/reject는 정보손실 0·점수/판단 불변, rewrite는 의미손실·malformed(미사용) |
| R3-pre | backend 설계 | review-first gate를 no-context 경로 + E1 위의 추가 안전층으로 설계 |

→ **레버는 모델 프롬프트가 아니라 서버 측 결정론 review-first gate.** R3는 이 결론을 backend로 구현한다.

## 4. 문서 근거 확인표 (구현 전 작성, C 범위 확인)
| # | 작업 대상 | 근거 | 판정 |
| --- | --- | --- | --- |
| 1 | R3 gate = 적합도 후처리 안전층 | TEAM §3 #12, §6 AI 처리 원칙("설명 가능한 근거") | C |
| 2 | `backend/.../fitanalysis/**` | TEAM §6 담당 폴더 | C |
| 3 | fitanalysis DTO 내 safety(공통 ApiResponse 미변경) | TEAM §11 / FEATURE_MODULE §3 | C(DTO), 공통 record 불변 |
| 4 | `FitAnalysisServiceImpl` 후처리 배선 | FEATURE_MODULE §3 | C |
| 5 | OssFitAnalysisAiService E1 유지·일반화 | TEAM §6 | C(E1 약화 금지) |
| 6 | C-only evidence/gate 테이블 | TEAM §6 주요 DB·연동경계, db/patches `_c_*` 관행 | C(additive) |
| 7 | admin/home 검토 큐 요약 | TEAM §12 / FEATURE_OWNERSHIP §7(admin/home=C) | C |
| 8 | admin/dashboard 검토 카운트 | TEAM §6 담당 폴더(admin/dashboard) | C |
| 9 | admin/fit-analysis gateStatus·필터 | TEAM §6 담당 폴더·관리자 완료기준 | C |
| 10 | 공통 ai_usage_log 연동(스키마 미변경) | TEAM §3·§11 | 공통(기존 컬럼만) |
| 11 | RAG runtime·rewrite 자동노출 보류 | reports/60 결론, 설계 §3 비목표 | 보류 |

**결론:** R3 전 범위가 C 담당. 공통 영역과 타 담당 원본은 변경하지 않는다.

## 5. 적용 범위 / 비목표
- 대상: `fitanalysis` 도메인 AI 설명 결과의 free-text(fitSummary=`FitAnalysisAiResult.strategy`).
- 위치: 모델 호출 + 기존 E1 grounding guard **이후**의 결정론 후처리.
- 비목표: RAG runtime 자동주입 아님(`ragRuntimeEnabled=false`), rewrite 자동노출 아님(`rewriteApplied=false`),
  E1 guard 대체 아님, 점수/applyDecision/계약 필드 변경 아님.

## 6. evidence source 구분(현 runtime: RAG off)
| source | 의미 | user-owned |
| --- | --- | --- |
| userEvidence | `matchedSkills` + 프로필 보유 스킬/자격 | 예 |
| jobRequirements | `requiredSkills`/`preferredSkills`/`missingSkills` | 아니오 |
| catalogFacts | (RAG 도입 시에만, 현재 빈 버킷) | 아니오 |
| companyContext | (현재 빈 버킷) | 아니오 |

현 단계 gate는 사실상 **'보유 주장 스킬이 userEvidence에 있는가'** 검사(E1의 일반화). 4 버킷 스냅샷을 `fit_analysis_evidence_source`에 저장(감사·재현).

## 7. unsupported user-owned claim 정의 / gateStatus
- 정의: fitSummary 문장에 '보유' 류 표현이 있고 결핍·부정 표현이 없을 때, 그 문장이 언급한 **공고 요구 역량이 userEvidence에 없으면** unsupported user-owned claim.
- gateStatus:
  - `PASSED` — unsupported claim 없음.
  - `REVIEW_REQUIRED` — unsupported claim ≥1(자동 확정 금지, 검토 플래그). **review-first: 내용은 폐기하지 않는다.**
  - `REJECTED` — 구조 무결성 위반(matched/missing null, applyDecision null, 점수 범위 밖). 자동 확정 금지·재생성 검토.
- 심각도: 필수(`requiredSkills`) 역량을 보유로 단정 → `critical`, 그 외 요구 역량 → `warning`. `needsHumanReview = (PASSED 아님)`.

## 8. 휴리스틱 — E1과 동일 기준, E1 코드는 불변
gate의 보유/결핍 판정은 `OssFitAnalysisAiService`의 E1과 **동일 기준**(POSSESSION/LACK 표현, 한 문장 내 보유 표현 존재 + 결핍·부정 표현 부재).
단 E1 코드를 손대지 않기 위해 `EvidenceGateService`에 self-contained로 복제했다(중복은 2개 짧은 배열, 안전 우선). E1=hard fallback, gate=soft review로 책임이 분리되어 서로를 약화하지 않는다.

## 9. response envelope — 공통 ApiResponse 불변
공통 `ApiResponse<T>`(record `success/code/message/data`)는 바꾸지 않고, 적합도 응답 DTO(`FitAnalysisDetailResponse`)에 옵셔널 `safety`(`FitSafetyResponse`)를 추가했다.
```
safety: { gateStatus, needsHumanReview, maxSeverity,
          gateReasons: [{type, claim, reason, severity}],
          evidenceGateVersion: "r3-review-first", ragRuntimeEnabled: false, rewriteApplied: false }
```
- `fitScore`/`applyDecision`은 gate가 변경하지 않음. `safety`는 노출·검토 상태만.
- `gateReasons.claim`은 스킬명/축약만(개인정보·원문 prompt 미포함).
- R3 이전 분석은 gate 레코드가 없어 `safety=null`(하위호환, 프런트는 없으면 기존대로 동작).

## 10. service layer 위치 / 호출 지점
- 신규 `fitanalysis/service/EvidenceGateService`(@Service, 순수 함수, 외부호출 없음) + `EvidenceGateDecision`(record, 중첩 `Reason`/`EvidenceSource`).
- 호출: `FitAnalysisServiceImpl.generate(...)`가 `fitAnalysisAiService.generate(command)` 결과를 받은 **직후** `evidenceGateService.evaluate(command, ai)`.
- 저장: `fit_analysis` insert 후 `persistGate(row.getId(), gate)` — gate 결과 1행 + evidence 버킷 4행.
- 응답: `response()`가 `findGateResultByFitAnalysisId`로 gate를 읽어 `safety` 구성.

## 11. C-only 데이터 모델 (additive, owner-prefixed)
- `fit_analysis_evidence_source` — gate가 쓴 evidence 버킷 스냅샷(source_type/user_owned/item_count/items_json). 감사·재현.
- `fit_analysis_gate_result` — 적합도 1건당 gate 결정 1:1(gate_status/needs_human_review/reason_count/max_severity/gate_reasons_json/evidence_gate_version/rag_runtime_enabled/rewrite_applied).
- 두 테이블 모두 `fit_analysis(id)` FK ON DELETE CASCADE. 패치 `db/patches/20260629_c_fit_analysis_evidence_gate.sql` + `schema.sql` 캐노니컬 양쪽 반영(기존 C 정규화 테이블과 동일 관행).

## 12. 설계 대비 의도적 차이 — admin 검토 큐는 별도 projection 테이블 대신 단일 출처 쿼리
사전 합의 범위는 C-only 테이블(`fit_analysis_evidence_source`/`fit_analysis_gate_result`/`admin_ai_review_projection`)을 자유롭게 추가하는 것이었다.
구현에서는 **검토 큐를 별도 materialized projection 테이블로 만들지 않고** `fit_analysis_gate_result`를 단일 출처로 삼아 `fit_analysis`/`application_case`/`users`와 **LEFT JOIN 쿼리**로 제공했다.
- 이유: gate_result가 이미 gate_status/needs_human_review/created_at/fit_analysis_id를 보유한 검토 레코드라, projection은 동일 데이터를 복제해 dual-write 정합성 부담만 늘린다(단일 출처가 더 안전).
- 범위: C-area 내 설계 판단이며 의도(review-first gate + 관리자 검토 큐)는 그대로 충족. `admin_ai_*` 같은 광역 명칭 대신 C 소유 `fit_analysis_*` 접두를 유지해 소유권을 명확히 했다.

## 13. 관리자 반영 (백엔드)
- `admin/fitanalysis`: `findAll(reviewRequiredOnly)`에 `fit_analysis_gate_result` LEFT JOIN + `<if reviewRequiredOnly> gate_status='REVIEW_REQUIRED'`. 목록/상세 DTO에 gateStatus/needsHumanReview/gateReasonCount/gateMaxSeverity(+detail: evidenceGateVersion). 컨트롤러에 `@RequestParam reviewRequiredOnly`(옵셔널, 기본 false — 하위호환).
- `admin/home`: `countReviewRequiredAnalyses`(지원 건별 최신 분석 중 REVIEW_REQUIRED) → 요약에 `reviewRequiredAnalyses`.
- `admin/dashboard`: `countReviewRequiredAnalyses`(REVIEW_REQUIRED 총수) → overview에 `reviewRequiredAnalyses`.

## 14. 관리자/사용자 반영 (프런트)
- `admin/features/fit-analysis`: 목록 gateStatus 뱃지(검토 필요/반려/근거 통과), '검토 필요만' 필터, 상세 근거검토 배너(심각도·지적건수·정책버전). 사용자 점수/판단 불변 안내 문구 포함.
- `admin/features/home`·`admin/features/dashboard`: 검토 큐 카드 추가.
- `app/lib/mock/domains/admin`(C 도메인 mock 픽스처): gate 필드 채워 mock 모드 렌더 일관성 유지(공통 mock 프레임워크·타 도메인 데이터 미변경).
- 사용자 적합도 화면의 `safety` 시각화(검토중/보완필요 톤)는 soft 롤아웃 단계로 후속(이번엔 API로 데이터만 노출).

## 15. 운영 로그 / 감사
- gate 결정과 evidence 버킷을 C-only 테이블에 구조화 저장(원문 출력·prompt·개인정보 제외).
- `ai_usage_log`는 기존 컬럼만 사용(공통 스키마 미변경). `evidence_gate_version`으로 정책 버전 추적·롤백.

## 16. 테스트
- `EvidenceGateServiceTest`(10): supported→PASSED, 우대 요구 보유단정→REVIEW_REQUIRED/warning, 필수 요구 보유단정→critical, 결핍문맥 미플래그, 보유 자격증 미플래그, 계약 깨짐→REJECTED, 점수 범위밖→REJECTED, **점수/판단/매칭/부족 불변 단언**, evidence 버킷 스냅샷, RAG/rewrite off 상수.
- `FitAnalysisServiceImplTest`(신규 1 + 기존 2): generate가 gate 1행 + evidence 4행 저장, 버전 `r3-review-first`·rag/rewrite false, 저장 행의 점수/판단 불변. 기존 2 테스트는 5-arg 생성자로 갱신(실 `EvidenceGateService` 주입).
- `OssFitAnalysisAiServiceTest`(9) 무변경 통과 → E1 guard 미변경 확인.

## 17. 검증 결과
- `./gradlew test`(백엔드 전체): **BUILD SUCCESSFUL**. MyBatis XML 신규 statement 바인딩·LEFT JOIN 정상(전체 매퍼 로드 회귀 없음).
- `npm run typecheck`(프런트): **무오류**. 새 타입 필드와 mock 픽스처 정합.
- 라이브 admin 화면 스크린샷은 admin 세션 seed가 필요해 이번엔 정적 검증(전체 테스트 + tsc)으로 확인. UI는 기존 렌더 idiom(뱃지·필터·카드)을 그대로 따른다.

## 18. 불변식·안전 보장 체크리스트
- [x] 공통 `ApiResponse` record 미변경 — safety는 fitanalysis DTO 내부.
- [x] `fitScore`/`applyDecision`/`matchedSkills`/`missingSkills` gate가 변경 안 함(테스트 단언).
- [x] 기존 E1 grounding guard 미변경(OSS 테스트 9 통과).
- [x] `ai_usage_log` 스키마 미변경(기존 컬럼만).
- [x] A/B/D/E/F 원본·AI 출력 JSON 구조·기존 API 계약·라우트 미변경(추가는 전부 옵셔널/additive).
- [x] RAG runtime 자동주입 안 함(`ragRuntimeEnabled=false`), rewrite 자동노출 안 함(`rewriteApplied=false`).
- [x] gateReasons/evidence는 스킬명·축약만(개인정보·원문 prompt 미저장).

## 19. 롤아웃 / 롤백
- 현재는 **shadow→soft 경계**: gate를 계산·저장·관리자 노출까지 하되, 사용자 차단/자동 톤 강제는 아직 안 함(데이터만 API 노출).
- 롤백: gate는 추가층이라 호출/저장을 제거하면 기존 경로(no-context 3B + E1 guard)로 즉시 복귀. `safety`는 옵셔널이라 미산출 시 프런트 하위호환. `evidence_gate_version`으로 정책 재현.

## 20. 후속 작업(이번 범위 밖)
- soft 롤아웃: 사용자 적합도 화면에서 `REVIEW_REQUIRED`를 '검토중/보완 필요' 톤으로 표시(자동 확정 금지).
- enforce: critical을 관리자 재생성 라우팅. 단계별 feature flag + 메트릭 게이트.
- rewrite 재설계(R2g): 문장 단위 교정(현 whole-sentence 교체는 의미손실·malformed라 미사용).
- RAG runtime은 계속 off(실험상 grounding 미개선). 도입 시 catalogFacts/companyContext 버킷이 채워지며 gate의 catalog_as_owned/unsupported 분류가 작동.
- review/reject 비율 모니터링으로 severity 임계·UX 부담 관찰(다중 run 후 확정).

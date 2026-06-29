# R3 evidence gate — userEvidence 기준 정정 hotfix (#174 후속, C 영역)

> [PR #174](https://github.com/notetester/CareerTuner/pull/174)로 머지된 review-first evidence gate 구조(reports/61)는 유지하고,
> gate 판정 기준을 보정한 hotfix. 되돌리기가 아니라 보정. 브랜치 `fix/c-r3-evidence-gate-user-evidence`(origin/dev 기준).

## 1. 목적
#174 gate 는 사용자 보유 근거(userEvidence)에 **AI 출력 `matchedSkills` 를 포함**했다. 이 구조는 AI 가 잘못 생성한
`matchedSkills` 를 다시 신뢰하게 되어(순환), 공고 요구 기술을 사용자 보유 기술처럼 단정한 케이스를 놓칠 수 있다.
이번 hotfix 는 (a) userEvidence 를 사용자 원본 입력으로 한정, (b) AI 파생 결과 분리, (c) matched 순환 오류 검출,
(d) 사용자 노출 텍스트 검사 확대, (e) 관리자 상세 reason 노출, (f) home/dashboard 카운트 정합성을 보정한다.

## 2. 발견한 문제 — matchedSkills 순환 신뢰
- #174: `userEvidence = distinct(concat(ai.matchedSkills(), profileSkills, profileCertificates))`.
- `matchedSkills` 는 AI(또는 규칙엔진)가 만든 **파생 결과**이지 사용자 원본 근거가 아니다. 이를 보유 근거로 신뢰하면,
  AI 가 `Spark` 를 잘못 matched 로 만들고 fitSummary 에서 "Spark 보유"라고 단정해도, gate 가 "Spark ∈ userEvidence" 로 보아 **통과**시킨다.
- 즉 gate 가 막아야 할 바로 그 케이스(미보유 역량을 보유로 단정)를 자기 입력으로 면제하는 순환.

## 3. userEvidence 기준 변경 전/후
| | 전(#174) | 후(hotfix) |
| --- | --- | --- |
| userEvidence | `matchedSkills + profileSkills + profileCertificates` | `profileSkills + profileCertificates` (사용자 원본만) |
| userOwned=true 버킷 | userEvidence(혼합) | userEvidence(원본만) |
| matchedSkills 취급 | 보유 근거로 신뢰 | **derived 버킷으로 분리**, 검토 대상 |

## 4. derivedMatchedSkills 분리 방식 + evidence 버킷(4→6)
gate 가 저장하는 evidence 버킷:
| 버킷 | 내용 | userOwned |
| --- | --- | --- |
| userEvidence | profileSkills + profileCertificates | **true** |
| derivedMatchedSkills | ai.matchedSkills | false |
| jobRequirements | requiredSkills + preferredSkills | false |
| missingSkills | ai.missingSkills | false |
| catalogFacts | (RAG off, 빈) | false |
| companyContext | (빈) | false |

신규 테이블/컬럼 없이 기존 `fit_analysis_evidence_source.source_type` 값만 추가(`derivedMatchedSkills`/`missingSkills`).

## 5. 새 reason type / 판정 경로
gate 는 두 결정론 경로를 돌려 claim 기준 중복 제거(critical 우선):
1. **derived-matched audit**(순환 차단): `ai.matchedSkills` 중 userEvidence(원본)에 없는 항목 → `matched_skill_without_user_evidence`. 텍스트 보유 서술이 없어도 검출.
2. **text-claim audit**(확대): 사용자 노출 텍스트가 공고 요구/부족 역량을 '보유로 단정'(보유 표현 有·결핍/부정 표현 無)했는데 userEvidence 에 없으면 → `requirement_as_owned`.

severity: 해당 역량이 `requiredSkills` 면 `critical`, 그 외(우대/요구 밖) 면 `warning`. 둘 다 gateStatus=`REVIEW_REQUIRED`(review-first: 내용 미폐기). 구조 무결성 위반은 `REJECTED`(기존 유지).

## 6. 사용자 노출 텍스트 검사 범위 확대
| 전 | 후 |
| --- | --- |
| `ai.strategy()` 만 | `strategy` + `scoreBasis` + `strategyActions` + `applyDecision.reasons` + `applyDecision.actions` |

명확한 accessor 가 있는 필드만 대상(무리한 reflection 미사용). `FitApplyDecision` 의 실제 accessor 는 `reasons()`/`actions()` 라 그대로 사용. `auditClaims(String)` → `userFacingTexts(List<String>)` 구조로 변경.

## 7. 관리자 상세 gateReasons 노출
- backend: `AdminFitAnalysisResult.gateReasonsJson` + 매퍼 `gr.gate_reasons_json` 조회 + `AdminFitAnalysisDetailResponse.gateReasons`(파싱). 목록 응답은 기존대로 count/severity 만.
- 파싱: `AdminFitAnalysisServiceImpl.parseGateReasons`(null/깨짐 → 빈 목록). reason view 는 `FitSafetyResponse.Reason` 재사용(type/claim/reason/severity).
- frontend: 상세 evidence gate 박스 아래 reason 목록(severity 뱃지 + claim + reason + type). 개인정보·원문 prompt·긴 원문 미저장/미표시(축약만).

## 8. home/dashboard 검토 대기 카운트 정합성
- 기준 통일: **application_case 별 최신 fit_analysis 의 gate_status=REVIEW_REQUIRED** 만 센다.
- home 은 이미 latest-per-case. **dashboard 를 누적 전체 → latest-per-case 로 변경**(home 과 동일 SQL).
- UI 문구 통일: home/dashboard 모두 "근거 검토 대기 / 최신 지원 건 기준".

## 9. 서버 필터 연결
- frontend `검토 필요만` 체크박스를 서버 `reviewRequiredOnly` 와 연결: 체크 시 `getAdminFitAnalyses(true)`, 해제 시 `(false)` 로 재fetch(useEffect deps=[reviewOnly]).
- 검토 필요 클라이언트 필터 제거(서버 1차 필터로 대체). 검색/점수대/상태/메모/재분석은 클라이언트 유지. 메모 갱신 후 재fetch 도 현재 reviewOnly 전달.

## 10. 유지한 불변식
- `ApiResponse` record 변경 없음 · A/B/D/E/F 원본 테이블·AI 출력 JSON 구조·route 변경 없음.
- **신규 DB 테이블/컬럼 없음**(기존 #174 테이블 재사용, source_type 값만 추가).
- 기존 E1 grounding guard(OssFitAnalysisAiService) 변경 없음.
- gate 는 `fitScore`/`applyDecision`/`matchedSkills`/`missingSkills` 미변경(읽기만, 테스트 단언).
- RAG runtime 자동 통합·rewrite 자동 노출·기본 모델 변경 없음.
- gate reason 에 개인정보·원문 prompt 미저장.

## 11. 테스트 결과
- `EvidenceGateServiceTest`: 17건(기존 10 + 신규 7) 통과 — matched 순환 회귀(critical), matched-only(텍스트 단정 없이 검출), scoreBasis/strategyActions/applyDecision 보유 단정 검출, 정상 보유 통과, 부정 문맥 통과.
- `FitAnalysisServiceImplTest`: 3건 통과(evidence 버킷 4→6 반영, 점수/판단 불변 단언).
- `OssFitAnalysisAiServiceTest`: 9건 통과 → E1 guard 미변경 확인.
- 백엔드 전체 `./gradlew test`: BUILD SUCCESSFUL. 프런트 `npm run typecheck`: 무오류.

## 12. RAG runtime / rewrite / model 미변경 확인
- `evidenceGateVersion="r3-review-first"`, `ragRuntimeEnabled=false`, `rewriteApplied=false` 유지.
- production prompt 에 retrievedContext 자동 주입 없음. rewrite 자동 노출 없음. 기본 모델(3B LoRA) 변경 없음.

## 13. 남은 한계와 후속
- 텍스트 검사 확대는 사용자 노출 ai-level 텍스트에 한정(DTO 파생 텍스트 adverseStrategies/next24HourActions 는 미검사 — 규칙엔진 결정론 산출이라 위험 낮음, 필요 시 후속).
- **skill 표면형(별칭) false-positive — 의도적 보수 설계로 유지**: 프로필 "Apache Spark" + 공고 "Spark" 처럼 별칭/부분문자열이 다르면
  exact(정규화 소문자) 매칭이 안 잡혀 정당 보유가 REVIEW_REQUIRED 로 잡힐 수 있다. 이는 #174 와 동일한 기존 동작이며(규칙엔진도 같은 기준으로 Spark 를 missing 처리),
  **substring 양방향 매칭으로 "고치면" 더 위험하다**: `"javascript".contains("java")` 처럼 프로필 JavaScript 가 Java 요구 보유 주장을 면제해
  conflation false-negative(MSSQL↔SQL 류, reports/53~54 hard-case)를 재유발한다. review-first gate 는 false-negative(놓친 conflation)보다 false-positive(과검토)를
  선호하는 게 안전 방향이라, 보수적 exact 매칭을 유지한다. 적정 해법은 **큐레이션 별칭 정규화**(naive substring 아님)이며 별도 후속.
- 적대검증(3 렌즈): 불변식·범위·정합성 전부 confirm. 지적된 substring fallback 은 위 사유로 채택하지 않음(안전 약화). React key 안정화·actions() 테스트는 반영.
- soft 롤아웃(사용자 화면 검토중 톤)/R2g rewrite phrase-level/LoRA 재학습은 이번 범위 밖(유지).
- review/reject 비율 모니터링으로 severity 임계·UX 부담 관찰.

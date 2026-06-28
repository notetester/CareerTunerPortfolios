# R3-pre — RAG 실험 결론 → review-first evidence gate backend 설계 판단 (2026-06-29)

> R2b~R2f 6연속 실험의 결론을 **제품 안전 구조로 번역**하는 단계. **backend 구현 아님** — Spring Boot 코드·
> runtime prompt·기본 모델·API 동작 변경 없음. 이 문서는 실험 결론과 R3-pre 판단을, 동반 설계 초안
> [docs/AI_CAREER_STRATEGY_EVIDENCE_GATE_DESIGN.md](../../../docs/AI_CAREER_STRATEGY_EVIDENCE_GATE_DESIGN.md) 은
> backend service layer 설계를 다룬다(둘 다 docs만, 코드 미변경).

## 1. 선행 확인
- **#172 merged** — R2f 결과가 dev 의 reports/59 에 반영. reports/60 번호 비어있음.
- R2f raw = CareerTunerAI `results/2026-06-28-rag-r2f-output-capture-001`(commit `462d1cc`).
- **backend 파일 미수정**(이 PR 은 docs/** + reports/60 만).

## 2. R2b~R2f 요약
| 단계 | 무엇 | 결론 |
| --- | --- | --- |
| R2b(reports/54) | flat RAG A/B 실측 | net wash. success/CJK 개선하나 **E1 grounding 악화**(3→5) |
| R2c(reports/56) | scoped/guarded RAG A/B/C | C 도 conflation 못 줄임(catalog B=C=8). 프롬프트 가드 불충분 |
| R2d(reports/57) | evidence bucket A/B/C/D | **no-context A 가 모든 축 최선**(pass_rate A>D>B>C). ctx 주입은 conflation 을 새로 만듦(A=0→B/C/D=5/6/4) |
| R2e(reports/58) | deterministic gate(R2d 카운트) | gate 가 측정 violation 제거 가능(집계 14→0), 점수/판단 mutation 0. 단 출력 텍스트 부재로 미검증 |
| R2f(reports/59) | output-capture gate end-to-end | **review/reject 가 robust**(위반 8/32 검출, 출력 미변형, 계약/점수 불변). rewrite 는 detector-safe+score-preserving이나 의미손실·malformed → 미흡 |

**핵심:** 모델 prompt(retrievedContext/role/guard)로는 3B 의 grounding conflation 을 못 막는다. **출력에 결정론 review-first evidence gate** 를 두는 것이 안정적 레버다.

## 3. RAG runtime 자동 통합 보류 이유
- R2b~R2d 실측에서 **retrievedContext 자동 주입은 no-context 대비 grounding 을 개선하지 못하고 오히려 conflation 을 새로 만든다**(직무요구/catalog 정의를 보유로 혼동). scoped·evidence-bucket 으로도 모델 단계에선 제거 안 됨.
- 따라서 **현 단계 production runtime prompt 에 retrievedContext 를 자동 주입하지 않는다.** RAG 는 모델 품질 개선 수단이 아니라 grounding risk 원인이다. (재학습/스키마 재설계 전까지 rag_poc 실험 기능으로만 유지.)

## 4. review-first gate 가 유망한 이유
- R2f 실측: review/reject gate 는 실제 출력 32건 중 위반 8건을 100% 검출, **출력을 변형하지 않아 정보손실 0**, fitScore/applyDecision·JSON 계약 불변, 모델 변동성과 무관.
- 즉 모델을 바꾸지 않고 **출력의 위험 claim 을 잡아 '검토 상태'로 돌리는** 안전장치로 적합. 백엔드엔 이미 OSS grounding guard(E1 mirror, `OssFitAnalysisAiService.groundingViolation`)가 있어 개념적 연속선상이다.

## 5. rewrite 보류 이유 (R2f 적대 검증)
rewrite 는 **자동 사용자 노출 기능으로 넣지 않는다**:
- 재-audit 0 은 **같은 detector 자기검증**(독립적 의미 안전 증명 아님).
- **문장 통째 치환**으로 정당한 co-located 정보 삭제, 다중 스킬 문장 일부 claim 소실.
- malformed text(이중 마침표·라틴 조사 `Spark은(는)`).
→ **R2g 에서 phrase-level rewrite 로 redesign 후 재검증**. 그 전까지 rewrite 미사용.

## 6. no-context 3B LoRA 기본 경로 유지 판단
- R2d 에서 **no-context A(현 production 경로)가 grounding 최선**. 즉 현재 `OssFitAnalysisAiService`(retrievedContext 없이 설명만 생성) + 기존 E1 grounding guard(retry→fallback)가 **현 시점 가장 안전한 기본 경로**다.
- 변경 없이 유지하고, 그 위에 review-first evidence gate 를 **추가 안전층(설계 후보)** 으로 얹는다.

## 7. backend 설계 후보
실제 backend 구조 기준(설계만, 구현·수정 아님):
- 통합 지점: `fitanalysis/service/FitAnalysisServiceImpl` 가 `FitAnalysisAiService.generate(...)` 결과를 받은 **직후** 후처리 단계.
- gate 책임 분리: 신규 `EvidenceGateService`(가칭) 가 AI 결과 + 입력(matchedSkills 등)을 받아 `gateStatus`/`needsHumanReview`/`gateReasons` 산출. 기존 `OssFitAnalysisAiService` 의 hard grounding guard(retry→fallback)는 유지하고, evidence gate 는 그 **이후의 soft review 층**.
- 노출: `ApiResponse<T>`(record success/code/message/data) 의 data 에 `safety` 블록 부가(또는 응답 DTO 확장). 점수/판단은 gate 가 바꾸지 않음.
- 상세는 [설계 문서](../../../docs/AI_CAREER_STRATEGY_EVIDENCE_GATE_DESIGN.md). **클래스 생성/수정은 backend owner(D/F) 합의 후 별도 PR.**

## 8. 남은 위험
- evidence gate detector 는 한국어 보유/부정 휴리스틱 기반 — 오탐/미탐 가능(절대값 보수적 해석). review-first 라 오탐은 '불필요한 검토 플래그'(안전측), 미탐은 잔여 risk.
- review 상태 비율(R2f 25%)이 높으면 UX/운영 부담 — severity 등급으로 자동노출/검토 임계 조정 필요(설계 §7).
- RAG 를 끄는 결정은 '현 3B+프롬프트' 한정. 재학습/더 큰 모델/스키마 재설계 시 재평가 대상.
- 변동성: 단일 run 표본이라 절대 수치는 흔들림(R2c~R2f negctrl 노이즈). 정책 임계는 다중 run 후 확정.

## 9. 다음 단계
1. **R3-pre(이 PR)**: 설계 문서 확정 → backend owner(D/F) 리뷰.
2. **R3**(별도, backend owner 주도): 합의된 review-first gate 를 service layer 에 구현(점수/판단 불변·E1 guard 독립 유지).
3. **R2g**(C): rewrite phrase-level redesign + 재검증(자동 노출 전).
4. RAG runtime 자동 통합은 보류 유지. 안전 기본 경로 = no-context 3B LoRA + E1 guard, 추가층 = review-first evidence gate.

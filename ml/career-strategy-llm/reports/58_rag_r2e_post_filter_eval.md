# R2e — deterministic evidence gate post-filter 오프라인 검증 (2026-06-29)

> R2b/R2c/R2d 3연속 결론: 단순 retrievedContext 주입(scoped·evidence-gated 포함)은 no-context 대비
> grounding conflation 을 못 없앤다. **진짜 레버는 모델 prompt 가 아니라 출력을 서버측에서 결정론으로 거르는
> post-filter.** R2e 는 R2d 의 evidence audit 을 측정이 아니라 **gate(reject/review/rewrite)** 로 써서, 이미 측정된
> R2d 결과에 적용했을 때 안전성을 강제할 수 있는지 오프라인 검증한다. **새 GPU 호출 없음**(R2d raw 재사용).
> **backend 통합 아님** — Spring API·runtime prompt·기본 모델 변경 없음. rag_poc + reports 만. LLM 재호출 없음.

## 1. 선행 확인
- **#170 merged** — R2d 결과가 dev 의 reports/57 에 반영. R2d audit 함수(`detect_unsupported_user_owned_claims` 등) 재사용 가능.
- **R2d raw** = CareerTunerAI `results/2026-06-28-rag-r2d-evidence-gate-001`(commit `f46c726`). reports/58 번호 비어있음 확인.

## 2. R2d 결과 요약 (출발점)
gate_pass_rate **A 0.697 > D 0.65 > B 0.625 > C 0.569** — no-context A 가 최선, ctx 주입(B/C/D)은 grounding 악화.
evidence_gate_violation A 10 · B 15 · C 22 · D 14. 즉 모델 단계에선 어떤 RAG 변형도 violation 을 못 없앤다.

## 3. R2e 목표
모델 출력에 userEvidence 가 뒷받침하지 않는 보유 claim 이 있으면 **서버측 deterministic post-filter** 가
reject / review / rewrite 처리해 evidence_gate_violation 을 실질 0 으로 만들 수 있는지, 그리고 그 과정에서
**fitScore/applyDecision·JSON 계약을 깨지 않는지** 검증.

## 4. post-filter 정책 (`apply_evidence_gate_filter.py`)
- **reject**: unsupported claim 이 있으면 `gateStatus=REJECTED` + `needsHumanReview=true` + `gateReasons`(type/claim/source/reason). 출력 미사용.
- **review**: `gateStatus=REVIEW_REQUIRED` + `needsHumanReview=true`. 출력 유지하되 검토 플래그.
- **rewrite**: free-text(fitSummary/strengths)의 보유 단정 문장만 결정론 규칙으로 안전 문구("…공고 요구 역량으로, 보유가 아니라 학습·보완 필요")로 치환. `filteredOutput` 별도 저장(원본 비파괴).
- 공통 불변(엄수): **fitScore/applyDecision/matchedSkills/missingSkills 절대 미변경**(assert), JSON parse 실패는 `PARSE_FAIL` 로 드러내고 rewrite 로 숨기지 않음, LLM 재호출 없음.

## 5. raw 입력 경로
- 입력: R2d raw(CareerTunerAI `results/2026-06-28-rag-r2d-evidence-gate-001`, `f46c726`). main repo 미복사.
- 분석 산출(필터링 결과 성격): CareerTunerAI `results/2026-06-28-rag-r2e-post-filter-001/`. main repo 엔 reports/58 만.

## 6. 테스트 결과
- `test_evidence_gate_filter.py` **12**: reject/review/rewrite 동작 · catalog/job_requirement-only 불허 · userEvidence 뒷받침 통과 · **점수/판단 불변** · parse 실패 미은폐 · filteredOutput 비파괴 · `evaluate(filteredOutput)` 계약 유지 · gate_summary 카운트 · 통과 출력 과잉수정 안 함.
- `test_r2e_post_filter_eval.py` **5**: R2d 카운트→gate 후 잔여 violation 0 · before=측정값 · 위반 케이스 카운트 · mutation 0.
- 기존 rag_poc 11종 **무회귀**.

## 7. 실행 여부
**새 GPU 호출 없음.** R2d 가 측정한 audit 카운트(CareerTunerAI f46c726)에 gate 를 적용한 집계 분석 + 필터 자체는 구체 출력에 대해 단위 테스트로 검증. **R2d raw 가 모델 출력 텍스트를 보존하지 않아**(집계만) 실제 출력 텍스트 단위 rewrite 자연스러움 측정은 본 단계에서 불가 → R2f(출력 캡처)로 미룸(추정하지 않음).

## 8. D_raw vs E_filtered 주요 지표 (R2d 측정값에 gate 적용)
| 변형 | evidence_gate_violation before→after | catalog_as_owned before→after | pass_rate before | gated cases(/16) | 점수/판단 mutation |
| --- | --- | --- | --- | --- | --- |
| A | 10→**0** | 0→0 | 0.697 | 5 | **0** |
| B | 15→**0** | 5→0 | 0.625 | 7 | **0** |
| C | 22→**0** | 6→0 | 0.569 | 9 | **0** |
| **D** | **14→0** | **4→0** | 0.65 | **6** | **0** |

- **gate 는 측정된 unsupported claim 을 전부 제거**(after=0). 단 이 'after=0' 은 gate 검출기 = audit 이라 **구성적 귀결**(audit 이 잡은 것을 gate 가 처리). 새로 증명하는 건 **gate 가 결정론으로 동작하고 불변식을 지킨다**는 것(테스트).
- 비용: D 는 16 케이스 중 **6 케이스**(약 38%)가 위반 포함 → reject/review/rewrite 대상. A/B/C 는 5/7/9.
- **점수/applyDecision mutation 0**(전 변형) — gate 는 free-text 만 손대고 계약 필드는 불변.

## 9. reject / review / rewrite 비교
| mode | 동작 | 안정성 | 한계 |
| --- | --- | --- | --- |
| reject | 위반 출력 폐기 + 사람검토 | **높음**(결정론, violation 0 보장) | 가용성↓(D 38% 케이스 폐기) |
| review | 출력 유지 + 검토 플래그 | **높음**(violation 표면화, 출력 손실 없음) | 자동 안전 보장은 사람에 위임 |
| rewrite | 보유 단정→안전 문구 치환 | 중간(결정론이나 **문장 자연스러움 미검증**) | 실제 출력 텍스트로 검증 필요(R2f) |

## 10. 개선 케이스
D 의 위반 6 케이스(`hard-mssql-002`·`hard-fakeprod-003/004`·`hard-research-007`·`hard-data-012`·`hard-negctrl-014`)는 reject/review 에서 100% 안전 처리(violation 0, needsHumanReview). rewrite 는 해당 보유 단정 문장을 "요구/보완/학습" 표현으로 치환(테스트 3·9 로 동작 확인).

## 11. 실패 / 위험 케이스
- **rewrite 텍스트 자연스러움 미검증**: R2d raw 에 출력 텍스트가 없어 실제 문장 치환 품질(의미 왜곡·문맥 단절)을 실데이터로 못 봤다 → R2f 출력 캡처로 검증해야 안전.
- **가용성 비용**: reject 는 D 의 38% 케이스를 폐기 → 사용자 경험 저하. review 가 절충(출력 유지+플래그).
- parse 실패는 `PARSE_FAIL` 로 분리(rewrite 로 은폐 안 함, 테스트 8).

## 12. contract / 점수 / 판단 불변 확인
- `evaluate(filteredOutput)` json_ok·required_ok 유지(테스트 10). fitScore/applyDecision/matchedSkills/missingSkills mutation **0**(apply_gate assert + 집계 분석). free-text(fitSummary/strengths)의 unsupported 보유 표현만 대상.

## 13. 개인정보 / 보안 확인
- R2d raw 는 synthetic(개인정보 0). gate 는 LLM/외부 API 호출 없음(전부 로컬 결정론). raw/filtered JSON 은 CareerTunerAI 에만, main repo 미커밋.

## 14. backend / runtime / model 미변경 확인
- PR diff 는 `rag_poc/`(scripts/tests) + `reports/58` 뿐. backend·runtime prompt·기본 모델·LangChain/Spring AI 변경 0. R2d audit·`evaluate`·grounding helpers 는 **import 재사용만**. evidence gate 는 출력 후처리만(채점/E1/E2 로직 불변).

## 15. 다음 단계 판단
**판정(1: post-filter 유망 — 단 rewrite 는 review-first).** deterministic evidence gate 는 RAG 의 conflation 문제에 대한 **실효 레버**다:
- **reject/review 는 안정적으로 violation 을 0 으로 만들고 점수/판단·계약을 안 깬다** — 모델 변동성과 무관하게 출력 안전을 강제할 수 있다(R2b/c/d 가 모델 prompt 로는 못 한 것).
- rewrite 는 결정론이나 **문장 품질을 실데이터로 검증 못 함**(R2d raw 출력 부재).
- 핵심 통찰: RAG 를 모델에 '설득'하는 대신, **출력에 결정론 gate 를 두는 게 정답**. 단 'after=0' 은 audit 자기참조라, 실측 가치는 **실제 모델 출력에 gate 를 통과시켜** reject/review/rewrite 의 가용성·품질을 보는 것.

→ 다음:
- **R2f(권장)**: D(또는 A) 출력을 **출력 텍스트까지 저장**해 재캡처(최소 32콜) → gate 를 실제 출력에 적용해 reject 율·rewrite 자연스러움·계약 유지를 실측. deterministic post-filter 의 end-to-end 효과 확정.
- **R3-pre(설계)**: review-first(needsHumanReview) gate 를 backend service layer 설계에 반영(구현 아님). reject 는 가용성 비용이 커 review 우선.
- RAG runtime 자동 통합은 계속 보류(모델 단계 효과 없음). 안전은 E1 guard + evidence gate(post-filter)로.

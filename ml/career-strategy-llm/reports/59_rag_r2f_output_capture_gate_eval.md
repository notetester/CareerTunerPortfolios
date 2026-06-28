# R2f — output-capture evidence gate end-to-end 검증 (2026-06-29)

> R2e 는 R2d 가 *측정한* audit 카운트에 gate 를 적용했으나(집계 14→0), **R2d raw 에 실제 출력 텍스트가 없어**
> rewrite 품질·실제 reject 율·filteredOutput 계약 유지를 검증하지 못했다. R2f 는 D(evidence-gated) 변형을
> 최소 호출로 돌려 **실제 출력 텍스트(rawText)를 저장**하고, 각 출력에 review/reject/rewrite gate 를 적용해
> end-to-end 로 본다. 특히 rewrite 는 **재-audit**(치환된 텍스트를 다시 audit)으로 violation 이 진짜 사라지는지
> 실측한다. **backend 통합 아님** — Spring API·runtime prompt·기본 모델 변경 없음. rag_poc + reports 만. LLM 재호출 없음.

## 1. 선행 확인
- **#171 merged** — R2e 결과가 dev 의 reports/58 에 반영, `apply_evidence_gate_filter.py` 재사용 가능.
- **R2e 분석** = CareerTunerAI `results/2026-06-28-rag-r2e-post-filter-001`(commit `17f5d8e`). reports/59 번호 비어있음.
- **4090 reachable**(chanssick) + Ollama GPU. 메인 repo 타 팀원 브랜치 → **격리 worktree**.

## 2. R2e 결과 요약 (출발점)
deterministic gate 가 R2d 측정 violation 을 0 으로(A 10→0/B 15→0/C 22→0/D 14→0), 점수/판단 mutation 0. 단 'after=0'
은 gate 검출기=audit 이라 **구성적**(자기참조). rewrite 텍스트 품질·실제 reject 율은 출력 텍스트 부재로 미검증 → R2f.

## 3. R2f 목표
실제 모델 출력에서 (1)gate 가 어떤 claim 을 잡는가 (2)reject/review 가 안전 동작하는가 (3)rewrite 가 의미 보존적으로
"보유"→"요구/보완/학습"으로 바꾸는가 (4)filteredOutput 이 계약·fitScore/applyDecision 을 유지하는가 (5)review-first
gate 가 backend 설계 후보가 되는가 — 를 실측한다. **rewrite 는 재-audit 으로 실제 잔여 violation 을 본다(구성적 아님).**

## 4. output capture 설계 (`capture_r2f_outputs.py`)
D 변형 호출마다 저장: `rawText`(원문)·`parsedJson`·`parseStatus`·`evidenceAudit`·`gateReview/gateReject/gateRewrite`·
`evidenceAuditAfterRewrite`(rewrite 재-audit)·`evaluationBefore/AfterRewrite`·`scoreDecisionMutation`·input/messagesHash.
`_call_ollama`/`evidence_audit`/`apply_gate`/`evaluate` 재사용. **rawText 는 CareerTunerAI 에만**(main repo 금지, 보고서엔 truncated 샘플만).

## 5. 실행 여부와 jobId
**실측 완료(2026-06-29, 4090 Ollama, D variant only, repeat 2, 총 32 call).** jobId `2026-06-28-rag-r2f-output-capture-001`.
격리 worktree(PARK-SEONG-HO 미간섭). rawText 포함 capture → CareerTunerAI `results/2026-06-28-rag-r2f-output-capture-001/r2f_output_capture.json`(commit `462d1cc`).

## 6. raw 저장 위치
- rawText 포함 capture: CareerTunerAI `results/2026-06-28-rag-r2f-output-capture-001/r2f_output_capture.json`. main repo 미커밋.
- main repo: capture/summarize 스크립트 · 테스트 · reports/59 요약(truncated 샘플)만.

## 7. review / reject / rewrite 결과
실제 D 출력 32건에서 gate 가 **8건(25%)에 unsupported user-owned claim 검출**(원본 evidence_gate_violation 합 16).
- **reject**: REJECTED 8 / PASSED 24.
- **review**: needsHumanReview 8/32(나머지 24 통과).
- **rewrite**: 8건 적용.

## 8. D_raw vs E_review / E_reject / E_rewrite 주요 지표
| 항목 | D_raw(원본) | E_reject | E_review | E_rewrite |
| --- | --- | --- | --- | --- |
| evidence_gate_violation | 16(8 출력) | 0(8 출력 폐기) | 표면화(8 flag, 출력 유지) | **재-audit 잔여 0** |
| rewrite 재-audit 0 달성 | — | — | — | **8/8** |
| 계약 유지(json_ok+required_ok) | — | — | — | **8/8** |
| 점수/applyDecision mutation | — | 0 | 0 | **0** |
| parse_fail | 0 | — | — | — |

**주의(적대 검증 반영): 'rewrite 재-audit 0'은 같은 detector 자기검증이다.** 치환된 텍스트에서 `detect_unsupported_user_owned_claims` 가 더는 안 잡는다는 뜻이지, 독립적/의미적 안전 증명이 아니다. 또한 rewrite 가 문장을 **통째 치환**해 0 을 정보손실로 달성하는 경우도 포함된다(§9). **확실한 건 점수/applyDecision·계약 불변(이건 detector 무관, assert+재계산으로 검증)** — 안전 주장은 'detector-safe + score-preserving'까지로 한정한다.

## 9. rewrite 안전성 / 가독성 샘플 요약 (적대 검증 반영)
- **검출기 기준 안전(detector-safe): 8/8 재-audit 0** — 단 §8 주의대로 같은 detector 자기검증. 보유 단정이 "요구/학습·보완" 표현으로 바뀜. LLM judge 미사용(결정론 규칙).
- **그러나 rewrite 는 의미손실·malformed 결함이 있다(human review 로 확인):**
  - **정보손실**: `_rewrite_text` 가 위반 **문장을 통째 템플릿으로 치환**한다. 같은 문장의 정당한 정보(예 "5년 백엔드 경력과 함께 …")가 함께 삭제된다. 다중 unsupported 스킬이 한 문장에 있으면 **한 스킬만 템플릿으로 남고 나머지 claim 텍스트는 사라진다** — re-audit 0 이 부분적으로 '삭제'로 달성됨(안전 재서술이 아니라).
  - **malformed**: 이중 마침표(`…필요합니다.. 다음문장`), 라틴 토큰에 잘못된 조사(`Spark은(는)`). 계약은 통과하나 사용자 노출 부적합.
  - 샘플(truncated): `hard-research-007` after "MSA은(는) 공고가 요구하는 역량이므로 학습·보완이 필요합니다. 기술 스택…"(MSA 문장 통째 치환).
- 즉 rewrite 는 **'detector-safe + score-preserving'이지 'semantically/contractually 사용자에게 보여도 안전'은 아니다.** 정직한 라벨: **검출기-안전·점수보존, 단 의미손실·가독성 결함.**

## 10. contract / 점수 / 판단 불변 확인
- `evaluate(filteredOutput)` json_ok+required_ok **8/8**(rewrite 후에도 계약 유지). fitScore/applyDecision/matchedSkills/missingSkills mutation **0**(apply_gate assert + 캡처 `scoreDecisionMutation` 합 0). free-text(fitSummary/strengths)만 치환, 구조/스킬필드 불변.

## 11. parse fail 처리 확인
- parse_fail **0**(이번 D 출력 32건 전부 valid JSON). PARSE_FAIL 경로는 rewrite 로 은폐하지 않고 별도 status(테스트 7·R2e gate 로 보장).

## 12. 개인정보 / 보안 확인
- 케이스 synthetic(개인정보 0). gate 는 LLM/외부 API 호출 없음(전부 로컬 결정론, 모델 호출은 로컬 Ollama).
- **rawText 는 CareerTunerAI results 에만**(main repo 미커밋). 보고서엔 truncated 샘플(≤90자)만, 원문 복붙 금지.

## 13. backend / runtime / model 미변경 확인
- PR diff 는 `rag_poc/`(capture/summarize/test + apply_evidence_gate_filter 버그수정) + `reports/59` 뿐. backend·runtime prompt·기본 모델·LangChain/Spring AI 변경 0. R2d/R2e 모듈·`evaluate`·grounding 은 import 재사용(rewrite safe 문구 1줄 수정 제외 — possession 단어 제거). 채점/E1/E2 로직 불변.

## 14. 다음 단계 판단 (적대 검증 반영)
**판정(1: review-first gate 유망). rewrite 는 아직 미흡(redesign 필요).**
R2b~R2e 5연속 실험이 "모델 prompt 로는 RAG conflation 못 막음"을 보였고, R2f 는 **출력에 결정론 gate(특히 review/reject)를 두면 안전을 강제할 수 있음을 실측 확인**한다. 단 rewrite 는 과대평가하지 않는다:
- **review/reject 는 robust 한 레버**: 실 출력 32건 중 위반 8건을 100% 검출, needsHumanReview 표면화, **출력을 변형하지 않아 정보손실 0**, 점수/판단·계약 불변. 모델 변동성과 무관 — **이게 추천 gate**.
- **rewrite 는 detector-safe + score-preserving 까지만**(8/8 재-audit 0·계약·mutation 0). 그러나 (a) 재-audit 이 같은 detector 자기검증이고 (b) **문장 통째 치환이라 정당한 co-located 정보를 삭제**하며(다중 스킬 문장은 일부 claim 소멸) (c) 출력이 malformed(이중 마침표·라틴 조사). **사용자에게 보여도 안전한 단계가 아니다.**

→ 다음:
- **R3-pre(권장)**: **review-first(needsHumanReview) evidence gate 를 backend service layer 설계에 반영**(설계만, 구현 아님). reject 는 가용성 비용(25% 폐기) 커 review 우선. rewrite 는 fallback 후보에서 보류.
- **R2g**: rewrite **redesign** — 문장 통째 치환이 아니라 **위반 구(phrase) 단위 타깃 치환**(co-located 정보 보존) + 조사/구두점 정상화 + 다중 스킬 처리. 그 후에야 fallback 검토.
- RAG runtime 자동 통합은 계속 보류(모델 단계 효과 없음). **출력 안전은 E1 guard + review-first evidence gate 로 확보** — 5연속 실험의 결론.

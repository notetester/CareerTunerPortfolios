# AI 직무적합 — review-first Evidence Gate 설계 초안 (R3-pre)

> [!WARNING]
> 이 문서는 R3 이전 설계 초안이다. 최신 구현 상태는
> [`ml/career-strategy-llm/AI_ROADMAP_CHECKLIST.md`](../../../ml/career-strategy-llm/AI_ROADMAP_CHECKLIST.md) 및
> [`reports/61`](../../ai-reports/areas/c-career-strategy/reports/61_rag_r3_review_first_gate_implementation.md)~
> [`reports/65`](../../ai-reports/areas/c-career-strategy/reports/65_r3_evidence_gate_dev_integration_check.md)를 기준으로 확인한다.
> PR #175 이후 `userEvidence` 는 `profileSkills + profileCertificates` 로 고정되었고, AI 파생 `matchedSkills` 는 보유 근거로 신뢰하지 않는다.

> **상태: 설계 초안(design only). 구현 아님.** 이 문서는 C 자체 LLM RAG 실험(R2b~R2f, `ml/career-strategy-llm/reports/54~59`)의
> 결론을 backend service layer 안전장치로 어떻게 가져올지 제안한다. **Spring Boot 코드·runtime prompt·기본 모델·
> API 동작은 변경하지 않는다.** 실제 클래스 생성/수정은 backend owner(D/F) 리뷰·합의 후 별도 PR 에서.
> 실험 결론·판단은 [reports/60](../../ai-reports/areas/c-career-strategy/reports/60_rag_r3_pre_backend_gate_design.md).

## 1. 목적
AI 직무적합 설명 출력이 **지원자가 보유하지 않은 역량을 '보유'로 단정**(grounding conflation)하는 위험을, 모델을
바꾸지 않고 **출력 후 결정론 검사 → 검토 상태 표시(review-first)** 로 완화한다. R2f 실측에서 review/reject gate 는
출력을 변형하지 않고 위험 claim 을 안정적으로 잡았다(정보손실 0, 점수/판단·계약 불변).

## 2. 적용 범위
- 대상: `fitanalysis` 도메인의 AI 설명 결과(`FitAnalysisAiResult` 의 free-text: fitSummary/strengths 등).
- 위치: model 호출 + 기존 grounding guard **이후**의 후처리(post-process) 안전층.
- 형태: 응답 envelope 에 `safety`(gateStatus/needsHumanReview/gateReasons) 부가. 점수/판단 미변경.

## 3. 비목표 (non-goals)
- **RAG runtime 자동 통합 아님** — production runtime prompt 에 retrievedContext 를 자동 주입하지 않는다(R2b~R2d 결론: grounding 악화).
- **rewrite 자동 사용자 노출 아님** — R2f 에서 rewrite 는 의미손실·malformed 결함(R2g redesign 전 미사용).
- 기존 OSS grounding guard(E1, retry→fallback) 대체 아님 — 그 위에 얹는 soft review 층.
- 점수/applyDecision 변경, contract field(matchedSkills/missingSkills) 구조 변경 아님.
- 이 문서로 backend 코드를 바꾸지 않음(설계만).

## 4. evidence source 구분
RAG 를 끄므로 runtime 의 근거는 **모델 입력에 이미 있는 값**으로 한정된다:
| source | 의미 | user-owned 근거? |
| --- | --- | --- |
| userEvidence | 지원자 실제 보유 — `matchedSkills` + (있으면)프로필 보유 스킬/자격 | **예** |
| jobRequirements | 공고 요구 — `requiredSkills`/`missingSkills` | 아니오 |
| catalogFacts | 자격/기술 정의(현 runtime 미주입, RAG 도입 시에만) | 아니오 |
| companyContext | 회사/직무 맥락(현 runtime 미주입) | 아니오 |

현 단계(RAG off)에서 evidence gate 는 사실상 **'보유 주장 스킬이 matchedSkills(=userEvidence)에 있는가'** 검사로,
기존 grounding guard 의 일반화다.

## 5. unsupported user-owned claim 정의
출력 free-text(fitSummary/strengths)에서 어떤 스킬을 **'보유'로 서술**(보유/갖춤/강점 류 표현 + 결핍/부정 표현 없음,
기존 `OssFitAnalysisAiService` E1 휴리스틱 재사용)했는데, 그 스킬이 **userEvidence(matchedSkills/보유 자격)에 없으면**
unsupported user-owned claim 으로 본다. 출처에 따라 `requirement_as_owned`/`catalog_as_owned`/`unsupported` 로 분류.

## 6. gateStatus 정의
| gateStatus | 조건 | 동작 |
| --- | --- | --- |
| `PASSED` | unsupported claim 없음 | 정상 노출 |
| `REVIEW_REQUIRED` | unsupported user-owned claim 존재(warning 급) | 자동 확정 노출 금지, 검토 플래그 |
| `REJECTED` | 치명(parse fail · score/applyDecision mutation 시도 · critical violation) | 응답 폐기/재생성 경로 |

- review-first 원칙: 대부분의 unsupported claim 은 **REVIEW_REQUIRED**(출력 유지 + 플래그). REJECTED 는 내부 안전 모드(폐기/재생성).
- gate 는 **결과(점수/판단)를 바꾸지 않고** 노출/검토 상태만 결정한다.

## 7. needsHumanReview 정책
- `REVIEW_REQUIRED`/`REJECTED` → `needsHumanReview=true`.
- review 비율이 높을 수 있으므로(R2f 25%) gateReason 에 **severity**(`warning`/`critical`)를 둬, 운영 정책상
  warning 은 사용자에 '검토중/보완필요' 안내, critical 은 관리자 검토/재생성으로 라우팅 가능하게 한다(임계는 다중 run 후 확정).
- 기존 E1 grounding guard(hard retry→fallback)는 그대로 — evidence gate 는 그 **이후 잔여 위험의 soft 층**이라 이중 안전.

## 8. response envelope 초안
`ApiResponse<T>`(record `success/code/message/data`)의 data 에 `safety` 부가(또는 Fit 응답 DTO 확장). 예:
```json
{
  "success": true, "code": "OK",
  "data": {
    "fitResult": { "fitScore": 64, "applyDecision": "HOLD", "fitSummary": "...", "strengths": ["..."] },
    "safety": {
      "gateStatus": "REVIEW_REQUIRED",
      "needsHumanReview": true,
      "gateReasons": [
        { "type": "requirement_as_owned", "claim": "Spark", "reason": "공고 요구 역량 — userEvidence 미지원", "severity": "warning" }
      ]
    },
    "metadata": { "model": "careertuner-c-career-strategy-3b", "evidenceGateVersion": "r2f-review-first", "ragRuntimeEnabled": false }
  }
}
```
- `fitScore`/`applyDecision` 은 gate 가 **변경하지 않음**. `safety` 는 노출/검토 상태만.
- `gateReasons.claim` 은 **스킬명/축약**만(개인정보·원문 prompt 미포함).

## 9. service layer 위치 후보
실제 구조 기준 제안(구현 아님):
- **신규 `EvidenceGateService`**(가칭, `fitanalysis` 하위) — 입력(matchedSkills 등) + `FitAnalysisAiResult` → gate 결과. 순수 결정론, 외부 호출 없음.
- 호출 지점: **`FitAnalysisServiceImpl`** 가 `FitAnalysisAiService.generate(...)` 결과를 받은 직후. 기존 `OssFitAnalysisAiService` 의 hard grounding guard 는 AI service 내부에 유지(retry→fallback), evidence gate 는 **그 이후 응답 조립 단계**.
- 노출: Fit 응답 DTO(`FitAnalysisApplicationResponse`/`FitAnalysisDetailResponse`)에 `safety` 필드 추가 또는 별도 envelope 확장.
- E1 guard 와 **독립**: 둘은 별개 책임(E1=hard fallback, gate=soft review)으로 서로를 약화하지 않는다.

## 10. 테스트 전략 (설계 — 구현 시)
- supported user-owned claim(matchedSkills) → `PASSED`.
- catalog fact / job requirement as user-owned → `REVIEW_REQUIRED`.
- score/applyDecision mutation 시도 → `REJECTED`(gate 가 점수/판단 못 바꿈을 단언).
- parse fail → `REJECTED`(rewrite 로 은폐 금지).
- E1 grounding guard 와 evidence gate 가 **독립 동작**(한쪽 변경이 다른 쪽 미영향).
- `needsHumanReview` 가 envelope 에 노출.
- raw prompt/retrievedContext 는 로그/응답에 미포함.
- `gateReasons` 는 개인정보 없이 축약 저장.

## 11. UI / 관리자 표시 고려사항
- 사용자: `REVIEW_REQUIRED` 면 결과를 '확정'으로 보여주지 않고 '검토중/보완 필요' 톤으로 표시(자동 확정 답변 금지).
- 관리자: `safety`/`gateReasons`/severity 를 admin 화면(분석 run/AI usage)에서 확인·재생성 트리거 가능하게(설계 후보, admin owner 합의).

## 12. 운영 로그 / 감사 로그 고려사항
- gate 결정(gateStatus/severity/reason type)을 구조화 로그로(원문 출력·prompt·개인정보 제외).
- 기존 AI usage/analysis run 로그(admin/aiusage·analytics)와 정합 — evidenceGateVersion 으로 정책 버전 추적.
- review/reject 비율 모니터링으로 임계·UX 부담 관찰.

## 13. rollout plan (설계)
1. shadow: gate 를 계산만 하고 노출/분기 안 함(로그로 review/reject 비율·오탐 관찰).
2. soft: `REVIEW_REQUIRED` 를 사용자 톤(검토중)에만 반영, 차단 없음.
3. enforce: critical 은 관리자 검토/재생성 라우팅. 단계별 feature flag.
- 각 단계 backend owner 합의 + 메트릭 게이트.

## 14. rollback plan (설계)
- feature flag off → gate 미적용(기존 경로: no-context 3B LoRA + E1 grounding guard 만). gate 는 **추가층**이라 끄면 기존 동작으로 즉시 복귀.
- envelope `safety` 는 옵셔널 필드라 미산출 시 프런트는 기존대로 동작(하위호환).
- evidenceGateVersion 으로 정책 롤백/재현.

---
**요약:** RAG runtime 자동 통합은 보류, rewrite 는 R2g 까지 미사용, **review-first evidence gate 를 기존 no-context 경로 + E1 guard 위의 추가 안전층으로 설계**한다. 구현은 backend owner 리뷰·합의 후 별도 PR.

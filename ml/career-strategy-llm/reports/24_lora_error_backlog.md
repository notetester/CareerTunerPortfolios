# LoRA 약점 backlog — 재학습 전 validator/retry/prompt로 막을 수 있나

> reports/21에서 드러난 LoRA 슬립을 원문 근거로 정리. **재학습은 아직 안 한다** — 먼저 validator/retry/prompt 수준 차단 가능성을 본다.
> ★ 정정: 1차 보고의 "즉시 지원(HOLD 톤 슬립)"은 **실제 LoRA 오류가 아니라 하니스 오탐**(부정문맥)으로 재분류(E3).

## E1. 자기모순 — strengths(보유) ↔ risks(부재)  [REAL, priority HIGH]
```text
caseId: case-it-data-hold-001   run: 0   errorType: SELF_CONTRADICTION / GROUNDING
raw:
  strengths: "우대 역량인 Spark와 TensorFlow를 갖추고 있어 ... 추가 기여 가능"   ← 보유라고 서술
  risks:     "우대 역량인 Spark와 TensorFlow도 부재해 ..."                      ← 부재라고 서술
실제(규칙엔진): missingPreferredSkills=[Spark, TensorFlow] → 둘 다 '부재'가 정답.
```
- why: 한 응답 안에서 같은 스킬을 보유/부재로 동시 서술 → 신뢰 훼손.
- fixCandidate: **validator**(missing* 에 있는 스킬이 strengths 에 '보유/갖추고' 형태로 등장하면 reject→retry) ≪ prompt(강조) < data. 점수/판단은 규칙엔진이라 무해하지만 설명 신뢰엔 직접.
- 코드 위치 후보: `OssFitAnalysisAiService` 병합 직후 교차검증(strengths ∩ missing 금지).

## E2. 입력 밖 고유명사 날조  [REAL, priority MEDIUM]
```text
caseId: case-nonit-marketing-apply-001   run: 0   errorType: FABRICATED_ENTITY
raw: strategyActions: "CRM 시스템(CRM465, HubSpot 등) ..."   ← 'CRM465'는 존재하지 않는 제품명
```
- why: 입력에 없는 구체 고유명사(제품/툴) 날조 — 환각. HubSpot은 실재하나 입력 밖.
- fixCandidate: **prompt**(입력 밖 회사/제품/툴 고유명사 생성 금지 강조) + **validator**(고유명사 화이트리스트 또는 입력 토큰 외 영문/숫자혼합 토큰 경고). 완전차단은 어려움 → 우선 prompt+경고.

## E3. (정정) "즉시 지원" = 하니스 오탐, LoRA 오류 아님  [EVAL FIX, not model]
```text
caseId: case-it-clear-gap-001   run: 2   하니스판정: FORBIDDEN_CLAIM
raw 원문: "... 신입 포지션임을 고려하면 즉시 지원하기보다는 관련 역량을 집중 ..."
```
- 실제로는 **올바른 HOLD 조언**("즉시 지원하기보다는"=즉시 지원하지 말라). substring "즉시 지원"이 부정문맥을 오탐.
- fixCandidate: **eval 정정** — 골든셋 forbiddenClaims 의 bare "즉시 지원"→"즉시 지원하세요"(구체 권유형)로 교체(이 PR 반영). 추가로 하니스에 negation-aware 검사(후속).
- 참고: base의 `HALLUCINATED_SKILL`(머신 러닝 vs 머신러닝 띄어쓰기)도 같은 류의 **하니스 오탐** → allowedSkills 정규화(공백/변형) 후속.

## E4. timeout (콜드/경합)  [INFRA, priority HIGH(데모)]
```text
caseId: case-it-backend-apply-001 / case-it-frontend-complement-001   run: 0   errorType: ERROR_TimeoutError(180s)
- 같은 케이스 run1/run2는 성공(26~30s). 즉 첫 호출(콜드 로드/경합)만 타임아웃.
```
- why: 모델 내용 문제 아님(인프라/로드). 데모 첫 호출 리스크.
- fixCandidate: **retry**(백엔드엔 #105로 이미 적용) + **격리/워밍업**(reports/22, 데모 전 단일 모델 로드+1회 워밍업) + **pairwise selected-run**(이 PR: 성공 run 선택→케이스 1·2 복원).

## 종합 판단
- **재학습 불필요(현재):** E1(validator 교차검증)·E2(prompt+경고)·E4(retry+워밍업)·E3(eval 정정)로 대응 가능.
- 재학습/데이터 정제는 골든셋 40~60 확대 + 블라인드 재판정으로 신호를 더 모은 뒤 결정.
- 우선순위: **E1(validator) ≥ E4(워밍업/격리) > E2(prompt) > E3(eval, 이 PR 일부 반영)**.

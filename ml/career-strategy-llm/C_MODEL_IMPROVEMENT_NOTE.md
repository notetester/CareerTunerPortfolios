# C 자체 모델 개선 노트 — 재학습 판정 + 게이트/출력/신뢰도 (2026-07-07)

자체 모델 우선(도입안 §1인1모델·§목적="직접 만든 증거", 외부 LLM 은 시연 안전망일 뿐 해법 아님).
개선은 전부 **자체 3B + 서버 검증** 안에서. 최상위 LLM 판정단(Claude+Codex)은 런타임이 아니라
**오프라인 측정·판정 도구**로만 쓴다(도입안이 허용하는 선생/판정자 역할).

## 1. 재학습 판정: 보류 (근거 있는 결정)

"게이트도 만들고 7B 비교도 했는데 재학습 얘기가 없던 이유"를 이력으로 규명한 결과, 재학습은
누락이 아니라 매 국면 명시적으로 게이트된 결정이다.

- **결정적 반증**: SFT 가 conflation 을 오히려 악화시킨다 — reports/36 golden36: LoRA E1 grounding
  위반 **0.194 > base 0.139**. 파인튜닝이 더 유창·확신적으로 만들어 "부족한 in-scope 역량을
  strengths/fitSummary 에서 보유로 서술"하는 빈도를 높였다. 같은 distillation 데이터로 재학습하면 더 나빠질 개연.
- **한계효용 구조적으로 낮음**: 뉴로-심볼릭상 점수/판단은 규칙엔진 소유(모델 무관), 설명문 위험은
  E1 hard guard + R3 gate 가 흡수. A-only 120관측 TRUE conflation 3건도 전부 E1/R3 포착 유형이고
  실경로 화면 노출 0(E2E reports/83).
- **선행 데이터 부재**: conflation 을 줄이려면 "요구/부족 역량을 보유로 쓰지 말라"는 하드네거티브 대조쌍이
  필요한데 그 학습셋이 없고, 어떤 패턴이 잦은지 정할 운영 gate-reason 데이터도 미축적(#229 관측 인프라만 구축).
- **올바른 순서**(reports/86): 운영 gate 데이터 축적 → FP triage → 대조 학습셋 큐레이션 → 재학습. 1단계 미비.

→ **재학습은 안전지향 보류.** 트리거: 운영 gate 데이터로 특정 직군/패턴 conflation 이 계층 방어로도 반복 미검출일 때.

## 2. 검증된 게이트 약점 (측정 우선의 근거)

라이브 실측에서 나온 유일한 진짜 R3 실패(EA-GV2-109)를 코드로 규명:

- R3(및 E1)의 텍스트 탐지는 **문장 단위 "보유 표현 有 + 결핍 표현 無"**(`EvidenceGateService` POSSESSION/LACK).
  결핍 억제는 **FP 방지 목적의 의도된 설계**다.
- 109 는 그 tradeoff 의 FN: "NestJS와 Git가... **두 가지 모두 보유하고 있어**... 우대 스킬이 **없어**..."에서
  같은 문장의 "없어"(다른 스킬 결핍)가 NestJS 보유 단정을 억제 → 미검출. 구조필드(conditionMatrix)는 NestJS UNMET 로 맞음(자유텍스트만 자기모순).
- **단순 수정은 역효과**: 절 분할은 "두 가지 모두" 대용어 참조를 끊어 다시 놓치고, 근접성 완화는
  "Java 는 보유하지만 Kafka 는 필수" 류를 오탐. reports 가 이미 이 어려움으로 review-first(오탐 허용)를 택함.

## 3. 왜 "측정 먼저"인가 (역효과 검증의 전제)

외부 캡처 재현율 측정은 **confound 로 불가**: R3 는 내부 `FitAnalysisAiResult` 를 감사하는데 API 응답은
조립 후 뷰라 텍스트가 다르다(GATE_ADVERSARIAL_LIVE_RESULT.md §정정). **게이트 FP율을 모른 채 heuristic·
confidence 를 바꾸면 역효과를 검증할 수 없다.** 그래서 계측·특성화가 선행.

## 4. 지금 한 것 (안전한 토대, 동작 무변경)

- **R3 계측**: `EvidenceGateService` 에 감사추적 debug 로그(logger `careertuner.evidencegate.audit`) 추가 —
  결정별로 R3 가 실제 감사한 텍스트·탐지대상·판정을 남긴다. DEBUG 시에만 동작, 판정값 불변.
- **특성화 테스트 2종**(EvidenceGateServiceTest): 109형 FN(같은 문장 LACK 억제 → PASSED)과 억제 격리
  (혼합 LACK 제거 시 → REVIEW)를 못박는 **회귀 앵커**. 향후 heuristic 개선의 역효과(FP 증가·FN 잔존) 검증 기준.

## 5. 다음 안전 단계 (순서·검증 포함)

1. **베이스라인 측정**: audit 로그 DEBUG + 적대 픽스처 실행 → R3 가 실제 감사한 텍스트를 판정단(Claude+Codex)이
   대조 → R3 의 실제 FP/FN 정량화. (외부 캡처가 아닌 R3 감사입력 기준이라 confound 해소.)
2. **heuristic 개선(측정 후에만)**: 내부 일관성 교차검(자유텍스트 보유단정 vs 구조 결정) 등 후보를 특성화
   테스트 + 베이스라인으로 before/after 검증, **FP 증가 없을 때만 채택**.
3. **신뢰도 통합(④)**: R3 gate 신호를 사용자 노출 신뢰도(`FitAnalysisConfidence`, 현재 입력완성도 전용·관리자만
   게이트 확인)에 반영 — 단 게이트 FP율 측정 후 결정(FP 높으면 불필요한 저신뢰 경고 = 역효과).
4. **출력 rewrite 자동화는 금지**(reports/58·59 R2g 보류: 문장 통째 치환이 정당 정보 삭제·malformed). review-first 유지.

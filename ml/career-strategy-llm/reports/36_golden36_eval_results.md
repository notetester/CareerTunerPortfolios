# 확장 골든셋(36) 평가 결과 — LoRA vs base + 하니스 오탐 정정 (2026-06-23)

> `eval_golden_set` job(작업 큐 자동화, "작업 진행" 1회)으로 36케이스 × 3 = **108 run/모델** 측정. dev `f82adb4`.
> raw 는 artifact repo(`CareerTunerAI` `results/2026-06-23-golden-set-001/`, push `629bdea`). 여기엔 요약·해석.

## 1. 결과 (★공백 정규화 정정 후, 동일 raw 재채점)
| 지표 | **LoRA**(careertuner-c-career-strategy-3b) | base(qwen2.5:3b-instruct) |
| --- | --- | --- |
| 계약 success | **0.944** (102/108) | 0.889 (96/108) |
| hallucination(범위밖 스킬) | **0.009** | 0.046 |
| **E1 grounding 위반**(부족 in-scope 보유 서술) | **0.194** | 0.139 |
| E2 high(가짜 제품 식별자) | 1 (`CRMOne`) | 0 |
| CJK 누출 | 0.019 (2) | 0.028 (3) |
| json_parse | 0.991 | 1.0 |
| timeout | 0 | 0 |

> 측정값(정정 전): LoRA success 0.935/halluc 0.019, base 0.861/halluc 0.102. 차이는 아래 §3 오탐 정정.

## 2. 해석 (정직하게)
- **파인튜닝이 '범위 밖 스킬 날조'를 줄인다 — LoRA hallucination 0.009 vs base 0.046(~5×).** 확장 골든셋의 grounding-bait·비IT 케이스가 base 의 날조 경향을 드러냈다. 계약 success 도 LoRA 우위(0.944 vs 0.889).
- **트레이드오프 — LoRA 가 E1 grounding 위반은 더 많다(0.194 vs 0.139).** LoRA 는 더 유창·확신적으로 서술해, 부족한 in-scope 역량을 strengths/fitSummary 에서 '보유'로 쓰는 빈도가 높다. **이게 바로 백엔드 E1 guard 가 런타임에서 잡는(retry→fallback) 유형** — 즉 측정이 guard 의 필요성을 정량 입증한다(라이브 회귀의 case2 폴백과 정합). 깔끔한 뉴로-심볼릭 서사: **유창한 생성기 + grounding guard**.
- **E2**: LoRA 1건 `CRMOne`(가짜 CRM 제품명, coinage 규칙이 포착) — 약 0.9% 희소. base 0건(대신 base 는 범위밖 '실재 스킬'을 더 날조).
- **CJK 누출**: 양쪽 다 소량 진짜 누출(`流程`·`上有優越性`·`实践`·`掌握`). 품질 backlog.

## 3. 측정 무결성 — 하니스 오탐 발견·정정
재채점 전 base HALLUC 8 / LoRA 1 중 일부가 **띄어쓰기 오탐**이었다(과거 #112 에서도 본 `머신 러닝`).
- `case-it-data-hold-001` allowed=`머신러닝`, 모델 출력 `머신 러닝` → base 3건 오탐.
- `case-nonit-retail-md-hold-001` allowed=`상품 소싱`, 모델 `상품소싱` → LoRA 1건 오탐.
- **수정**: HALLUCINATED_SKILL 비교를 **공백·대소문자 정규화**(`eval_fit_model.py`). 단위테스트 추가. 동일 raw 재채점으로 위 §1 정정 수치 산출(4090 재실행 불필요).
- 잔여 base bad_skills(`AFPK 자격증 취득`·`CRM 사용법`)은 in-scope 스킬(`AFPK 자격`/`CRM`)을 긴 구절로 쓴 **회색지대**, `코드리뷰`·`장애 대응 절차 작성`은 진짜 범위밖. 회색지대 감안해도 base 날조 > LoRA(≈0).
- 잔여 caveat: `MISSING_MUST_MENTION`(LoRA 3: `커뮤니케이션`·`CRM 마케팅`)은 substring 정확매칭이라 동의어/부분표기면 다소 엄격(정정 대상 아님, 해석 시 감안).

## 4. 결론
- **LoRA 유지 근거 강화**: 확장셋에서 파인튜닝의 가치(범위밖 날조↓ + 계약↑)가 12케이스 때보다 뚜렷.
- **E1 guard 정당화**: LoRA 의 grounding 위반 0.194 를 guard 가 런타임에 흡수 → 화면엔 안전.
- **측정 신뢰도**: 오탐을 찾아 고치고 동일 raw 로 재채점 = oversell 없는 정직한 수치.

## 5. 다음
- 골든셋 **60 확장(2차)** + 레인 C(SME) 사람검토(reports/34 계획).
- CJK 누출(양쪽 ~2~3%) 원인 점검은 별도 품질 backlog(재학습 범위, 지금은 관측).
- 정정된 하니스로 다음 평가부터 깨끗한 HALLUC 수치 자동 산출.

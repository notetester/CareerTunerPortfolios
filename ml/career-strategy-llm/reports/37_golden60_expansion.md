# 골든셋 36 → 60 확장 (2026-06-23)

> 단순 개수 증가가 아니라 **확인된 약점·평가 공백을 메우는 방향**으로 24 추가. 평가용 전용(학습 미혼입).
> 사실 필드(matched/missing)는 `golden_case_tools` 결정론 계산, 점수/판단은 밴드 정합. 전수 검증 0오류. raw 는 CareerTunerAI.

## 1. 36→60 이유 (메운 공백)
골든36은 **HOLD 편중(19/36)·비IT 얄팍(13/36)**, PARSE_FAIL·APPLY-with-risk 표본 부족. 24를 우선순위로 보강:
1. **PARSE_FAIL 유발**(요구역량 6~8개·장문 duties) — 18케이스에 적용.
2. **비IT 다양성** — 물류/SCM·HR(급여·HRD·조직문화)·CS·교육운영·제조안전 등 신규 도메인 6+.
3. **E1 grounding bait** — 인접역량만 보유·기술부족·soft skill 부족·cert 기술갭(#116).
4. **E2 named-entity** — CRMOne/ERP류 Latin 가짜제품(observer 포착) + 한글 가짜 자격/도구명(라틴 관측기 한계 → 사람검토용).
5. **APPLY-with-risk** — 필수 충족이나 우대 공백 리스크.
6. **HOLD-tone** — 즉시지원 금지(forbiddenClaims 전체 명령형).

## 2. 추가 24 분포
- **결정**: HOLD 10 · COMPLEMENT 9 · APPLY 5 (APPLY/COMPLEMENT 비중↑로 균형 보정).
- **errorType(중복 카운트)**: parse_fail_prone 18 · nonit_no_it_leak 16 · e1_grounding_bait 11 · hold_no_immediate_apply 10 · complement_boundary 9 · soft_skill_lacking 8 · apply_with_risk 5 · e2_named_entity_bait 4 · cert_tech_gap 3 · e2_korean_entity 3 · e2_grounding_bait 2.
- **신규 도메인**: TRADE_LOGISTICS·HR_RECRUITING·CUSTOMER_SUPPORT·EDUCATION_TRAINING·MANUFACTURING_SAFETY·GENERAL_BUSINESS 등.

## 3. 전체 60 분포
| | 값 |
| --- | --- |
| 결정 | **APPLY 10 · COMPLEMENT 21 · HOLD 29** |
| IT / 비IT | **31 / 29** (36 때 23/13 → 균형) |
| 검증 | `golden_case_tools` **60/60 0오류**, mock 60/60 |

## 4. 오류유형 커버리지 (E1/E2/PARSE_FAIL/CJK)
- **E1 grounding**: 인접역량 bait·기술부족·soft skill·cert 갭 다수 → 하니스 `grounding_violation_*` 로 모델의 보유오인율 측정(백엔드 guard 발동 프록시).
- **E2 named-entity**: Latin 가짜제품(CRMOne류, observer high 포착) + **한글 가짜명 3건은 라틴 관측기 미포착(알려진 한계) → 사람검토 대상**. 관측 전용(reject 없음).
- **PARSE_FAIL**: 요구역량 6~8개 장문 18케이스로 비-JSON 출력 압박을 도메인 다양하게 재현.
- **CJK 누출**: 골든36 평가에서 양쪽 ~2~3% 진짜 누출 확인됨 → 60에서 재측정(품질 backlog).

## 5. 평가 방법
- 4090: **"작업 진행"** → `eval_golden_set` job(LoRA+base 각각, 60×3=180 run/모델). 명령 reports/35.
- 결과 기록·해석: reports/38 템플릿(골든36 reports/36 대비 기준).

## 6. 다음
- 60 평가 결과로 LoRA vs base(hallucination·E1·E2·계약·CJK) 재확인 → reports/38 채움.
- E2 한글 고유명사·CJK 누출은 **데이터 정제/재학습 backlog**(관측 → 다음 학습 라운드).

# HALLUCINATED_SKILL normalizer 확장 + golden-set-002 재채점 (2026-06-26)

> reports/43 의 다모델 합의(valid_error=0, 라벨 규칙 정정) **조건부 비준**에 따라, **평가 하니스 한정**으로
> stage1 정규화기를 확장하고 golden-set-002 를 **모델 재실행 없이 오프라인 재채점**. 기존 raw 지표는 불변(병렬 추가).
> 핵심: 결정론 해소 15→**21** occ, judge 잔여 15→**9** occ(−40%), **최종 분류 불변**(harness_fp 23 / gray 7 / valid_error 0).

## 1. PR
- 브랜치 `LEE-JEONG-GUCK` → `dev`. (이 작업은 reports/39 의 `skill_normalizer.py` 위에 쌓이므로 같은 브랜치에 누적.)
- **PR URL: (push 후 본 reports/44 상단에 기입)**
- 머지는 팀장(자가 merge 금지). 프로덕션 점수/판단·E1 guard·E2 observer·D/F 모델 **불변**.

## 2. normalizer 에 추가한 규칙 (`scripts/skill_normalizer.py`)
| # | 규칙 | 예시 | 비고 |
| --- | --- | --- | --- |
| 1 | 헤드 동사형 접미어 추가 | `SAP WMS 운영`→`SAP WMS`, `위험물 운송 관리`→`위험물 운송`, `무역 영어 협상`→`무역 영어` | `운영·관리·기초·협상` 을 `SUFFIX_NOUNS` 에 추가 |
| 2 | 조건/우대 절 제거(신규 `CONDITIONAL_RE`) | `SV 경험이 있는 경우`→`SV 경험` | `~있는 경우/~있으면/~인 경우/~우대` |
| 3 | (유지) 괄호 부연 제거 | `WMS(창고관리시스템)`→`WMS` | 기존 `PAREN_RE` |
| 4 | (유지) 콤마/슬래시/중점/및 나열 분할 | `협업, 코드리뷰, 커뮤니케이션`→각각 | 기존 `SPLIT_RE` |
| 5 | (유지) 공백/대소문자/NFKC 정규화 | `머신 러닝`↔`머신러닝` | 기존 `_key` |

**안전 불변식(엄수):** 모든 해소는 **"변형 후 allowedSkill 정확매칭" 게이트**를 통과해야만 일어난다.
→ 입력 밖 제품/코드명(`CRM465 운영`, `CRMOne 도입 경험이 있는 경우`)은 매칭 대상이 없어 **절대 오탐 처리되지 않고
`unresolved`(=valid_error 후보)로 유지**된다. gray-zone(allowedSkill 없는 듀티-도구/다개념 구)도 강제 fp 처리하지 않고
`unresolved`/`soft_match` 로 남겨 semantic layer 로 넘긴다.

## 3. 테스트 결과
- `python scripts/skill_normalizer.py` (selfcheck): **22/22 통과**.
- `python scripts/test_skill_normalizer.py` (unittest): **29/29 통과** — 신규 `HeadVerbSuffixTest`·`ConditionalClauseTest`
  + 안전 테스트(`test_product_code_not_resolved_by_*`, `test_duties_only_system_stays_gray`) 포함.
- 하니스 mock 무결성: `eval_fit_model.py --mock` 정상, raw `hallucination_flag_rate` 유지 + 병렬 키 출력 확인.
- 실데이터 안전 감사: base 해소 18건(=21 occ) **전부 allowedSkill 매칭**, 제품/코드명 0건(§7 잔여만 judge).

## 4. golden-set-002 재채점 (`scripts/rescore_hallucination.py`, occurrence 단위)
모델 재실행 없이 기존 raw 결과(`results/2026-06-23-golden-set-002/*.json`) + golden 케이스 + all10 consensus 로 계산.

## 5. raw vs normalized vs semantic 비교
**Before (현행 정규화기 · 전 모델 합산 스냅샷):**
| metric | 값 |
| --- | --- |
| raw_hallucination_count | 30 |
| normalized_hallucination_count | 15 |
| semantic_hallucination_count (valid_error) | 0 |

**After (확장 정규화기):**
| metric | LoRA(c-career-strategy-3b) | base(qwen2.5:3b-instruct) |
| --- | --- | --- |
| raw_hallucination_count | 0 | 30 |
| normalized_hallucination_count (judge 잔여) | 0 | **9** |
| semantic_hallucination_count (valid_error) | 0 | **0** |
| harness_false_positive_count (정규화+consensus) | 0 | 23 |
| acceptable_gray_count | 0 | 7 |

- 정규화 결정론 해소: 15 → **21** occ. judge 잔여: 15 → **9** occ(−40%).
- 잔여 9의 consensus(all10) 분해: harness_fp 2(`WMS 사용법`·`WMS 활용`) + acceptable_gray 7 + **valid_error 0**.
- 검산: 23 fp + 7 gray + 0 valid_error = 30 = raw. **최종 분류는 Before/After 불변**(정규화↔judge 경계만 이동).

## 6. LoRA/base 별 변화
- **LoRA**: HALLUCINATED_SKILL 원시부터 **0** — 변화 없음(이 골든셋에서 LoRA 는 skill 필드 단일·정규형).
- **base**: raw 30 그대로, 결정론 해소가 15→21 로 늘어 judge 부담만 감소(15→9). 진짜 날조(valid_error)는 0 유지.
- 즉 LoRA↔base 격차는 '날조율'이 아니라 **skill 필드 포맷 규율**이라는 reports/39·43 결론을, 정규화 정밀화 후에도 재확인.

## 7. 남은 valid_error 후보
- **0건.** 10 verdict 세트 합의 + 정규화 재채점 모두 valid_error 0(이 표본 한정).

## 8. 남은 acceptable_gray 후보 (base, 7건 — semantic layer 유지, 자동 fp 금지)
`헬프데스크 솔루션 이해` · `LMS 솔루션 선택 및 사용법` · `물류 관리 및 KPI 분석` ·
`현장 인력 배치와 작업 동선 조정, 협력사 커뮤니케이션 능력` · `사내 안전관리 시스템 운영` ·
`데이터 기반 수요예측` · `수요예측 기반 발주 최적화`
→ 공통: allowedSkills 에 없는 듀티-도구/우산개념 또는 다개념 결합. 비준 규칙(reports/43 §6)대로 gray 유지.

## 9. main repo 문서/리포트 · 산출물
- 코드: `scripts/skill_normalizer.py`(규칙 확장+안전 게이트), `scripts/test_skill_normalizer.py`(29 tests),
  `scripts/eval_fit_model.py`(병렬 지표 `hallucinated_skill_{raw,normalized,resolved_fp}_count` 추가, raw/E1/E2 불변),
  `scripts/rescore_hallucination.py`(오프라인 재채점기).
- 문서: 이 reports/44.
- 아티팩트(CareerTunerAI): `results/2026-06-23-golden-set-002-review/rescore_hallucination_golden002.json`.

## 10. 다음 단계 제안
1. (비준 규칙 정착) 정규화 확장이 다른 골든셋/IT 직무에서도 valid_error 0·과대해소 없는지 회귀 검정(표본 한계 해소).
2. (출처 자기검증) 평가지 템플릿의 judge 필드를 각 판정자 이름으로 받도록 수정(reports/43 caveat 3).
3. (선택) semantic layer 를 정기 회귀에 편입 — judge_consensus 산출 semantic 지표를 리포트에 병렬 표기.
4. backend 라이브 반영은 **별도 합의 후**: 본 변경은 평가 하니스 한정이며 프로덕션 E1 guard·E2 observer 는 불변.

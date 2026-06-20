# C_FIT_EXPLAIN 데이터 품질 리포트 (Phase 1 / mixed 416건)

> 생성일: 2026-06-20 · 선생 모델: **sonnet** · task: `C_FIT_EXPLAIN`
> **mixed = IT/SW baseline 297(`*.it_mvp.*`) + 비IT 119(`*.nonit.*`)**. IT MVP 단독 리포트는 `02_dataset_quality_report.md`.

## 0. 직군 범위 정책

C 자체 LLM은 **특정 직군 전용이 아니라 범용 커리어 전략 설명 모델**이다(공고 조건 ↔ 프로필 역량/자격/경험 비교 설명).
Phase 1 MVP는 IT/SW 중심으로 검증하되, **비IT 직군(마케팅·영업·디자인·회계·인사·물류·CS) 샘플을 포함**해 데이터·프롬프트가 IT 전용으로 굳지 않게 한다. 비IT 정밀 자격증/RAG/점수정책은 **Phase 2 확장**.

## 1. 구성

| 셋 | 직군 | 생성 | 검증·필터 후 | 비고 |
| --- | --- | --- | --- | --- |
| IT/SW MVP | IT_SOFTWARE, DATA_AI | 300 | **297** | 환각 3 드롭(`02_` 리포트) |
| 비IT | MARKETING/SALES/DESIGN/FINANCE_ACCOUNTING/HR_ADMIN/MANUFACTURING_LOGISTICS/SERVICE_CS | 120 | **119** | 모순 1 드롭 |
| **mixed** | 9개 domainGroup | — | **416** | 병합 후 seedId 재부여(mix_*) |

## 2. 비IT 생성 방식 (범용화)

- 시드: `seed_profiles.py --preset nonit120` — 직군 쿼터(마케팅18/영업18/디자인18/회계18/인사16/물류16/CS16) + 판단 쿼터(APPLY40/COMP40/HOLD40) joint 거부 샘플링.
- `seed_profiles.py` 범용화: `domainGroup` 필드 + 비IT 7직군 카탈로그(역량/도구/자격). 필드명(requiredSkills 등)은 DTO 정합 위해 유지하되 값 의미를 "직무 역량/도구/자격/경험"으로 확장.
- teacher 프롬프트 범용화: "기술스택" → "직무 요구조건/핵심 역량/도구/자격/경험", "다른 직군(특히 IT) 전용 표현 금지", "learningTaskReasons.skill 은 구체 역량명(추상 표현 금지)".

## 3. 비IT 품질

- 스모크 10: 10/10 통과, 문제 0, **IT 누출(it_leak) 0**.
- 120 생성 → 자동검증 **119/120**(모순 1) → 필터 119. **it_leak 경고 0**(전 직군에서 IT 전용 표현 누출 없음).

## 4. mixed 최종 분포 (416)

| domainGroup | rows | | 지원판단 | rows |
| --- | --- | --- | --- | --- |
| IT_SOFTWARE | 192 | | APPLY | 137 |
| DATA_AI | 105 | | COMPLEMENT_BEFORE_APPLY | 140 |
| FINANCE_ACCOUNTING | 18 | | HOLD | 139 |
| SALES | 18 | | | |
| DESIGN | 18 | | **IT/비IT** | **297 / 119** |
| MARKETING | 17 | | it_ratio | 0.714 |
| HR_ADMIN | 16 | | nonit_ratio | 0.286 |
| MANUFACTURING_LOGISTICS | 16 | | fitScore avg/min/max | 63.6 / 2 / 96 |
| SERVICE_CS | 16 | | | |

## 5. mixed 검증 결과

| 항목 | 결과 |
| --- | --- |
| 총 row / 통과 | 416 / **416** (ok_rate 1.0) |
| messages 구조·필수 키 | 100% |
| **금지 키**(fitScore/score/applyDecision/decision) | **0건** |
| 규칙엔진 정합(matched/missing/decision) | 100% |
| 환각(스킬) | 0건(위반은 필터됨) |
| 모순 | 0건 |
| **IT 누출(it_leak, 비IT 119 대상)** | **0건** |

## 6. train/val split

- mixed 416 → **train 375 / val 41** (`train.mixed.jsonl` / `val.mixed.jsonl`).

## 7. 품질 이슈와 조치

| 셋 | 이슈 | 건수 | 조치 |
| --- | --- | --- | --- |
| IT | 환각(추상적 skill) | 3 | it_mvp 단계 드롭 |
| 비IT | 모순(부족역량을 강점서 언급) | 1 | 필터 드롭 |

teacher 프롬프트에 "skill 구체화" 제약을 추가해 IT 환각 유형(추상 skill)을 사전 억제. 비IT 모순 1건은 검증기 정밀화(학습 프레이밍 제외)에도 남은 실제 모순 → 드롭.

## 8. 산출물

```
data/seeds.fit_explain.it_mvp.300.jsonl / raw.*.it_mvp.300(.clean).json / dataset.*.it_mvp.300.jsonl   IT baseline
data/seeds.fit_explain.nonit.120.jsonl  / raw.*.nonit.120(.clean).json                                  비IT
data/raw.fit_explain.mixed.clean.json   / dataset.fit_explain.mixed.jsonl                                통합 raw/dataset
data/train.mixed.jsonl / val.mixed.jsonl                                                                 학습 입력(375/41)
data/validate.summary.{it_mvp.300.clean,nonit.120,mixed}.json                                            검증 요약
```
모두 `.gitignore` 추적 제외(재생성 가능). IT baseline 은 `*.it_mvp.*`로 보존되어 IT 단독 비교에도 사용 가능.

## 9. 한계

- 비IT 직군 자격증/역량 카탈로그는 MVP 수준(대표 항목). 정밀 자격증 매핑·RAG 근거·직군별 점수정책은 Phase 2.
- it_leak/환각/모순 검사는 구조화 필드+토큰 휴리스틱 → 자유문장의 미묘한 직군 부적합은 완전 탐지 못함. 학습 전 표본 사람 검수 권장.
- IT 297은 구버전 시드라 raw 의 `domainGroup` 을 병합 시 jobFamily 로 backfill(검증·통계는 정상).

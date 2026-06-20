# C_FIT_EXPLAIN 데이터 품질 리포트 (Phase 1 / IT/SW MVP 297건)

> 생성일: 2026-06-20 · 선생 모델: **sonnet** · task: `C_FIT_EXPLAIN`
> ⚠️ **이 리포트는 IT/SW 중심 baseline(`*.it_mvp.*`) 데이터다. 전 직군 대표 데이터가 아니다.**
> 비IT 직군을 포함한 통합(mixed) 데이터 품질은 `03_dataset_quality_report.mixed.md` 참고.
> **직군 범위 정책:** C 자체 LLM은 IT 전용이 아니라 **범용 커리어 전략 설명 모델**이다. Phase 1 MVP만 IT/SW 중심 검증이며, 비IT 직군 샘플을 포함해 IT 전용으로 굳지 않게 한다(비IT 정밀화는 Phase 2).
> 목적: Phase 1 검증용 MVP 데이터. 최종 아님 — 비IT 추가 후 mixed 로 확장.

## 1. 생성 방식 (뉴로-심볼릭)

```
seed_profiles.py --n 300 --balance        → seeds.fit_explain.300.jsonl (규칙엔진이 점수/판단 계산)
generate_dataset.workflow.js (sonnet)     → 시드 슬라이스를 배치(20×15)로 읽어 fit_explain 생성
   · fit_explain 스키마에 additionalProperties:false → 점수·판단 키 생성 구조적 차단
join_raw.py                               → raw.fit_explain.300.json [{seed, fit_explain}]
validate_dataset.py                       → 자동 검증(규칙엔진 오라클 재검증)
filter_dataset.py                         → raw.fit_explain.300.clean.json (실패/중복 제거)
assemble_dataset.py                       → dataset.fit_explain.300.jsonl (messages)
prepare_data.py                           → train.jsonl / val.jsonl
```

점수(fitScore)·지원판단(applyDecision)은 **시드(입력)에만** 존재하고, teacher 출력에는 설명만 둔다.

## 2. 스모크 테스트(10건)

- 10/10 통과, 금지키·환각·모순·규칙불일치 0. 분포 APPLY3/HOLD4/COMP3.
- → 300건 본생성 진행 결정.

## 3. 300건 생성 결과

- 워크플로우: sonnet 20배치(배치당 15) → fit_explain **300/300** 생성, join 300/300(미생성 0).
- 토큰: 약 580K(서브에이전트). 소요 약 8분.

## 4. 검증 결과

### 4.1 1차 검증 → 검증기 정밀화
1차 자동검증에서 300건 중 287 ok / 13 fail(모순 10, 환각 3). **모순 10건을 표본 점검한 결과 대부분 거짓 양성**이었다:
- 부족역량을 "보유"라 주장한 게 아니라 **학습/전환 프레이밍**("TypeScript 보유로 React **학습 시 빠르게 전환**")으로 언급 — 올바른 출력.
- **보유 자격증 인용**("**AWS 솔루션스 아키텍트 자격증**"으로 클라우드 이해 입증) — AWS 스킬 주장 아님.

→ 검증기를 정밀화했다(`validate_dataset.py`): 모순은 strength **항목 단위**로 보고, ① 학습 마커(학습/전환/습득/익히/토대/기반/빠르/수월/적응 등)가 있으면 제외, ② 부족역량이 보유 자격증 문자열에 포함되면 제외.

### 4.2 최종 검증(정밀화 후)
| 단계 | total | ok | fail | 비고 |
| --- | --- | --- | --- | --- |
| 원본 raw(300) | 300 | 297 | 3 | 환각(halluc_skill) 3건만 |
| 클린(297) | 297 | **297** | 0 | 전부 통과 |

검증 항목별(클린 297 기준):
| 항목 | 결과 |
| --- | --- |
| messages 구조 `[system,user,assistant]` | 100% |
| user 입력에 fitScore/applyDecision/matched/missing 존재 | 100% |
| assistant JSON parse(스키마 강제) | 100% |
| 필수 키(fitSummary/strengths/risks/strategyActions/learningTaskReasons) | 100% |
| **금지 키(fitScore/score/applyDecision/decision)** | **0건** |
| 규칙엔진 정합(matched/missing/decision 재계산 일치) | 100% |
| 환각(learningTaskReasons.skill 이 시드 역량 집합 내) | 100%(위반 3건 필터됨) |
| 모순(부족역량을 강점에서 보유 주장) | 0건 |

## 5. 데이터 분포 (최종 297)

| 축 | 분포 |
| --- | --- |
| **지원판단** | APPLY 97 · COMPLEMENT_BEFORE_APPLY 100 · HOLD 100 |
| **직군** | AI 54 · 백엔드 48 · 데이터 51 · 풀스택 50 · 인프라 48 · 프론트엔드 46 |
| **경력** | 신입/주니어/미들 고른 분포(시드 단계 87/108/105) |
| **fitScore** | 평균 63.0 · 최소 2 · 최대 96 |

(시드 300은 APPLY/COMP/HOLD=100/100/100 균형. 환각 3건이 모두 고적합 APPLY 시드라 APPLY만 97로 감소.)

## 6. 품질 이슈 샘플과 조치

드롭된 3건(환각, halluc_skill): 모두 **부족 필수역량이 없는 고적합(APPLY) 시드**라, teacher가 구체 스킬 대신 **추상적 성장영역**을 learningTaskReasons.skill 로 적었다.
| seedId | 문제 skill 값 | 조치 |
| --- | --- | --- |
| cseed_0080 | "보안 인증 처리" | 드롭(스킬명 비구체) |
| cseed_0106 | "MLOps/실험관리 도구" | 드롭 |
| cseed_0116 | "데이터 오케스트레이션 도구(예: Airflow)" | 드롭 |

조치: 필터에서 제거(297 유지). 필요 시 해당 시드만 재생성하거나, teacher 프롬프트에 "learningTaskReasons.skill 은 구체 기술명" 제약을 강화해 회수 가능.

## 7. 산출물

```
data/seeds.fit_explain.300.jsonl          균형 시드 300 (규칙엔진 사전계산 포함)
data/raw.fit_explain.300.json             teacher 원본 300
data/raw.fit_explain.300.clean.json       검증·필터 후 297
data/dataset.fit_explain.300.jsonl        messages 297 (C_FIT_EXPLAIN)
data/train.jsonl / val.jsonl              268 / 29
data/validate.summary.300(.clean).json    검증 요약
```
모두 `.gitignore`로 추적 제외(재생성 가능).

## 8. 확장 계획

- 워크플로우는 `--n`(args.n)만 바꾸면 1,000/3,000으로 확장(배치 수 자동 증가). 배치당 15 유지.
- **품질 검증 없이 대량 생성 금지**: 1,000건 생성 시에도 동일 validate→filter 파이프라인 통과 필수.
- C_FIT_EXPLAIN 최소 1,000건 목표. 4 task 전체 3,000~7,000건까지 확장 가능 구조.
- 증량은 **실패 유형 기반**(환각 줄이기 위한 프롬프트 강화 → 재생성)으로 판단. 30만 건은 현재 범위 아님.

## 9. 한계

- 환각/모순 검사는 **구조화 필드(스킬)** 중심의 휴리스틱이다. 자유문장(fitSummary 등)의 미묘한 사실 오류는 완전 탐지하지 못한다 → 학습 전 표본 사람 검수 권장.
- 규칙엔진은 MVP 임시 미러링. 백엔드 점수정책과 정합은 통합 단계에서 확정.

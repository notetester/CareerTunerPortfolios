# 모델 카드 — careertuner-c-career-strategy (작성 중)

> 운영안 13장 항목. Phase 진행에 따라 채운다. (현재 Phase 0: 스캐폴딩)

| 항목 | 내용 |
| --- | --- |
| 모델명 | `careertuner-c-career-strategy` (alias) · `-3b` / `-7b` 변형 |
| 담당자 | C 파트 |
| 담당 도메인 | 홈/스펙비교/취업분석/대시보드 + 커리어 전략 추천 |
| 직군 범위 | **범용**(IT 전용 아님). Phase 1 MVP는 IT/SW 중심 검증 + 비IT(마케팅·영업·디자인·회계·인사·물류·CS) 샘플 포함. 비IT 정밀 자격증/RAG/점수정책은 Phase 2 확장 |
| base model | Phase 1 `Qwen/Qwen2.5-3B-Instruct` (Apache 2.0) · 비교 `Qwen/Qwen2.5-7B-Instruct` |
| 학습 방식 | QLoRA(4bit NF4) SFT, LoRA r=16/α=32, 3 epoch (D 검증 설정 재사용) |
| 학습 데이터 출처 | 합성 distillation(Claude 선생) + 공개데이터(NCS/Q-net) grounding. 실사용 데이터 미사용 |
| **기본 학습 데이터셋** | **mixed**(IT 297 + 비IT 119 = 416 → `train.mixed.jsonl` 375 / `val.mixed.jsonl` 41). `*.it_mvp.*`=비교/보존용 |
| 주요 task | `C_FIT_EXPLAIN`(MVP) / `C_STRATEGY` / `C_LEARNING_ROADMAP` / `C_TREND_SUMMARY` |
| 입력 형식 | 구조화 텍스트: 공고 요구 + 프로필 + **규칙엔진 사전계산값(fitScore/판단/matched/missing)** |
| 출력 형식 | 설명 JSON (fitSummary/strengths/risks/strategyActions/learningTaskReasons). **점수 미포함** |
| 검증 방식 | JSON 스키마 + 원문근거(입력 외 사실 추가 금지) + 점수/판단 불변 검증 + E1 grounding hard guard + R3 review-first evidence gate |
| fallback | OSS → Claude(Haiku) → OpenAI → Mock (백엔드 `FallbackFitAnalysisAiService` @Primary. 2026-07-02 표기 정정 — 기존 "OSS → OpenAI → Mock" 은 Claude 단계 추가 이전 표기) |
| 알려진 한계 | 소형(3B) 모델 JSON 깨짐/긴 출력 시 Ollama 500/한국어 토큰 누출 가능 → format=json + 폴백으로 방어. R3 이후에도 alias map 밖 표면형과 운영 false-positive/false-negative feedback loop는 별도 과제 |
| 라이선스 주의 | base Qwen2.5 Apache 2.0. 공개데이터는 공공누리 유형 확인(제1유형+출처표시면 상업 OK) |
| 실제 서비스 연결 경로 | `fitanalysis/ai` `FallbackFitAnalysisAiService`(@Primary) → `OssFitAnalysisAiService`(뉴로-심볼릭) → `CareerAnalysisOssClient` → Ollama `careertuner-c-career-strategy-3b`. provider=oss+base-url 설정 시 활성(원격경로 미확정) |
| 마지막 평가 일자 | 2026-06-30 R3 evidence gate dev 통합·자동 검증 확인([reports/65](../../docs/ai-reports/areas/c-career-strategy/reports/65_r3_evidence_gate_dev_integration_check.md), [reports/66](../../docs/ai-reports/areas/c-career-strategy/reports/66_r3_auto_verification_and_ai_checklist.md)). 7B smoke는 2026-06-26 [reports/49](../../docs/ai-reports/areas/c-career-strategy/reports/49_7b_smoke_benchmark_result.md) 기준 |

## 뉴로-심볼릭 설계 요지

점수·판단(`fitScore`/`applyDecision`/`matchedSkills`/`missing*`)은 **서버 규칙엔진이 결정론적으로 계산**하고,
LLM은 그 값을 입력으로 받아 **한국어 설명/추천만** 생성한다. → 점수 재현성·감사가능성 확보, 환각이 설명 문장에 국한.

> **정직성 주석 (2026-07-02, 해소됨):** 이 비대칭은 PR #211 로 해소됐다 — 폴백 경로(Claude/OpenAI)도 규칙엔진
> skeleton + 설명 전용 생성으로 통일되어, 판단값 소유는 전 provider 규칙엔진이다([AIDocs report 78](../../docs/ai-reports/areas/c-career-strategy/reports/78_provider_judgment_ownership_unification.md)).
> 아래는 해소 전 기록이다: 위 원칙은 과거 **OSS 경로에만** 구현되어 있었다. fallback 경로
> (`AnthropicFitAnalysisAiService`/`OpenAiFitAnalysisAiService`)는 일반 prompt 로 **LLM 이 점수/판단을 직접
> 생성**하며(비대칭), R3 gate 가 provider 무관하게 후처리로 커버한다. 판단값 소유를 전 provider 규칙엔진으로
> 통일하는 작업이 진행 중이다([AIDocs report 77](../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md) §7).

## Production safety layer

현재 production path 는 모델 출력을 그대로 신뢰하지 않는다. 실제 적합도 분석 설명 경로는 다음 계층으로 본다.

```text
3B LoRA / provider output
→ E1 grounding hard guard
→ R3 review-first evidence gate
→ admin review state / safety response
```

- **E1 grounding hard guard**: 명백한 grounding violation 을 hard guard 로 다룬다. OSS 설명 생성에서 위반을 감지하면 retry/fallback 경로로 이동해 사용자 화면이 깨지지 않도록 한다.
- **R3 review-first evidence gate**: 모델 출력 이후 결정론 검사로 `PASSED` / `REVIEW_REQUIRED` / `REJECTED` review state 를 남긴다. gate 는 설명을 자동 교정하지 않고, 관리자 검토 상태와 `safety` 응답으로 분리한다.
- **불변 필드**: R3는 `fitScore`, `applyDecision`, `matchedSkills`, `missingSkills` 를 변경하지 않는다. 점수와 지원판단은 계속 서버 규칙엔진의 산출물이다.
- **근거 분리**: `userEvidence` 는 `profileSkills + profileCertificates` 기준이다. `matchedSkills` 는 AI/규칙 산출의 derived evidence 이며 사용자 보유 근거로 신뢰하지 않는다.
- **검출 범위**: gate 는 사용자에게 표시되는 핵심 설명 필드에서 unsupported possession claim 을 감지하고, gate reason/evidence source 를 감사 가능하게 남긴다.
- **alias/mention 정책**: `SkillAliasNormalizer` 는 curated alias map 기반이다. substring/fuzzy matching 은 사용하지 않는다. mention-boundary 보강으로 `Next.js`/`React Native`/`Spring Boot`/`PostgreSQL` 류 false-positive 를 줄였다.

## Current deployment decision

- **3B LoRA 유지**: `careertuner-c-career-strategy-3b` 계열을 현재 기준 모델로 유지한다.
- **7B base 전환 보류**: [reports/49](../../docs/ai-reports/areas/c-career-strategy/reports/49_7b_smoke_benchmark_result.md) smoke 결과만으로는 3B LoRA 교체 근거가 부족하다. 7B base 는 latency/VRAM 비용이 높고 golden60 기준 명확한 우위를 보이지 않았다.
- **retrieved-context/evidence-bucket 자동 주입 보류**: R2b~R2f 실측에서 retrievedContext 주입은 net wash 이거나 grounding conflation 을 늘렸다. production prompt 에 자동 연결하지 않는다. **true external retrieval RAG(런타임 벡터검색·웹·카탈로그 조회)는 아직 구현·평가되지 않았다** — 지금까지의 실험은 전부 evidence-bucket prompt augmentation 이다([AIDocs report 77](../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md)).
- **rewrite 자동 노출 보류**: R2f rewrite 는 detector-safe 와 score-preserving 은 보였지만 의미손실과 malformed 문제가 있어 사용자 자동 노출 대상이 아니다.
- **R3 safety layer 적용**: 현재 운영 안전성은 prompt 만이 아니라 E1 hard guard + R3 review-first gate 의 결정론 계층으로 보강한다.

## Known limitations after R3

- curated alias map 에 없는 표면형은 false-positive 로 REVIEW_REQUIRED 처리될 수 있다.
- gate reason 이 운영자에게 과도하거나 중복으로 느껴질 수 있다.
- DB fixture 기반 mapper 통합 테스트는 아직 없다. 현재 일부 관리자 SQL 검증은 정적 XML 테스트로 고정되어 있다.
- 운영 false-positive / false-negative feedback loop 는 아직 없다.
- A-only baseline(120관측)에서 모델 단독 출력의 진짜 보유단정 3건(2.5%)이 확인됐다 — E1/R3 계층 없이 모델 출력을 신뢰하면 안 되는 정량 근거([AIDocs report 80](../../docs/ai-reports/areas/c-career-strategy/reports/80_a_only_baseline_repeat2_judge.md)).
- RAG 재도입 조건은 별도 hard-case benchmark 재구성이 필요하다([reports/67](../../docs/ai-reports/areas/c-career-strategy/reports/67_rag_reentry_criteria_and_hardcase_benchmark.md)).

## Re-evaluation triggers

- 운영 `REVIEW_REQUIRED` reason 이 과도하게 쌓일 때.
- alias 후보가 반복적으로 발생할 때.
- 특정 직군/기술군에서 false-positive 가 반복될 때.
- 3B LoRA 출력 품질이 E1 + R3 gate 로도 감당되지 않을 때.
- 7B LoRA 또는 새로운 base model 이 golden set 에서 latency/VRAM 비용을 감수할 만큼 명확히 우위일 때.
- RAG hard-case benchmark 에서 unsupported possession claim 감소가 검증될 때.

## 학습 실측 (3B, 2026-06-21)

| 항목 | 값 |
| --- | --- |
| base / 방식 | Qwen/Qwen2.5-3B-Instruct · QLoRA 4bit, LoRA r=16 / α=32, epochs 3 |
| 데이터 | mixed 416 (train 375 / val 41) |
| 학습 시각 | 2026-06-21 01:14:05 ~ 01:26:25 (약 12분, 공유 RTX 4090) |
| 최종 train_loss | **0.6267** (마지막 logged 0.4601 @epoch 2.9813) |
| 최종 eval_loss | **0.5154562592506409** @epoch 3 (eval token accuracy 0.864) |
| eval_loss 추이 | 0.589 → 0.529 → 0.516 (epoch 1→2→3, **매 epoch 개선**) |
| merge | 성공 |
| 산출물 | LoRA adapter `adapter_model.safetensors` ~59.9MB · merged `model.safetensors` ~6.17GB |

> 해석: eval_loss가 epoch 1→2→3에서 0.589→0.529→0.516으로 개선되어 **과적합 신호는 크지 않다.** 다만 loss는 출력 품질을 보장하지 않으므로, **실제 생성 JSON 테스트와 Ollama 서빙 테스트를 별도로 수행**한다(`HANDOFF_SERVE_TO_CODEX.md`).

## 변경 이력

* 2026-06-20 Phase 0: 스캐폴딩 생성, `C_FIT_EXPLAIN` 시드/규칙엔진/조립 스크립트 초안.
* 2026-06-20 Phase 1: `C_FIT_EXPLAIN` IT/SW 300 → 297(it_mvp baseline). 금지키 0·필수키 100%([reports/02](../../docs/ai-reports/areas/c-career-strategy/reports/02_dataset_quality_report.md)).
* 2026-06-20 Phase 1: 범용 직군 확장 — seed/프롬프트 범용화, 비IT 120 → 119, **mixed 416**(IT 297+비IT 119) 검증 통과(it_leak 0). train **375**/val **41**. 4090 학습 입력=`train.mixed.jsonl`([reports/03](../../docs/ai-reports/areas/c-career-strategy/reports/03_dataset_quality_report.mixed.md)).
* 2026-06-21 Phase 1: 공유 RTX 4090에서 **3B QLoRA 학습·merge 완료**(train_loss 0.627 / eval_loss 0.516, 약 12분). 로그(loss curve) 검증 통과.
* 2026-06-21 Phase 1: **서빙 완주** — GGUF f16(5.75GiB) → **Q4_K_M(1.80GiB)** → Ollama `careertuner-c-career-strategy-3b:latest`. `test_infer` **4/4 PASS**(JSON·금지키0·중국어누출0·비IT IT누출0), `ollama run` JSON 정상. 로그 직접 검증.
* 2026-06-21 Phase 1: **백엔드 C_FIT_EXPLAIN OSS 연동** — 뉴로-심볼릭 조립(Mock 규칙엔진 골격 + 자체모델 설명) + OSS→OpenAI→Mock 폴백. 컴파일+단위테스트 **18/18 통과**. 원격 호출 경로 미확정([reports/10](../../docs/ai-reports/areas/c-career-strategy/reports/10_backend_integration_plan.md)).
* 2026-06-21 Phase 1: **라이브 API E2E 검증** — PR #95 dev 머지(`4406fa7`), 공유 DB 패치(sanction/mention) 적용 후 bootRun 정상. `POST /api/fit-analyses/.../39` → `model=careertuner-c-career-strategy-3b`·`fitScore=45`(규칙엔진)·strategy=자체모델 설명, 금지키/CJK 0. Ollama 중단 시 `model=mock` 폴백 확인. 전체 테스트 202/0. UI 화면 캡처는 4090에서([reports/12](../../docs/ai-reports/areas/c-career-strategy/reports/12_live_e2e_result.md)).
* 2026-06-21 Phase 1: **프런트 UI E2E 실측(4090 로컬, `dev @ 96f9ef0`)** — 시드 실계정 로그인 → application_case 2(국민건강보험공단 전산직) 적합도 화면에 자체모델 설명 정상 표시(`model=careertuner-c-career-strategy-3b`, fitScore=10 규칙엔진, 금지키/CJK 0), 캡처 확보. ★OSS 성공률 실측: 3건 중 case 2 성공·case 14(JSON 파싱)·35(Ollama 500) **mock 폴백**(폴백이 SUCCESS로 화면 보장). 시연은 case 2 사용 권장([reports/12](../../docs/ai-reports/areas/c-career-strategy/reports/12_live_e2e_result.md) §1-A).

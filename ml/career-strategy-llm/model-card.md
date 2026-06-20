# 모델 카드 — careertuner-c-career-strategy (작성 중)

> 운영안 13장 항목. Phase 진행에 따라 채운다. (현재 Phase 0: 스캐폴딩)

| 항목 | 내용 |
| --- | --- |
| 모델명 | `careertuner-c-career-strategy` (alias) · `-3b` / `-7b` 변형 |
| 담당자 | 이정국 (C) |
| 담당 도메인 | 홈/스펙비교/취업분석/대시보드 + 커리어 전략 추천 |
| 직군 범위 | **범용**(IT 전용 아님). Phase 1 MVP는 IT/SW 중심 검증 + 비IT(마케팅·영업·디자인·회계·인사·물류·CS) 샘플 포함. 비IT 정밀 자격증/RAG/점수정책은 Phase 2 확장 |
| base model | Phase 1 `Qwen/Qwen2.5-3B-Instruct` (Apache 2.0) · 비교 `Qwen/Qwen2.5-7B-Instruct` |
| 학습 방식 | QLoRA(4bit NF4) SFT, LoRA r=16/α=32, 3 epoch (D 검증 설정 재사용) |
| 학습 데이터 출처 | 합성 distillation(Claude 선생) + 공개데이터(NCS/Q-net) grounding. 실사용 데이터 미사용 |
| **기본 학습 데이터셋** | **mixed**(IT 297 + 비IT 119 = 416 → `train.mixed.jsonl` 375 / `val.mixed.jsonl` 41). `*.it_mvp.*`=비교/보존용 |
| 주요 task | `C_FIT_EXPLAIN`(MVP) / `C_STRATEGY` / `C_LEARNING_ROADMAP` / `C_TREND_SUMMARY` |
| 입력 형식 | 구조화 텍스트: 공고 요구 + 프로필 + **규칙엔진 사전계산값(fitScore/판단/matched/missing)** |
| 출력 형식 | 설명 JSON (fitSummary/strengths/risks/strategyActions/learningTaskReasons). **점수 미포함** |
| 검증 방식 | JSON 스키마 + 원문근거(입력 외 사실 추가 금지) + 점수/판단 불변 검증 |
| fallback | OSS → OpenAI → Mock (백엔드 `FallbackCareerAnalysisClient`, D 패턴 미러) |
| 알려진 한계 | 소형 모델 JSON 깨짐/한국어 토큰 누출 가능 → format=json + 폴백으로 방어 (Phase 1 측정) |
| 라이선스 주의 | base Qwen2.5 Apache 2.0. 공개데이터는 공공누리 유형 확인(제1유형+출처표시면 상업 OK) |
| 실제 서비스 연결 경로 | `fitanalysis/ai` `FallbackFitAnalysisAiService`(@Primary) → `OssFitAnalysisAiService`(뉴로-심볼릭) → `CareerAnalysisOssClient` → Ollama `careertuner-c-career-strategy-3b`. provider=oss+base-url 설정 시 활성(원격경로 미확정) |
| 마지막 평가 일자 | 2026-06-21 서빙 검증(test_infer 4/4, ollama run) + 백엔드 단위테스트 18/18. 골든셋 정량평가는 Phase 3 |

## 뉴로-심볼릭 설계 요지

점수·판단(`fitScore`/`applyDecision`/`matchedSkills`/`missing*`)은 **서버 규칙엔진이 결정론적으로 계산**하고,
LLM은 그 값을 입력으로 받아 **한국어 설명/추천만** 생성한다. → 점수 재현성·감사가능성 확보, 환각이 설명 문장에 국한.

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
* 2026-06-20 Phase 1: `C_FIT_EXPLAIN` IT/SW 300 → 297(it_mvp baseline). 금지키 0·필수키 100%(`reports/02_`).
* 2026-06-20 Phase 1: 범용 직군 확장 — seed/프롬프트 범용화, 비IT 120 → 119, **mixed 416**(IT 297+비IT 119) 검증 통과(it_leak 0). train **375**/val **41**. 4090 학습 입력=`train.mixed.jsonl`(`reports/03_dataset_quality_report.mixed.md`).
* 2026-06-21 Phase 1: 공유 RTX 4090에서 **3B QLoRA 학습·merge 완료**(train_loss 0.627 / eval_loss 0.516, 약 12분). 로그(loss curve) 검증 통과.
* 2026-06-21 Phase 1: **서빙 완주** — GGUF f16(5.75GiB) → **Q4_K_M(1.80GiB)** → Ollama `careertuner-c-career-strategy-3b:latest`. `test_infer` **4/4 PASS**(JSON·금지키0·중국어누출0·비IT IT누출0), `ollama run` JSON 정상. 로그 직접 검증.
* 2026-06-21 Phase 1: **백엔드 C_FIT_EXPLAIN OSS 연동** — 뉴로-심볼릭 조립(Mock 규칙엔진 골격 + 자체모델 설명) + OSS→OpenAI→Mock 폴백. 컴파일+단위테스트 **18/18 통과**. 원격 호출 경로 미확정(`reports/10_backend_integration_plan.md`).

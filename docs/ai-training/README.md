# A 프로필 AI 학습 모듈

이 폴더는 프로필 평가용 `Qwen3-4B-Instruct-2507` QLoRA 학습·검증·서빙 코드와 저장소에 남길 수 있는
소형 합성 데이터셋을 둔다.

> **현재성 기준:** 2026-07-14 `origin/dev` `23bb4d22`. v1~v4 학습과 v3/v4 비교는 로컬 산출물로
> 확인됐지만, 최신 v4 3,000행 데이터와 adapter는 Git에 포함되지 않는다. 새 clone에서 재현 가능한 기준선은
> 추적 중인 500행 데이터셋이다. 세부 실측과 한계는
> [AI 종합 기술 보고서 §6](../ai-reports/areas/shared-ai/portfolio/careertuner-self-ai-model-deep-dive.md)을 따른다.

## 현재 구현 상태

| 항목 | 상태 |
| --- | --- |
| 베이스 모델 | `Qwen/Qwen3-4B-Instruct-2507`, Apache 2.0 |
| 학습 방식 | NF4 4-bit QLoRA, LoRA `r=16`, `alpha=32`, dropout 0.05, 3 epochs |
| 최신 확인 모델 | `qwen3-profile-lora-v4`, 3,000행(2,400/300/300), 로컬 산출물만 존재 |
| clone 재현 기준 | `profile_ai_training_samples_500.jsonl` + `train_qwen3_profile_qlora.py` |
| 모델 서버 | `serve_profile_ai_model.py`, `/health`, `/analyze-profile` |
| 백엔드 연결 | `FineTunedProfileAiService` 구현 완료 |
| 런타임 기본 | `PROFILE_AI_FINETUNED_ENABLED=false`, base URL 미설정 |
| 폴백 | 전용 모델 실패/비활성 → Claude → OpenAI → `profile-rule-v2` |

학습 완료와 현재 서버 기동은 다른 상태다. 전용 모델은 사용자가 `AUTO` 또는 `CAREERTUNER`를 선택하고,
기능 플래그와 base URL이 설정됐을 때만 진입한다. 현재 기본 설정에서는 호스티드 provider와 규칙 엔진이
서비스를 유지한다.

## 출력 계약

모델은 JSON 객체 하나로 다음 필드를 반환한다.

```text
summary
extractedSkills[]
strengths[]
gaps[]
recommendations[]
criterionScores[]
```

`criterionScores`는 아래 여섯 criterion을 정확히 한 번씩 포함한다.

```text
GOAL_CLARITY
EXPERIENCE_SPECIFICITY
ACHIEVEMENT_EVIDENCE
JOB_SKILL_ALIGNMENT
DOCUMENT_CONSISTENCY
IMPROVEMENT_READINESS
```

모델의 raw score가 최종 점수를 독점하지 않는다. 서버의 `JobFamilyWeightPolicy`, `ProfileScoreCalculator`,
`ProfileQualityGuard`가 직무군 가중치·범위·입력 품질을 결정론적으로 계산하고 보정한다.

## 데이터와 재현 범위

| 파일 | 용도 | 해석 |
| --- | --- | --- |
| `profile_ai_seed_samples.jsonl` | 30행 seed | [수동 검수 기록](archive/seed-30-manual-review-2026-06-23.md) 존재 |
| `profile_ai_training_samples_100.jsonl` | 초기 확장 | 학습 흐름 비교용 |
| `profile_ai_training_samples_300.jsonl` | 초기 확장 | 학습 흐름 비교용 |
| `profile_ai_training_samples_500.jsonl` | 추적 기준선 | clone에서 검증·학습 가능한 기본 입력 |
| v2~v4 JSONL·adapter | 최신 실험 | 개인정보·대형 산출물 경계에 따라 현재 clone에는 없음 |

합성 데이터라도 이메일·전화번호·주민번호·실명 같은 PII가 섞이지 않았는지 검사한다. 자동 validator의 PII
검사는 경고 기반이므로 실제 공개·학습 승격 전 사람 검수가 필요하다. 3,000행 전체를 사람이 검수했다고
주장하지 않는다.

## 문서와 도구

| 문서·도구 | 역할 |
| --- | --- |
| [dataset-schema.md](dataset-schema.md) | JSONL messages와 assistant 출력 스키마 |
| [data-quality-checklist.md](data-quality-checklist.md) | 의미 품질 수동 검수 기준 |
| [training-runbook.md](training-runbook.md) | 검증·QLoRA·추론·서빙 실행 절차 |
| `validate_profile_ai_dataset.py` | 구조, criterion, 범위, 중복, 분포, PII 의심 패턴 검사 |
| `train_qwen3_profile_qlora.py` | 4-bit QLoRA 학습 |
| `infer_qwen3_profile_lora.py` | adapter 단건 추론 |
| `serve_profile_ai_model.py` | FastAPI 추론 서버 |
| `generate_profile_ai_*_samples.py` | 소형 합성 fixture 생성 |
| [archive/](archive/README.md) | 특정 데이터셋 시점의 검수 기록 |

## 빠른 검증

```powershell
python docs/ai-training/validate_profile_ai_dataset.py `
  docs/ai-training/profile_ai_training_samples_500.jsonl

python -m py_compile `
  docs/ai-training/train_qwen3_profile_qlora.py `
  docs/ai-training/infer_qwen3_profile_lora.py `
  docs/ai-training/serve_profile_ai_model.py
```

학습과 서빙 명령은 [training-runbook.md](training-runbook.md)를 사용한다. `output/`, adapter, merged weight,
raw inference 결과는 Git에 커밋하지 않고 적절한 artifact 저장소나 비추적 작업 디렉터리에 둔다.

## 현재 한계

- 최신 v4 데이터·adapter·resolved dependency lock이 없어 clone만으로 동일 checkpoint를 재현할 수 없다.
- 추적된 500행 기준선은 구조 검증을 통과하지만 `ENGINEERING_TECHNICAL` 표본이 0이라는 품질 경고가 남는다.
- `r=16`, `alpha=32`는 sweep으로 찾은 절대 최적값이 아니다.
- 낮은 eval loss에는 반복되는 system/user template 예측도 포함되므로 정확도로 환산할 수 없다.
- v3/v4 25-case 비교는 의도한 calibration 방향을 확인했지만 사람 gold 정확도를 증명하지 않는다.
- 전용 모델 서버 응답의 `adapterDir` identity를 백엔드가 강제로 검증하지 않는다.
- 프로필 경로는 최신 사용자 데이터를 직접 주입하며 embedding/retriever 기반 RAG가 아니다.

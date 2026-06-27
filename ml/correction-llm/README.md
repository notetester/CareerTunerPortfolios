# correction-llm — E 담당 첨삭 자체 모델

CareerTuner E 영역(자기소개서, 면접 답변, 이력서 문장, 포트폴리오 설명 첨삭)의 자체 LoRA/QLoRA 학습 파이프라인이다.

목표는 먼저 `Qwen/Qwen2.5-3B-Instruct`로 작은 seed 데이터가 RTX 4090에서 끝까지 학습되는지 검증하고, 이후 `Qwen3-4B` 또는 `Qwen3-8B`로 품질 비교를 진행하는 것이다.

## 폴더

```text
ml/correction-llm/
  README.md
  requirements.txt
  data/
    raw.seed.40.jsonl              원본 GPT 검수 후보 40개
    raw.trainable.seed.38.jsonl    preserved_meaning=true 학습 후보
    hardcases.seed.2.jsonl         preserved_meaning=false 하드케이스
    dataset.seed.messages.jsonl    SFT messages 포맷
    train.seed.jsonl               smoke train
    val.seed.jsonl                 smoke eval
    dataset.seed.summary.json      변환 요약
  scripts/
    validate_dataset.py
    build_messages.py
    split_data.py
    finetune_lora.py
    test_infer.py
  out/                             LoRA 산출물(커밋 금지)
```

## 데이터 원칙

- `corrected_text`는 `original_text`와 `user_profile_facts`에 없는 경력, 수치, 기술, 성과를 추가하지 않는다.
- `job_context`는 문장 방향을 잡는 데만 쓰고, 지원자의 실제 경험처럼 쓰지 않는다.
- 정보가 부족한 샘플은 `risk_flags`와 낮은 `confidence`로 표현한다.
- `preserved_meaning=false` 샘플은 초반 학습셋에서 제외하고 하드케이스 평가셋으로 둔다.

## 검증 및 변환

```powershell
cd C:\Users\careertuner\Desktop\CareerTuner\ml\correction-llm

..\..\.venv-ai\Scripts\python.exe scripts\validate_dataset.py `
  --input data\raw.seed.40.jsonl `
  --summary-out data\validate.seed.summary.json

..\..\.venv-ai\Scripts\python.exe scripts\build_messages.py `
  --input data\raw.seed.40.jsonl `
  --messages-out data\dataset.seed.messages.jsonl `
  --clean-out data\raw.trainable.seed.38.jsonl `
  --hardcases-out data\hardcases.seed.2.jsonl `
  --summary-out data\dataset.seed.summary.json

..\..\.venv-ai\Scripts\python.exe scripts\split_data.py `
  --input data\dataset.seed.messages.jsonl `
  --train data\train.seed.jsonl `
  --val data\val.seed.jsonl
```

## Smoke 학습

샘플 38개는 품질 평가용이 아니라 파이프라인 검증용이다.

```powershell
cd C:\Users\careertuner\Desktop\CareerTuner\ml\correction-llm

..\..\.venv-ai\Scripts\python.exe scripts\finetune_lora.py `
  --base-model Qwen/Qwen2.5-3B-Instruct `
  --train data\train.seed.jsonl `
  --eval data\val.seed.jsonl `
  --output out\correction-lora-smoke-3b `
  --epochs 1 `
  --batch-size 1 `
  --grad-accum 8 `
  --max-seq-len 2048
```

## Smoke 추론

```powershell
..\..\.venv-ai\Scripts\python.exe scripts\test_infer.py `
  --model out\correction-lora-smoke-3b `
  --raw data\hardcases.seed.2.jsonl
```

합격 기준:

- JSON 파싱 가능
- 필수 키 존재
- `added_facts`가 근거 없이 채워지지 않음
- `risk_flags`가 필요한 상황에서 빠지지 않음
- 중국어/일본어 토큰 누출 없음

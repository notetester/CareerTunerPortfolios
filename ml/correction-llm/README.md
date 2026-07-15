# correction-llm — E 담당 첨삭 자체 모델

CareerTuner E 영역(자기소개서, 면접 답변, 이력서 문장, 포트폴리오 설명 첨삭)의 자체 LoRA/QLoRA 학습 파이프라인이다.

목표는 `Qwen/Qwen2.5-3B-Instruct` 단일 모델로 네 첨삭 유형의 장문 보존과 구조화 출력 계약을 만족하는 것이다.

## 현재 서비스 기준

| 항목 | 현재 값 |
| --- | --- |
| 베이스 모델 | `Qwen/Qwen2.5-3B-Instruct` |
| 고정 서비스 태그 | `careertuner-e-correction-3b:delivery-s-f16-20260708` |
| 백엔드 기본 provider | `openai` |
| 자체 모델 활성화 | `CAREERTUNER_CORRECTION_AI_PROVIDER=self` + `CAREERTUNER_CORRECTION_AI_SELF_BASE_URL` |
| 자체 모델 호출 | 최대 2회(잘못된 JSON은 동일 모델 repair 1회), 요청당 20초·총 30초 예산 |
| 폴백 | 자체 3B → Anthropic → OpenAI, Mock 없음 |

> **라이선스:** 이 3B 모델은 Qwen Research License이며 Apache 2.0이 아니다. 현재 포트폴리오 연구·평가와
> 상용 배포를 구분하고, 상용 사용 전 별도 허가 또는 베이스 교체·재학습·회귀검증을 수행한다.

자체 모델 출력은 JSON 객체 하나여야 하며 서버가 필수 키·타입·허용 enum·원문 보존 여부를 검증한다.
현재 서비스는 `latest` 부동 태그가 아니라 위 `delivery-s` 고정 태그를 사용한다. 모델 가중치와 대형
평가 산출물은 저장소에 넣지 않고 `out/` 또는 `docs/ai-artifacts/`에 둔다.

## 폴더

```text
ml/correction-llm/
  README.md
  requirements.txt
  data/
    raw.seed.360.jsonl                         seed360 원본
    train.seed360.hardfailfix.messages.jsonl   확정 학습 messages 320건
    val.seed360.mixed40.messages.jsonl         확정 평가 messages 40건
    raw.hardcase.val.20.jsonl                  별도 하드케이스 평가 20건
    *.summary.json                             데이터 검증·분할 요약
  scripts/
    validate_dataset.py / build_messages.py / split_data.py
    finetune_lora.py / merge_and_export.py / test_infer.py
    run_gate_suite.py / evaluate_*.py
    build_*_curriculum.py
  out/                             LoRA 산출물(커밋 금지)
```

## 데이터 원칙

- `corrected_text`는 `original_text`와 `user_profile_facts`에 없는 경력, 수치, 기술, 성과를 추가하지 않는다.
- `job_context`는 문장 방향을 잡는 데만 쓰고, 지원자의 실제 경험처럼 쓰지 않는다.
- 정보가 부족한 샘플은 `risk_flags`와 낮은 `confidence`로 표현한다.
- `preserved_meaning=false` 샘플은 초반 학습셋에서 제외하고 하드케이스 평가셋으로 둔다.

## 기준 데이터 검증

```powershell
cd ml/correction-llm
python scripts/validate_dataset.py --input data/raw.seed.360.jsonl --contract legacy
```

기준 데이터는 raw 360건, train 320건, validation 40건이며 별도 하드케이스 20건을 유지한다.
새 데이터는 학습 전에 `validate_dataset.py`와 messages 변환·중복/분할 검사를 통과해야 한다.

## Smoke 학습·추론

아래 명령은 현재 seed360 파일로 학습 파이프라인을 점검하는 예시다. `out/`은 커밋하지 않는다.

```powershell
cd ml/correction-llm
python scripts/finetune_lora.py `
  --base-model Qwen/Qwen2.5-3B-Instruct `
  --train data/train.seed360.hardfailfix.messages.jsonl `
  --eval data/val.seed360.mixed40.messages.jsonl `
  --output out\correction-lora-smoke-3b `
  --epochs 1 `
  --batch-size 1 `
  --grad-accum 8 `
  --max-seq-len 2048
```

```powershell
python scripts/test_infer.py `
  --model out\correction-lora-smoke-3b `
  --raw data/raw.hardcase.val.20.jsonl `
  --preserved-meaning-mode soft
```

합격 기준:

- JSON 파싱 가능
- 필수 키 존재
- `added_facts`가 근거 없이 채워지지 않음
- `risk_flags`가 필요한 상황에서 빠지지 않음
- 중국어/일본어 토큰 누출 없음

## Unified v2 장문 재학습

`unified-v2`는 3B 단일 모델이 자기소개서, 면접 답변, 이력서, 포트폴리오 설명을 모두 처리하도록
기존 단문 데이터와 장문 합성 데이터를 함께 학습하는 계약이다. 생성 원본과 평가 결과는 본체에
커밋하지 않고 `docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/<run-id>/`에 둔다.

처음 사용하는 클론에서는 artifact 서브모듈을 받는다.

```powershell
git submodule update --init docs/ai-artifacts
```

### 1. 생성 요청 dry-run

```powershell
python scripts/generate_unified_data.py `
  --per-task 20 `
  --output ../../docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-04-pilot/requests/raw.generated.jsonl `
  --summary-out ../../docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-04-pilot/results/generation.json `
  --dry-run
```

실제 생성에는 `OPENAI_API_KEY`가 필요하다. 출력 파일이 이미 있으면 덮어쓰지 않으며,
중단 후 계속할 때만 `--resume`을 사용한다.

장문 4건 묶음 생성이 반복 실패하면 raw 파일을 유형별로 분리하고 `--task`로 독립 실행한다.

```powershell
$runRoot = "../../docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-04-pilot"

python scripts/split_raw_by_task.py `
  --input "$runRoot/requests/raw.generated.jsonl" `
  --output-dir "$runRoot/requests/by-task"

python scripts/generate_unified_data.py `
  --per-task 20 `
  --task SELF_INTRO_CORRECTION `
  --output "$runRoot/requests/by-task/raw.self-intro.jsonl" `
  --summary-out "$runRoot/results/generation.self-intro.json" `
  --resume
```

각 유형 파일을 독립 프로세스로 실행할 수 있으며 완료 후 `merge_datasets.py`로 하나의 raw 파일로 합친다.

### 2. 파일럿 생성 및 검증

```powershell
python scripts/generate_unified_data.py `
  --per-task 20 `
  --output ../../docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-04-pilot/requests/raw.generated.jsonl `
  --summary-out ../../docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-04-pilot/results/generation.json `
  --resume

python scripts/validate_dataset.py `
  --input ../../docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-04-pilot/requests/raw.generated.jsonl `
  --contract unified-v2 `
  --summary-out ../../docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-04-pilot/results/validate.json
```

파일럿의 길이 분포와 표본을 검수한 뒤 `--per-task 300`으로 확장한다.

### 3. 신규 데이터 변환과 기존 messages 병합

```powershell
$runRoot = "../../docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-04-pilot"

python scripts/build_messages.py `
  --input "$runRoot/requests/raw.generated.jsonl" `
  --messages-out "$runRoot/requests/long.messages.jsonl" `
  --clean-out "$runRoot/requests/raw.generated.clean.jsonl" `
  --hardcases-out "$runRoot/requests/raw.generated.hardcases.jsonl"

python scripts/split_data.py `
  --input "$runRoot/requests/long.messages.jsonl" `
  --train "$runRoot/requests/long.train.messages.jsonl" `
  --val "$runRoot/requests/long.val.messages.jsonl" `
  --val-ratio 0.15

python scripts/merge_messages.py `
  --input data/train.seed360.hardfailfix.messages.jsonl `
          "$runRoot/requests/long.train.messages.jsonl" `
  --output "$runRoot/requests/unified-v2.train.messages.jsonl"

python scripts/merge_messages.py `
  --input data/val.seed360.mixed40.messages.jsonl `
          "$runRoot/requests/long.val.messages.jsonl" `
  --output "$runRoot/requests/unified-v2.val.messages.jsonl"
```

신규 장문 데이터만 `task_type + length_bucket` 기준으로 나눈 뒤 기존 확정 train 320건과 val 40건에
각각 병합한다. `merge_messages.py`는 첫 번째 기준 파일 내부의 중복 원문을 보존하고 이후 추가 파일에
대해서만 중복을 제거한다. 기존 raw와 신규 raw를 먼저 합치면 하드케이스와 중복 원문 때문에 기준
데이터가 줄어들 수 있다.

### 4. 4090 학습 및 평가

```powershell
$runRoot = "../../docs/ai-artifacts/benchmarks/e-correction-unified-v2/runs/2026-07-04-pilot"

python scripts/finetune_lora.py `
  --base-model Qwen/Qwen2.5-3B-Instruct `
  --train "$runRoot/requests/unified-v2.train.messages.jsonl" `
  --eval "$runRoot/requests/unified-v2.val.messages.jsonl" `
  --output out/correction-unified-v2-3b `
  --epochs 2 `
  --batch-size 1 `
  --grad-accum 8 `
  --lr 1e-4 `
  --max-seq-len 3000

python scripts/test_infer.py `
  --model out/correction-unified-v2-3b `
  --raw "$runRoot/requests/raw.generated.jsonl" `
  --max-new 3072 `
  --contract unified-v2 `
  --preserved-meaning-mode strict
```

2026-07-04 파일럿 당시 Windows RTX 4090에서 3051-token 배치가 4bit CUDA 커널에서 장시간 정체됐다.
당시 `3000` 상한은 작업 유형별 8-step smoke train과 2 epoch 학습에서 검증했고, 파일럿 train 387건 중
13건이 3000 tokens를 초과했다. 이 수치는 현재 서비스 데이터 총량을 뜻하지 않는다.

통합 학습 후 장문 계약 통과율이 낮으면 현재 어댑터를 시작점으로 신규 장문 데이터만 짧게
추가 학습한다.

```powershell
python scripts/finetune_lora.py `
  --resume-adapter out/correction-unified-v2-3b `
  --train "$runRoot/requests/long.train.messages.jsonl" `
  --eval "$runRoot/requests/long.val.messages.jsonl" `
  --output out/correction-unified-v2-3b-longstage `
  --epochs 2 `
  --batch-size 1 `
  --grad-accum 8 `
  --lr 5e-5 `
  --max-seq-len 3000
```

### 5. LoRA 병합

```powershell
python scripts/merge_and_export.py `
  --adapter out/correction-unified-v2-3b `
  --out out/correction-unified-v2-3b-merged
```

출력 디렉터리를 `llama.cpp/convert_hf_to_gguf.py`로 변환하고, 기존 모델을 덮어쓰지 않는
버전 태그(`careertuner-e-correction-3b:unified-v2`)로 Ollama에 등록한다.

### 6. P1 repair 커리큘럼과 배포 게이트

P1은 장문 train과 계약 실패 repair 표본을 섞고, 자기소개서·면접 장문을 한 번 더 노출한다.
repair 정답은 `preserved_meaning=true`, `added_facts=[]`, `changes` 3개 이상인 표본만 사용한다.
같은 raw 파일을 학습·검증 원천으로 지정해도 ID가 겹치지 않게 분리된다.

```powershell
python scripts/build_p1_curriculum.py `
  --repair-train-raw "$runRoot/requests/raw.generated.clean.jsonl" `
  --repair-val-raw "$runRoot/requests/raw.generated.clean.jsonl" `
  --long-train-messages "$runRoot/requests/long.train.messages.jsonl" `
  --long-val-messages "$runRoot/requests/long.val.messages.jsonl" `
  --train-out "$runRoot/requests/p1-v3.train.messages.jsonl" `
  --val-out "$runRoot/requests/p1-v3.val.messages.jsonl" `
  --summary-out "$runRoot/results/p1-v3-curriculum.json" `
  --repair-train-per-task 16 `
  --repair-val-per-task 3
```

repair 표본은 기존 long train/val ID와 같은 split에 배치한다. 생성 요약의
`train_val_id_overlap_count`가 0이 아니면 학습하지 않는다. 후보는 현재 고정 서비스 태그를 덮어쓰지 않는
태그로 등록한 뒤 직접 추론, repair 추론, Ollama strict
schema 추론을 모두 통과해야 한다. 하나라도 실패하면 서비스 태그 교체를 진행하지 않는다.

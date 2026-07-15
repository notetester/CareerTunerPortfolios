# 면접 자체 모델 파인튜닝 (D 영역)

면접 질문 생성·모범답안·답변 평가를 위한 **Qwen2.5-3B-Instruct + LoRA** 멀티태스크
파이프라인이다. 현재 생성기는 `QGEN`, `MODEL_ANSWER`, `EVAL`, `PROBE`, `REPORT`를 만들고 `PLAN`은
아직 생성하지 않는다. 2026-06-20 서비스 체크포인트는 앞선 세 task 중심으로 학습했으며, 현재 서비스에서
자체 모델의 주 용도는 답변 평가다. 상세 실행 기록은 [TRAINING.md](TRAINING.md), 채점 품질 판정은
[eval/LIVE_AB_RESULT.md](eval/LIVE_AB_RESULT.md)를 정본으로 본다.

## 현재 기준

| 항목 | 현재 값 |
| --- | --- |
| 베이스 모델 | `Qwen/Qwen2.5-3B-Instruct` |
| 학습 방식 | 4bit LoRA, `r=16`, `alpha=32`, 3 epochs |
| 검증 GPU | 공유 RTX 4090 24GB |
| 서비스 산출물 | 병합 F16 GGUF, Ollama `interview-3b` |
| 백엔드 기본 평가 provider | `openai` |
| 자체 평가 활성화 | `INTERVIEW_EVAL_PROVIDER=oss`와 OpenAI 호환 endpoint 설정 |

> **라이선스:** 이 3B 모델은 Apache 2.0이 아니라 Qwen Research License다. 연구·평가 목적을 벗어난
> 상용 사용 전에는 별도 허가 또는 상업 친화 베이스로 재학습·회귀검증이 필요하다. 같은 Qwen2.5라도
> 모델 크기별 LICENSE가 다르므로 이름별로 확인한다.

LoRA 학습, 병합, GGUF 변환, Ollama 등록은 2026-06-20 완료했다. 과거 7B/RunPod/vLLM 계획은
현재 기준이 아니다. `serve_vllm.sh`는 OpenAI 호환 서버 실험용 자산으로 남겨 두며 기본 운영은 Ollama다.

## 데이터 준비와 학습

관리자 export 또는 합성 워크플로 결과를 messages JSONL로 조립하고 train/val로 나눈다.

```bash
# 선택: 관리자 학습 데이터 export
curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
  "http://localhost:8080/api/admin/interview/training/export?limit=5000" -o export.jsonl

python assemble_dataset.py --input <workflow-output.json> --out dataset.jsonl
python prepare_data.py --input dataset.jsonl --out-dir data --val-ratio 0.1
python -m pip install -r requirements.txt
python finetune_lora.py \
  --train data/train.jsonl \
  --eval data/val.jsonl \
  --output out/interview-lora \
  --epochs 3
```

`finetune_lora.py` 기본 베이스는 3B다. `data/`, `out/`, 모델 가중치와 raw 생성 결과는 저장소에
커밋하지 않는다. 병합·GGUF·Ollama 등록 절차와 실제 장애 대응은 [TRAINING.md](TRAINING.md)를 따른다.

## 서빙과 백엔드 연결

Ollama는 `/v1/chat/completions` 호환 endpoint를 제공한다.

```bash
INTERVIEW_EVAL_PROVIDER=oss
INTERVIEW_EVAL_BASE_URL=http://<ollama-host>:11434/v1
INTERVIEW_EVAL_MODEL=interview-3b
# endpoint에 인증이 있다면 INTERVIEW_EVAL_API_KEY=...
```

런타임 분기는 다음과 같다.

- 답변 평가·critic: OSS가 설정되면 자체 모델을 먼저 호출하고, 실패하면 `Claude → OpenAI → Mock`으로 degrade한다.
- 질문·모범답안 생성의 `AUTO`: 현재 OSS 생성 task whitelist가 비어 있어 `Claude → OpenAI → Mock`을 사용한다.
- 사용자가 `CAREERTUNER` 모델을 명시적으로 선택한 생성: endpoint가 준비돼 있으면 자체 모델을 시도한 뒤 같은 폴백 체인을 탄다.

자동 생성 whitelist를 비워 둔 이유는 QGEN 데이터가 부족해 JSON 형식과 내용이 불안정하기 때문이다.
자체 모델 평가 연결은 `OssAnswerEvaluator`, 생성 연결은 `OssLlmGateway`, provider 선택은
`FallbackInterviewAnswerEvaluator`와 `FallbackInterviewLlmGateway`에서 확인한다.

## 평가 결과와 운영 결정

2026-07-07 골든셋 60건을 독립 판정단 점수와 비교했다.

| 모델 | 크기 | 판정단 대비 MAE | 10점 이내 일치 |
| --- | ---: | ---: | ---: |
| F16 | 6.18GB | 8.30 | 0.700 |
| Q4_K_M | 1.93GB | 9.53 | 0.617 |

- Q4는 F16보다 오차가 커 운영 후보에서 제외했다.
- F16도 HIGH 답변은 비교적 정확하지만 중간·하위 점수대 오차가 커 단독 사용자 채점기로 보기 어렵다.
- 현재 운영 안전장치는 호스티드 provider 폴백이며, 다음 재학습은 40~84점 부분 정답 사례 보강이 우선이다.

평가 원본·루브릭·재현 명령은 [eval/README.md](eval/README.md)와
[eval/LIVE_AB_RESULT.md](eval/LIVE_AB_RESULT.md)에 있다.

## 주요 파일

| 파일 | 역할 |
| --- | --- |
| `TRAINING.md` | 4090 학습·병합·GGUF·Ollama 실측 절차 |
| `BRIEFING_CONTRACT.md` | Python 합성 QGEN 입력 계약과 런타임 parity 전제 |
| `prepare_data.py` / `assemble_dataset.py` | 데이터 조립과 train/val 분리 |
| `finetune_lora.py` | Qwen2.5-3B 4bit LoRA 학습 |
| `merge_and_export.py` | LoRA 병합 및 tokenizer 저장 |
| `serve_vllm.sh` | 선택적 vLLM 호환 서버 실험 |
| `eval/` | 60건 골든셋, 판정단 점수, A/B 결과 |

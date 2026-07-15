# A 프로필 AI QLoRA 실행 가이드

이 문서는 추적 중인 500행 기준선으로 학습 파이프라인을 재현하고, 별도로 보관한 v4 데이터·adapter가 있을 때
동일 도구로 검증·서빙하는 절차를 설명한다.

> **마지막 검증 범위:** 2026-07-14 `origin/dev` `23bb4d22`. 최신 v4 checkpoint 자체는 저장소에 없으므로
> 아래 명령의 존재가 bitwise 재현을 보장하지 않는다.

## 1. 사전 조건

- Linux 또는 WSL2 권장
- Python 3.10 또는 3.11
- CUDA 지원 NVIDIA GPU, 12GB 이상 권장
- 베이스 모델 다운로드 권한과 충분한 디스크
- 모델별 라이선스 확인: `Qwen3-4B-Instruct-2507`은 Apache 2.0

```bash
python -m venv .venv-ai
source .venv-ai/bin/activate
python -m pip install --upgrade pip
python -m pip install -r docs/ai-training/requirements-qlora.txt
python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'NO GPU')"
```

Windows PowerShell에서는 가상환경 활성화만 다음과 같다.

```powershell
.\.venv-ai\Scripts\Activate.ps1
```

## 2. 입력 검증

clone에서 재현 가능한 기본 입력은 500행이다.

```powershell
python docs/ai-training/validate_profile_ai_dataset.py `
  docs/ai-training/profile_ai_training_samples_500.jsonl
```

별도 보관한 v4 3,000행을 사용할 때도 먼저 같은 validator를 실행한다. 구조 통과는 의미 품질을 보장하지
않으므로 [data-quality-checklist.md](data-quality-checklist.md)에 따라 샘플을 사람 검수한다.

## 3. 기준선 QLoRA 학습

```powershell
python docs/ai-training/train_qwen3_profile_qlora.py `
  --model-name Qwen/Qwen3-4B-Instruct-2507 `
  --dataset docs/ai-training/profile_ai_training_samples_500.jsonl `
  --output-dir docs/ai-training/output/qwen3-profile-lora `
  --epochs 3 `
  --max-seq-length 2048 `
  --batch-size 1 `
  --gradient-accumulation-steps 8 `
  --learning-rate 0.0002 `
  --eval-ratio 0.1 `
  --test-ratio 0.1 `
  --seed 42
```

이 스크립트는 NF4 4-bit base, double quantization, BF16/FP16 compute와 LoRA `r=16`, `alpha=32`,
dropout 0.05를 사용한다. rank·alpha·dropout을 독립 sweep해 최적화한 결과는 아니다.

VRAM 부족 시에는 먼저 `--max-seq-length`를 1536 또는 1024로 낮춘다. gradient accumulation을 낮추면
effective batch까지 바뀌므로 결과 비교 시 설정 차이를 기록한다.

## 4. adapter 추론 검사

```powershell
python docs/ai-training/infer_qwen3_profile_lora.py `
  --adapter-dir docs/ai-training/output/qwen3-profile-lora `
  --sample-index 0
```

최소 게이트는 JSON 단일 객체, 필수 필드, 여섯 criterion, 점수 범위, 입력 밖 사실 미추가다. 단건 통과를
모델 정확도나 운영 승격으로 해석하지 않는다.

## 5. 모델 서버

v3/v4 비교에서 JSON 절단을 줄인 검증 조건은 `max_new_tokens=1400`, temperature 0이었다. 서버 기본값도
이 조건을 사용하며 환경변수로 명시해 실행 이력을 남긴다.

```powershell
$env:PROFILE_AI_BASE_MODEL = "Qwen/Qwen3-4B-Instruct-2507"
$env:PROFILE_AI_ADAPTER_DIR = "docs/ai-training/output/qwen3-profile-lora"
$env:PROFILE_AI_MAX_NEW_TOKENS = "1400"
$env:PROFILE_AI_TEMPERATURE = "0"
$env:PORT = "8000"
python docs/ai-training/serve_profile_ai_model.py
```

```bash
curl http://localhost:8000/health
curl -X POST http://localhost:8000/analyze-profile \
  -H "Content-Type: application/json" \
  -d '{"featureType":"PROFILE_COMPLETENESS","jobFamily":"DEVELOPMENT_DATA","profile":{"desiredJob":"백엔드 개발자","skills":["Java","Spring Boot","MySQL"]}}'
```

## 6. 백엔드 연결

Spring Boot 연결은 이미 `FineTunedProfileAiService`에 구현되어 있다.

```powershell
$env:PROFILE_AI_FINETUNED_ENABLED = "true"
$env:PROFILE_AI_FINETUNED_BASE_URL = "http://localhost:8000"
$env:PROFILE_AI_FINETUNED_MODEL = "qwen3-profile-lora-v4"
```

진입 조건은 사용자 선택 `AUTO`/`CAREERTUNER`, enabled flag, base URL이다. 호출 또는 schema 검증이 실패하면
Claude → OpenAI → 규칙 기반으로 폴백한다. 모델 서버의 `/health`에서 반환하는 `adapterDir`를 배포 전에
사람이 확인한다. 백엔드는 현재 adapter identity를 강제하지 않는다.

## 7. 완료 증거

실행마다 다음을 artifact에 기록한다.

1. 코드 commit SHA와 dataset SHA-256
2. base model revision과 LICENSE
3. Python/CUDA/GPU 및 dependency freeze
4. train/eval/test 분할과 seed
5. LoRA·optimizer·sequence·generation parameter
6. trainer state, loss curve, adapter checksum
7. held-out raw output, validator 결과, 사람 검수 범위
8. 서버 health의 base model·adapter 경로
9. 백엔드 실제 provider/fallback path

가중치·adapter·raw output은 메인 저장소에 커밋하지 않는다. 장문 분석은 `docs/ai-reports/`, raw artifact는
`docs/ai-artifacts/` 경계를 따른다.

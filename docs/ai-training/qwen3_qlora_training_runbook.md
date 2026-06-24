# Qwen3 QLoRA 학습 실행 가이드

이 문서는 A파트 프로필 AI 모델을 실제로 학습시키기 위한 실행 순서입니다.
목표는 `Qwen3-4B-Instruct` 계열 기본 모델에 CareerTuner 프로필 평가 데이터셋을 LoRA 방식으로 학습시키는 것입니다.

## 1. 현재 목표

학습 모델이 해야 하는 일은 단순 채팅이 아닙니다.

```text
사용자 프로필 + 직무군
-> CareerTuner 평가 기준에 맞는 JSON 분석 결과 생성
-> 백엔드가 JSON 검증, 가중치 계산, fallback, 로그 저장
```

모델은 아래 항목을 반드시 포함한 JSON을 만들어야 합니다.

```text
summary
extractedSkills
strengths
gaps
recommendations
criterionScores
```

`criterionScores`에는 아래 6개 기준이 모두 있어야 합니다.

```text
GOAL_CLARITY
EXPERIENCE_SPECIFICITY
ACHIEVEMENT_EVIDENCE
JOB_SKILL_ALIGNMENT
DOCUMENT_CONSISTENCY
IMPROVEMENT_READINESS
```

## 2. 권장 학습 환경

가장 안정적인 환경은 Linux 또는 WSL2 Ubuntu입니다.

```text
OS: Ubuntu 22.04 이상 권장
Python: 3.10 또는 3.11
GPU: NVIDIA CUDA 지원 GPU
VRAM: 12GB 이상 권장, 8GB는 옵션을 낮춰야 할 수 있음
```

Windows에서 바로 실행할 수도 있지만, `bitsandbytes`가 CUDA와 충돌할 수 있습니다.
그 경우에는 Windows에 WSL2 Ubuntu를 설치해서 실행하는 편이 더 안정적입니다.

## 3. 환경 확인

학습 PC에서 먼저 GPU가 보이는지 확인합니다.

```bash
nvidia-smi
```

정상이라면 GPU 이름, 드라이버 버전, CUDA 버전이 표시됩니다.
여기서 오류가 나면 학습 스크립트보다 GPU 드라이버나 CUDA 환경을 먼저 잡아야 합니다.

## 4. 가상환경 생성

프로젝트 루트에서 실행합니다.

```bash
python -m venv .venv-ai
```

Windows PowerShell:

```powershell
.\.venv-ai\Scripts\Activate.ps1
```

Linux 또는 WSL2:

```bash
source .venv-ai/bin/activate
```

## 5. 의존성 설치

```bash
pip install --upgrade pip
pip install -r docs/ai-training/requirements-qlora.txt
```

설치 후 PyTorch가 GPU를 인식하는지 확인합니다.

```bash
python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'NO GPU')"
```

`True`가 나오면 GPU 학습 준비가 된 것입니다.

## 6. 데이터 검증

학습 전에 데이터셋 구조를 먼저 검증합니다.

```bash
python docs/ai-training/validate_profile_ai_dataset.py docs/ai-training/profile_ai_training_samples_500.jsonl
```

현재 500개 샘플은 구조 검증과 중복 검사를 통과한 1차 QLoRA 학습 기본 데이터셋입니다.
기존 30개 seed 데이터, 100개 확장 데이터, 300개 확장 데이터는 학습 흐름 검증용으로 보존하고, 실제 학습 기본값은 500개 데이터셋을 사용합니다.

## 7. QLoRA 학습 실행

기본 실행:

```bash
python docs/ai-training/train_qwen3_profile_qlora.py
```

옵션을 명시해서 실행:

```bash
python docs/ai-training/train_qwen3_profile_qlora.py \
  --model-name Qwen/Qwen3-4B-Instruct-2507 \
  --dataset docs/ai-training/profile_ai_training_samples_500.jsonl \
  --output-dir docs/ai-training/output/qwen3-profile-lora \
  --epochs 3 \
  --max-seq-length 2048 \
  --batch-size 1 \
  --gradient-accumulation-steps 8
```

VRAM이 부족하면 아래 순서로 낮춥니다.

```text
1. --max-seq-length 1536 또는 1024로 낮추기
2. --gradient-accumulation-steps 4로 낮추기
3. epochs를 1로 낮춰서 먼저 파이프라인만 확인하기
```

학습이 끝나면 아래 폴더에 LoRA adapter가 저장됩니다.

```text
docs/ai-training/output/qwen3-profile-lora
```

이 폴더가 실제로 우리가 백엔드나 모델 서버에서 불러올 학습 결과입니다.

## 8. 학습 결과 추론 테스트

```bash
python docs/ai-training/infer_qwen3_profile_lora.py \
  --adapter-dir docs/ai-training/output/qwen3-profile-lora \
  --sample-index 0
```

정상 결과는 JSON 하나가 출력되어야 합니다.
출력에 일반 대화문이 섞이거나, `criterionScores`가 빠지면 학습 데이터 또는 프롬프트를 보강해야 합니다.

## 9. 모델 서버 실행

학습된 모델을 백엔드에서 호출하려면 별도 AI 모델 서버로 띄우는 구조가 좋습니다.

```bash
python docs/ai-training/serve_profile_ai_model.py
```

정상 실행 후 헬스 체크:

```bash
curl http://localhost:8000/health
```

테스트 요청 예시:

```bash
curl -X POST http://localhost:8000/analyze-profile \
  -H "Content-Type: application/json" \
  -d "{\"featureType\":\"PROFILE_COMPLETENESS\",\"jobFamily\":\"DEVELOPMENT_DATA\",\"profile\":{\"desiredJob\":\"백엔드 개발자\",\"skills\":[\"Java\",\"Spring Boot\",\"MySQL\"],\"projects\":[{\"name\":\"회원 API\",\"result\":\"로그인과 회원가입 API 구현\"}]}}"
```

## 10. 백엔드 연동 방향

Spring Boot 쪽에는 이후 아래 구현체를 추가하는 방향입니다.

```text
FineTunedProfileAiService implements ProfileAiService
```

호출 흐름은 다음처럼 가져갑니다.

```text
ProfileController
-> ProfileService
-> FineTunedProfileAiService
-> http://localhost:8000/analyze-profile
-> ProfileAiJsonValidator
-> 실패 시 OpenAI 또는 RuleBased fallback
-> ai_usage_log 저장
```

이 구조를 쓰면 모델을 나중에 바꿔도 백엔드의 컨트롤러나 화면 코드는 크게 흔들리지 않습니다.

## 11. 발표에서 설명할 포인트

발표에서는 아래 문장으로 정리하면 됩니다.

```text
A파트에서는 Qwen3-4B-Instruct 계열 모델을 CareerTuner 프로필 평가 데이터셋으로 QLoRA 파인튜닝했습니다.
모델은 사용자 프로필을 CareerTuner 전용 JSON 스키마로 분석하고,
서버는 AI 응답을 그대로 신뢰하지 않고 JSON 구조와 점수 범위를 검증합니다.
검증 실패 시 OpenAI 또는 규칙 기반 엔진으로 fallback하며,
모든 실행 내역은 ai_usage_log에 저장해 모델 품질과 비용을 추적할 수 있게 설계했습니다.
```

## 12. 다음에 할 일

1. 학습 PC에서 `nvidia-smi` 확인
2. `requirements-qlora.txt` 설치
3. `validate_profile_ai_dataset.py` 실행
4. `train_qwen3_profile_qlora.py` 실행
5. `infer_qwen3_profile_lora.py`로 JSON 출력 확인
6. `serve_profile_ai_model.py`로 모델 서버 실행
7. Spring Boot의 `FineTunedProfileAiService`와 HTTP 연동

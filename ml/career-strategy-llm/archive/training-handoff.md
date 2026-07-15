# CareerTuner C 자체 LLM 4090 학습 인계 기록

> **보관 문서:** 최초 학습 전송 당시 절차다. 현재 상태는 [`../CURRENT_STATE.md`](../CURRENT_STATE.md)와 [`../model-card.md`](../model-card.md)를 따른다.

## 목적

이 폴더는 CareerTuner C 담당 자체 LLM `careertuner-c-career-strategy-3b`를 공유 RTX 4090 PC에서 학습하기 위한 전송 패키지다.
지금 해야 할 일은 다음이다.

```text
1. 파일이 모두 있는지 확인
2. SHA256 체크섬 확인
3. Python 가상환경 생성
4. requirements 설치
5. Qwen2.5-3B QLoRA 학습 실행
6. LoRA merge 실행
7. 학습/merge 결과를 보고
```

GGUF 변환과 Ollama 등록은 학습/merge 성공 후 진행한다.

## 절대 하지 말 것

- Tailscale 설치/설정하지 말 것
- application.yaml 수정하지 말 것
- 백엔드 코드 수정하지 말 것
- 데이터 재생성하지 말 것
- D 담당 `ml/interview-finetune/` 수정하지 말 것
- 7B 학습을 먼저 하지 말 것
- `data/train.mixed.jsonl`, `data/val.mixed.jsonl` 내용을 임의 수정하지 말 것

## 현재 확정 상태

- 기본 학습 모델: `Qwen/Qwen2.5-3B-Instruct`
- 최종 모델명 후보: `careertuner-c-career-strategy-3b`
- 학습 데이터: mixed dataset
  - train: `data/train.mixed.jsonl`
  - val: `data/val.mixed.jsonl`
- 데이터 규모: mixed 416건 = IT/SW 297 + 비IT 119
- split: train 375 / val 41
- 데이터 검증: 416/416 통과
- 금지키: fitScore/score/applyDecision/decision 출력 금지
- 모델 역할: 점수 생성이 아니라 설명 JSON 생성
- 점수와 판단은 서버 규칙엔진의 영역

## 먼저 읽을 파일

아래 순서로 읽어라.

```text
README.md
model-card.md
../../docs/ai-reports/areas/c-career-strategy/reports/00_runbook_4090.md
../../docs/ai-reports/areas/c-career-strategy/reports/03_dataset_quality_report.mixed.md
../../docs/ai-reports/areas/c-career-strategy/reports/05_transfer_manifest.md
../../docs/ai-reports/areas/c-career-strategy/reports/06_sha256_manifest.txt
```

## 파일 확인

다음 파일이 반드시 있어야 한다.

```text
requirements.txt
scripts/finetune_lora.py
scripts/merge_and_export.py
scripts/test_infer.py
scripts/synth_prompts.py
scripts/assemble_dataset.py
data/train.mixed.jsonl
data/val.mixed.jsonl
../../docs/ai-reports/areas/c-career-strategy/reports/00_runbook_4090.md
../../docs/ai-reports/areas/c-career-strategy/reports/06_sha256_manifest.txt
```

없으면 학습을 시작하지 말고 누락 파일을 보고해라.

## 체크섬 확인

PowerShell에서 실행:

```powershell
Get-FileHash .\data\train.mixed.jsonl -Algorithm SHA256
Get-FileHash .\data\val.mixed.jsonl -Algorithm SHA256
Get-FileHash .\requirements.txt -Algorithm SHA256
Get-FileHash .\scripts\finetune_lora.py -Algorithm SHA256
Get-FileHash .\scripts\merge_and_export.py -Algorithm SHA256
Get-FileHash .\scripts\test_infer.py -Algorithm SHA256
```

`../../docs/ai-reports/areas/c-career-strategy/reports/06_sha256_manifest.txt`와 비교해라.

## 학습 명령

Windows CMD 또는 PowerShell에서 실행:

```bat
cd C:\Users\<공유계정>\career-strategy-llm
nvidia-smi
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
cd scripts
python finetune_lora.py --train ..\data\train.mixed.jsonl --eval ..\data\val.mixed.jsonl --output ..\out\career-strategy-lora-3b --epochs 3
```

학습 성공 후 merge:

```bat
python merge_and_export.py --adapter ..\out\career-strategy-lora-3b --out ..\out\career-strategy-merged-3b
```

## 오류가 나면

오류를 임의로 크게 고치지 말고 먼저 원인을 분류해라.
가능한 원인:

```text
CUDA/PyTorch 설치 문제
bitsandbytes Windows 호환 문제
VRAM 부족
파일 경로 오류
train.mixed.jsonl 누락
모델 다운로드 실패
transformers/trl 버전 문제
```

작은 경로 문제나 패키지 설치 문제는 수정 가능하다. 학습 스크립트의 학습 로직, 데이터 스키마, 모델 정책은 임의로 바꾸지 마라.

## 완료 후 보고

다음 형식으로 보고해라.

```text
1. 파일 존재 확인 결과
2. SHA256 일치 여부
3. nvidia-smi 결과
4. pip install 결과
5. 학습 시작/종료 여부
6. 최종 train/eval loss
7. 생성된 out/career-strategy-lora-3b 파일 목록
8. merge 성공 여부
9. 생성된 out/career-strategy-merged-3b 파일 목록
10. 실패했다면 전체 에러 로그와 원인 추정
```

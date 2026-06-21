# 공유 RTX 4090 런북 — C 자체모델 학습 → GGUF → Ollama (복붙용)

> 전제: 노트북에서 `ml/career-strategy-llm/` 안에 **mixed 학습 데이터(`data/train.mixed.jsonl`, `data/val.mixed.jsonl`)** 까지 만든 뒤,
> `05_transfer_manifest.md` 의 파일을 **공유 4090 PC로 전송**(USB/OneDrive/scp).
> 공유 PC에는 Claude Code를 깔지 않는다. 아래 명령만 친다. (Windows 기준, 4090은 D 환경상 Windows)
>
> ⚠️ **`data/` 는 `.gitignore` 처리라 git clone/pull 만으로는 학습 데이터가 안 따라온다 — 데이터 파일은 반드시 별도 전송**(manifest 참고).
> ⚠️ 공유 GPU: 시작 전 `nvidia-smi` 로 빈 VRAM 확인 후 점유. 경합 시 batch 조절 또는 merge `--cpu`.

---

## 0. 데이터 전송 확인
4090에서 옮긴 폴더로 이동(예: `cd C:\Users\<공유계정>\career-strategy-llm`).
**이번 3B 학습 기본 입력 = mixed**. 다음이 존재해야 한다:
```
data\train.mixed.jsonl
data\val.mixed.jsonl
```
전송 무결성 확인(PowerShell, `06_sha256_manifest.txt` 의 해시와 대조):
```powershell
Get-FileHash .\data\train.mixed.jsonl -Algorithm SHA256
Get-FileHash .\data\val.mixed.jsonl   -Algorithm SHA256
```
(IT baseline 만 따로 학습/비교하려면 `data\train.it_mvp.jsonl` / `val.it_mvp.jsonl` 사용 — §7-참고.)

## 1. 환경 (최초 1회)
```bat
nvidia-smi
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```
> Qwen2.5-3B 4bit+LoRA = VRAM ~6~8GB. 4090 24GB 에 여유.

## 2. 학습 — 3B (Phase 1 기본, mixed)
```bat
cd scripts
python finetune_lora.py --train ..\data\train.mixed.jsonl --eval ..\data\val.mixed.jsonl --output ..\out\career-strategy-lora-3b --epochs 3
```
- 입력: **mixed**(IT 297 + 비IT 119 = 416 → train 375 / val 41). 범용 직군 모델.
- 기본: 4bit + LoRA(r=16, α32), batch 1 / grad_accum 8 / lr 2e-4 / max_seq 2048
- VRAM 여유 있으면 `--batch-size 2` 로 속도↑
- 예상: 데이터 ~400줄 × 3 epoch ≈ 20~40분
- ✅ **2026-06-21 학습→서빙 완주**: train_loss 0.627 / eval_loss 0.516(약 12분). merge → GGUF f16(5.75GiB) → **Q4_K_M(1.80GiB)** → Ollama `careertuner-c-career-strategy-3b:latest`. `test_infer` 4/4 PASS·`ollama run` JSON 정상. 백엔드 OSS 연동(C_FIT_EXPLAIN) 단위테스트 18/18 통과. 상세: `HANDOFF_SERVE_TO_CODEX.md`, `reports/09`, `reports/10_backend_integration_plan.md`.
  - 함정: 최초 `torch 2.x+cpu` wheel이면 `ValueError: ...doesn't support bf16/gpu` → **CUDA wheel(cu12x)로 재설치** 필요(`pip install torch --index-url https://download.pytorch.org/whl/cu128` 등).

## 3. 병합 (LoRA → merged)
```bat
python merge_and_export.py --adapter ..\out\career-strategy-lora-3b --out ..\out\career-strategy-merged-3b
```
- tokenizer 까지 함께 저장됨(GGUF 변환 필수). VRAM 경합 시 `--cpu`.

## 4. GGUF 변환 + Q4_K_M 양자화
```bat
git clone --depth 1 https://github.com/ggerganov/llama.cpp
pip install gguf sentencepiece protobuf
python llama.cpp\convert_hf_to_gguf.py ..\out\career-strategy-merged-3b --outfile career-strategy-3b-f16.gguf --outtype f16
```
- `convert_hf_to_gguf.py` 는 f16/bf16/q8_0 만 직접 출력한다. **Q4_K_M 은 `llama-quantize` 바이너리 필요.**
- Windows: llama.cpp **릴리스 prebuilt zip**(`llama-bXXXX-bin-win-*.zip`)을 받으면 `llama-quantize.exe` 가 들어있다(빌드 불필요).
```bat
llama-quantize career-strategy-3b-f16.gguf career-strategy-3b-q4_k_m.gguf Q4_K_M
```
- 공유 PC 경량 운영 목표라 **Q4_K_M(~2GB)** 서빙. 품질 불안하면 `Q5_K_M` 으로 안전하게.
- 양자화 바이너리 준비가 번거로우면 일단 **f16(~6GB) 로 등록해 동작부터 확인**(D가 쓴 회피책)하고, Q4 는 나중에.

## 5. Ollama 등록
`Modelfile` 을 **텍스트 에디터로** 생성(PowerShell `>` 는 UTF-16/BOM 문제 → 깨짐 주의). 내용:
```
FROM ./career-strategy-3b-q4_k_m.gguf
PARAMETER temperature 0.2
PARAMETER stop "<|im_end|>"
```
> 위 2개 PARAMETER 는 **필수**. 없으면 무한반복 + 중국어/일본어 토큰 누출(D가 실제로 겪음).
```bat
ollama create careertuner-c-career-strategy-3b -f Modelfile
```

## 6. 검증
```bat
ollama run careertuner-c-career-strategy-3b
```
- 대화모드 진입 후 `/set system "<synth_prompts.FIT_EXPLAIN_SYS 내용>"` 주입 → 적합도 입력 붙여넣기 → 설명 JSON 6요소가 나오는지 확인.
- 또는 venv 에서 `python test_infer.py` (어댑터 직접 로드, system+샘플입력 자동 주입).
- 성공 기준: 점수 없는 설명 JSON, 입력 외 사실 미추가, fitScore/판단 불변.

> JSON 강제: Modelfile 만으로는 JSON 이 100% 보장되지 않는다. 실제 서빙은 백엔드/HTTP 호출에서
> Ollama `format=json`(+스키마)으로 강제하고, 깨지면 `extractJsonSpan` 후처리 + 폴백한다(Phase 1 후반).

## 7. 7B 비교 (Phase 2~3)
같은 mixed 데이터로 베이스만 교체:
```bat
python finetune_lora.py --base-model Qwen/Qwen2.5-7B-Instruct --train ..\data\train.mixed.jsonl --eval ..\data\val.mixed.jsonl --output ..\out\career-strategy-lora-7b --epochs 3
python merge_and_export.py --adapter ..\out\career-strategy-lora-7b --out ..\out\career-strategy-merged-7b
python llama.cpp\convert_hf_to_gguf.py ..\out\career-strategy-merged-7b --outfile career-strategy-7b-f16.gguf --outtype f16
llama-quantize career-strategy-7b-f16.gguf career-strategy-7b-q4_k_m.gguf Q4_K_M
```
Modelfile 의 FROM 을 7b gguf 로 바꿔 `ollama create careertuner-c-career-strategy-7b`.
비교 항목은 `01_model_comparison_plan.md`.

### 7-참고. IT baseline 단독 학습(선택)
IT/SW baseline 만으로 비교 학습하려면 입력만 교체(나머지 동일):
```bat
python finetune_lora.py --train ..\data\train.it_mvp.jsonl --eval ..\data\val.it_mvp.jsonl --output ..\out\career-strategy-lora-3b-itmvp --epochs 3
```
`*.it_mvp.*` = IT/SW baseline(비교/보존용), `*.nonit.*` = 비IT 추가분, `*.mixed.*` = **기본 학습 데이터**.

## 8. 원격 호출 (이 런북 범위 밖 — 미확정)
- 백엔드가 4090 Ollama(`:11434/v1`)를 원격 호출하려면 네트워크 경로가 필요하다.
- **현재 공유 4090 PC에 Tailscale 미설치 확인.** 설정에 보이는 `localhost` 가 이 PC인지 불명확.
- 다음 중 하나를 추후 확인(이번 작업 범위 아님): 공유 PC Tailscale 신규 설치 / 같은 LAN 내부 IP 접근 /
  SSH·포트포워딩 / 데모를 공유 PC 로컬에서 직접 실행.
- Phase 1 은 **로컬 테스트(test_infer / ollama run)** 우선.

# 자체 면접 모델 학습 가이드 (원격 RTX 4090)

베이스 **Qwen2.5-3B-Instruct** + LoRA 멀티태스크(QGEN / MODEL_ANSWER / EVAL).
데이터는 합성(Claude 선생). 목적은 성능 1등이 아니라 "직접 학습해 붙인 증거".

```
[로컬] 워크플로우 생성 → assemble_dataset.py → prepare_data.py → data/{train,val}.jsonl
        ↓ (data/ 폴더를 4090으로 전송: OneDrive·scp·git-lfs 아무거나)
[4090] pip install → finetune_lora.py → out/interview-lora (LoRA 어댑터)
        ↓
[4090] merge → GGUF 변환 → Ollama 등록 → 백엔드 eval.provider=oss 연결
```

---

## 1. 데이터 준비 (로컬에서 이미 완료된 부분)

```bash
# 워크플로우 결과(raw) → 학습 JSONL 조립
python assemble_dataset.py --input <워크플로우 output.json> --out dataset.jsonl
# train/val 분할
python prepare_data.py --input dataset.jsonl --out-dir data --val-ratio 0.1
# => data/train.jsonl, data/val.jsonl
```

`data/` 폴더를 4090(CHANSSICK)으로 옮긴다. (git 미추적이므로 OneDrive/scp 등으로 직접 전송)

---

## 2. 4090 환경 셋업 (최초 1회)

```bash
nvidia-smi                       # 6명 공유 → 빈 VRAM 확인 후 점유
conda create -n interview python=3.11 -y && conda activate interview
#  또는: python -m venv .venv && .venv\Scripts\activate
pip install -r requirements.txt  # torch/transformers/peft/trl/bitsandbytes
```

> Qwen2.5-3B 4bit + LoRA 는 VRAM ~6~8GB → 4090 24GB 에 여유. 동시 점유자 있으면 배치만 조절.

---

## 3. 학습 실행

```bash
python finetune_lora.py \
  --train data/train.jsonl \
  --eval  data/val.jsonl \
  --output out/interview-lora \
  --epochs 3
```

- 기본: 4bit 양자화 + LoRA(r=16, α32), batch 1 / grad_accum 8 / lr 2e-4 / max_seq 2048
- VRAM 여유 있으면 `--batch-size 2` 또는 `--no-4bit` 로 속도↑
- 예상 시간: 데이터 ~1천 줄 × 3 epoch ≈ 30분~1시간

---

## 4. 서빙 (학습 후 — GGUF → Ollama)

```bash
# (1) LoRA 어댑터를 베이스에 병합
python -c "from peft import AutoPeftModelForCausalLM; \
m=AutoPeftModelForCausalLM.from_pretrained('out/interview-lora'); \
m.merge_and_unload().save_pretrained('out/interview-merged')"
# (2) GGUF 변환 (llama.cpp)
python llama.cpp/convert_hf_to_gguf.py out/interview-merged --outfile interview-3b.gguf --outtype q4_k_m
# (3) Ollama 등록
#   Modelfile:  FROM ./interview-3b.gguf
ollama create interview-3b -f Modelfile
ollama run interview-3b "면접 브리핑: ..."   # 동작 확인
```

백엔드 연결: `interview.eval.provider=oss` + `base-url`=Ollama(OpenAI 호환 `/v1`). 미서빙 시 OpenAI 폴백.

---

## 메모

- 데이터 부족하면 워크플로우 `n` 늘려 재생성(선생 `model:'sonnet'`).
- task 태그(`task` 필드)는 분석용 — 학습은 `messages`만 사용.
- 6 task 중 PROBE/REPORT/PLAN 은 현재 미생성(QGEN/MODEL_ANSWER/EVAL 우선). 필요 시 워크플로우 stage 추가.

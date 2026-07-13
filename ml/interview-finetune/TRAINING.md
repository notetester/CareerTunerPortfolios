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

## 4. 서빙 (학습 후 — merge → GGUF → Ollama) ✅ 2026-06-20 실행 완료

> 환경(실측): 원격 RTX 4090 Windows 환경, conda env `interview`.
> 실제로 밟은 절차 + 함정을 그대로 적는다(인라인 명령의 빈 곳·소형모델 디코딩 이슈 보강).

```bash
# (1) merge — 어댑터를 베이스에 병합 + tokenizer 저장
#     ★인라인 python -c 는 tokenizer 저장을 빠뜨려 (2)에서 막힘. 스크립트로 함께 저장.
python merge_and_export.py --adapter out/interview-lora --out out/interview-merged

# (2) GGUF 변환 — llama.cpp(순수 python, 빌드 불필요)
git clone --depth 1 https://github.com/ggerganov/llama.cpp
pip install gguf sentencepiece          # ★sentencepiece 없으면 토크나이저 단계서 막힘
python llama.cpp/convert_hf_to_gguf.py out/interview-merged --outfile interview-3b-f16.gguf --outtype f16
#     ↑ q4_k_m(K-quant)은 llama-quantize C++ 빌드 필요 → f16 으로 빌드 회피(4090 VRAM 충분, ~6GB)

# (3) Ollama 등록
winget install Ollama.Ollama            # 미설치 시. 설치 후 새 터미널(PATH 갱신) + Ollama 앱 백그라운드 유지
echo FROM ./interview-3b-f16.gguf>Modelfile
#     ★Modelfile 에 아래 2줄 필수 — 없으면 무한 반복 + 중국어/일본어 토큰 누출
#       PARAMETER stop "<|im_end|>"
#       PARAMETER temperature 0.2
ollama create interview-3b -f Modelfile

# (4) 검증 — ★system 프롬프트 필수. 맨몸(ollama run "질문 만들어줘")이면 질문 1개만 나옴.
ollama run interview-3b           # 대화모드 → /set system "면접관 지시" 주입 후 브리핑 → 질문 6개
```

**검증 결과(2026-06-20)**: system 주입 시 질문 6개 정상 생성. 단 소형 3B 한계로 가끔 중국어 토큰 누출·JSON 깨짐
→ 백엔드 `OssLlmGateway` 가 깨진 JSON 시 Claude/OpenAI 폴백(데모 안전). 품질 개선은 데이터 보강·추가학습 트랙.

백엔드 연결(다음 단계): `careertuner.interview.eval.provider=oss` + `eval.base-url`=Ollama OpenAI 호환(`http://<host>:11434/v1`).
학습된 생성 task(질문·모범답안)는 `OssLlmGateway`(화이트리스트), 채점은 `OssAnswerEvaluator`. 미서빙/미설정 시 Claude→OpenAI 폴백.

---

## 메모

- 데이터 부족하면 워크플로우 `n` 늘려 재생성(선생 `model:'sonnet'`).
- task 태그(`task` 필드)는 분석용 — 학습은 `messages`만 사용.
- 6 task 중 PROBE/REPORT/PLAN 은 현재 미생성(QGEN/MODEL_ANSWER/EVAL 우선). 필요 시 워크플로우 stage 추가.

# 자체 면접 모델 학습 가이드 (원격 RTX 4090)

베이스 **Qwen2.5-3B-Instruct** + LoRA 멀티태스크(QGEN / MODEL_ANSWER / EVAL).
데이터는 합성(Claude 선생). 목적은 성능 1등이 아니라 "직접 학습해 붙인 증거".

## 현재 상태

- LoRA 학습, 병합 F16 GGUF, Ollama `interview-3b` 등록은 2026-06-20 완료했다.
- 백엔드의 OSS 평가 분기와 호스티드 provider fallback은 구현되어 있다. 기본 평가 provider는
  `openai`이며, 자체 모델은 명시적으로 `INTERVIEW_EVAL_PROVIDER=oss`를 설정했을 때 활성화한다.
- 2026-07-07 독립 판정단 60건 라이브 A/B 결과 Q4_K_M은 F16보다 오차가 커 운영 후보에서 제외했다.
  현재 품질 기준과 수치는 [LIVE_AB_RESULT](eval/LIVE_AB_RESULT.md)를 따른다.

```
[로컬] 워크플로우 생성 → assemble_dataset.py → prepare_data.py → data/{train,val}.jsonl
        ↓ (data/ 폴더를 4090으로 전송: OneDrive·scp·git-lfs 아무거나)
[4090] pip install → finetune_lora.py → out/interview-lora (LoRA 어댑터)
        ↓
[4090] merge → GGUF 변환 → Ollama 등록 → 백엔드 OSS 평가 분기(명시 설정 시 활성화)
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

> 환경(실측): Windows 기반 공유 RTX 4090, conda env `interview`, Ollama.
> 작업 경로는 사용자별 디렉터리에 의존하지 않는다. 저장소 또는 별도 작업 디렉터리에서 아래 절차를
> 실행하고, 호스트·계정·키 같은 운영 연결 정보는 저장소 문서에 기록하지 않는다.

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

**초기 검증 결과(2026-06-20)**: system 주입 시 질문 6개를 생성했다. 다만 소형 3B에서 중국어 토큰
누출과 JSON 파손이 관측되어 자체 모델 응답을 단독 신뢰하지 않는다. 이후 60건 채점 A/B까지 완료했고,
Q4는 배포하지 않으며 F16도 중간 점수대 오차 때문에 호스티드 provider fallback과 함께 사용한다.

백엔드 연결은 이미 구현되어 있다. 운영 기본값은 `openai`이고, 승인된 Ollama endpoint가 준비된 환경에서만
아래 변수로 OSS 평가를 명시 활성화한다.

```dotenv
INTERVIEW_EVAL_PROVIDER=oss
INTERVIEW_EVAL_BASE_URL=http://<approved-ollama-host>:11434/v1
INTERVIEW_EVAL_MODEL=interview-3b
# endpoint 인증이 구성된 경우에만 설정
INTERVIEW_EVAL_API_KEY=<secret>
```

채점은 `OssAnswerEvaluator`, 생성은 `OssLlmGateway`가 담당한다. 자체 호출 실패 시 백엔드의
`Claude → OpenAI → Mock` 안전망으로 degrade하며, 비밀값은 환경변수나 배포 secret에서만 주입한다.

---

## 메모

- 데이터 부족하면 워크플로우 `n` 늘려 재생성(선생 `model:'sonnet'`).
- task 태그(`task` 필드)는 분석용 — 학습은 `messages`만 사용.
- 현재 생성·조립 범위는 `QGEN` / `MODEL_ANSWER` / `EVAL` / `PROBE` / `REPORT` 다섯 task다.
  `PLAN`만 아직 생성 stage와 assembler가 없다.
- 2026-06-20 서비스용 체크포인트는 당시 우선 범위였던 `QGEN` / `MODEL_ANSWER` / `EVAL` 중심으로
  학습했다. 현재 생성기가 다섯 task를 지원한다는 사실과 배포 체크포인트의 실제 학습 범위를 혼동하지 않는다.

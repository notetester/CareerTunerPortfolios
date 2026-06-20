# HANDOFF_SERVE_TO_CODEX — C 자체 LLM 서빙(생성 sanity → GGUF → Ollama)

## 목적

이미 학습·merge 완료된 `careertuner-c-career-strategy-3b`를
`test_infer`로 생성 sanity 확인한 뒤, **GGUF 변환 → Q4_K_M 양자화 → Ollama 등록 → ollama run 테스트**까지 진행한다.
(학습/merge 는 2026-06-21 완료: train_loss 0.627 / eval_loss 0.516.)

## 절대 하지 말 것

```text
7B 학습 금지
추가 학습 금지
데이터 재생성 금지
백엔드 수정 금지
application.yaml 수정 금지
Tailscale 설치/설정 금지
D 담당 폴더(ml/interview-finetune) 수정 금지
```

## ★ 먼저: test_infer.py 갱신본 확인 (중요)

이 서빙 단계용으로 **`scripts/test_infer.py` 가 갱신됐다**(argparse 추가, cwd 무관 경로, IT/비IT 4샘플 + 자동검사). 학습 전송본의 구버전이면 **갱신본으로 덮어써라.**
- 갱신본 SHA256: `42ce226070bf784c28a6f9fcc47bbd0360d64cde6f58da4a27d81fae6e82b101`
- 확인: `Get-FileHash .\scripts\test_infer.py -Algorithm SHA256` 가 위 값과 같아야 한다. 다르면 노트북에서 받은 갱신본으로 교체.
- `scripts/assemble_dataset.py`, `scripts/synth_prompts.py` 는 학습본 그대로 사용(변경 없음).

## 먼저 확인할 파일/폴더

```text
out/career-strategy-lora-3b/        (학습 어댑터; adapter_model.safetensors ~59.9MB)
out/career-strategy-merged-3b/      (merged HF 모델; model.safetensors ~6.17GB)
scripts/test_infer.py               (갱신본 — 위 SHA256 확인)
scripts/synth_prompts.py
scripts/assemble_dataset.py
reports/00_runbook_4090.md          (GGUF/Ollama 상세 §4~§6)
```

## 1차 생성 테스트 (test_infer)

먼저 **`--help` 로 실제 인자 형식을 확인**한 뒤 실행한다. 인자를 추측하지 말 것.

```bat
cd C:\Users\<공유계정>\career-strategy-llm
.venv\Scripts\activate
cd scripts
python test_infer.py --help
```

확인된 인자로 실행(어댑터/merged 둘 중 택1, 둘 다 해보면 더 좋음):

```bat
rem 어댑터(base+LoRA) — 기본
python test_infer.py --model ..\out\career-strategy-lora-3b
rem merged HF 모델
python test_infer.py --model ..\out\career-strategy-merged-3b --merged
```

`test_infer.py` 는 내장 **IT 2건 + 비IT 2건(APPLY/COMPLEMENT/HOLD 혼합)** 을 추론하고, 샘플마다 자동검사(JSON parse / 필수키 / 금지키 / 중국어·일본어 누출)를 출력한다.

### 생성 테스트 합격 기준 (각 샘플)

```text
JSON parse 가능
필수 키 존재: fitSummary, strengths, risks, strategyActions, learningTaskReasons
금지 키 없음: fitScore, score, applyDecision, decision
중국어/일본어 토큰 누출 없음
입력에 없는 회사/역량/자격증 생성 없음
부족역량을 보유역량처럼 말하지 않음
HOLD 샘플에서 지원 권장처럼 말하지 않음
비IT 샘플에서 IT 전용 표현이 섞이지 않음
```

자동검사(JSON/금지키/CJK)는 스크립트가 출력한다. 나머지(환각/모순/HOLD권장/IT누출)는 **출력 4건을 육안 확인**한다.
**생성 테스트가 통과하면 GGUF 단계로 진행한다.** (소형 3B라 일부 중국어 토큰 누출/JSON 흔들림이 보이면 보고하고, GGUF Modelfile 의 stop/temperature 로 완화되는지 확인.)

## 2. GGUF 변환 → Q4_K_M (런북 §4)

```bat
git clone --depth 1 https://github.com/ggerganov/llama.cpp
pip install gguf sentencepiece protobuf
python llama.cpp\convert_hf_to_gguf.py ..\out\career-strategy-merged-3b --outfile career-strategy-3b-f16.gguf --outtype f16
rem Q4_K_M 양자화: llama.cpp 릴리스 prebuilt zip(llama-bXXXX-bin-win-*.zip)의 llama-quantize.exe 사용
llama-quantize career-strategy-3b-f16.gguf career-strategy-3b-q4_k_m.gguf Q4_K_M
```

- `convert_hf_to_gguf.py` 는 f16/bf16/q8_0 만 직접 출력. **Q4_K_M 은 llama-quantize 바이너리 필요.**
- 양자화 바이너리 준비가 어려우면 f16(~6GB)로 먼저 등록해 동작 확인 후 Q4 진행.

## 3. Ollama 등록 (런북 §5)

`Modelfile` 을 **텍스트 에디터로** 생성(PowerShell `>` 는 BOM 문제). 내용:

```text
FROM ./career-strategy-3b-q4_k_m.gguf
PARAMETER temperature 0.2
PARAMETER stop "<|im_end|>"
```

> `temperature 0.2` 와 `stop "<|im_end|>"` 는 **필수**. 없으면 반복 출력이나 중국어/일본어 토큰 누출이 생길 수 있다.

```bat
ollama create careertuner-c-career-strategy-3b -f Modelfile
```

## 4. ollama run 테스트 (런북 §6)

```bat
ollama run careertuner-c-career-strategy-3b
```

- 대화모드에서 `/set system "<synth_prompts.py 의 FIT_EXPLAIN_SYS 내용>"` 주입 → IT 샘플 입력, 비IT 샘플 입력을 각각 붙여넣어 테스트.
- 입력 형식은 `test_infer.py` 가 출력하는 user 메시지(= `assemble_dataset.build_fit_user` 결과)와 같은 구조를 쓰면 된다.

## 완료 후 보고 형식

```text
1. test_infer 실행 여부
2. test_infer 출력 샘플 4건 요약(IT 2 / 비IT 2)
3. JSON parse 성공 여부
4. 금지 키 발생 여부
5. 중국어/일본어 토큰 누출 여부
6. GGUF 변환 성공 여부
7. Q4_K_M 양자화 성공 여부
8. 생성된 GGUF 파일 크기
9. Ollama create 성공 여부
10. ollama run 출력 샘플
11. 실패/경고 로그
```

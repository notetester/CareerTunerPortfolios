# 공유 4090 서빙 최소 명령 (생성 sanity → GGUF → Ollama)

> Windows 기준. 전체 절차/주의는 `HANDOFF_SERVE_TO_CODEX.md` 와 `00_runbook_4090.md §4~§6`.
> ★ `<공유계정>` 은 압축 해제 경로에 맞게 수정. 학습/merge 는 이미 완료된 상태 전제.

## 0. test_infer.py 갱신본 확인

```powershell
Get-FileHash .\scripts\test_infer.py -Algorithm SHA256
# 기대값: 42ce226070bf784c28a6f9fcc47bbd0360d64cde6f58da4a27d81fae6e82b101
# 다르면 노트북에서 받은 갱신본으로 scripts\test_infer.py 덮어쓰기
```

## 1. 생성 sanity 테스트 (★인자는 --help 로 먼저 확인)

```bat
cd C:\Users\<공유계정>\career-strategy-llm
call .venv\Scripts\activate
cd scripts
python test_infer.py --help
python test_infer.py --model ..\out\career-strategy-lora-3b
rem (선택) merged 모델도: python test_infer.py --model ..\out\career-strategy-merged-3b --merged
```

합격 기준: JSON parse / 필수키 존재 / 금지키(fitScore,score,applyDecision,decision) 없음 / 중국어·일본어 누출 없음 / 환각·모순 없음(육안). 통과 시 다음 단계.

## 2. GGUF 변환 + Q4_K_M

```bat
git clone --depth 1 https://github.com/ggerganov/llama.cpp
pip install gguf sentencepiece protobuf
python llama.cpp\convert_hf_to_gguf.py ..\out\career-strategy-merged-3b --outfile career-strategy-3b-f16.gguf --outtype f16
rem Q4_K_M: llama.cpp 릴리스 prebuilt zip 의 llama-quantize.exe 사용
llama-quantize career-strategy-3b-f16.gguf career-strategy-3b-q4_k_m.gguf Q4_K_M
```

## 3. Modelfile (텍스트 에디터로 생성)

```text
FROM ./career-strategy-3b-q4_k_m.gguf
PARAMETER temperature 0.2
PARAMETER stop "<|im_end|>"
```

## 4. Ollama 등록 + 실행 테스트

```bat
ollama create careertuner-c-career-strategy-3b -f Modelfile
ollama run careertuner-c-career-strategy-3b
rem 대화모드: /set system "<synth_prompts.py FIT_EXPLAIN_SYS>" 주입 후 IT/비IT 입력 각각 테스트
```

보고는 `HANDOFF_SERVE_TO_CODEX.md` 의 '완료 후 보고 형식' 11항목.

@echo off
chcp 65001 >nul
rem ============================================================
rem  CareerTuner C 자체 LLM - 공유 RTX 4090 최소 실행 (mixed 3B)
rem  TeamViewer 에서 복붙용. GGUF/Ollama 는 reports/00_runbook_4090.md 참고.
rem  ★ 아래 <공유계정> 을 ZIP 압축 해제 경로에 맞게 수정하세요.
rem    (예: cd C:\Users\careertuner\career-strategy-llm)
rem ============================================================

cd C:\Users\<공유계정>\career-strategy-llm

rem -- GPU 여유 확인 (공유 PC: 빈 VRAM 확인 후 점유) --
nvidia-smi

rem -- 가상환경 + 의존성 --
python -m venv .venv
call .venv\Scripts\activate
pip install -r requirements.txt

rem -- 3B QLoRA 학습 (mixed: train 375 / val 41) --
cd scripts
python finetune_lora.py --train ..\data\train.mixed.jsonl --eval ..\data\val.mixed.jsonl --output ..\out\career-strategy-lora-3b --epochs 3

rem -- LoRA 병합 (학습 성공 후) --
python merge_and_export.py --adapter ..\out\career-strategy-lora-3b --out ..\out\career-strategy-merged-3b

rem ============================================================
rem  다음 단계 (GGUF 변환 -> Q4_K_M 양자화 -> Ollama 등록 -> 검증)
rem  은 reports/00_runbook_4090.md 의 4~6장을 참고하세요.
rem  학습/merge 결과는 HANDOFF_TO_CODEX.md '완료 후 보고' 형식으로 보고.
rem ============================================================
pause

# 4090 평가 실행 명령 — C_FIT_EXPLAIN 하니스

> 4090(자체모델이 있는 곳)에서 평가 하니스를 실행한다. 결과 파일은 `out/eval/` 에 두고 **커밋하지 않는다**.
> 7B 변환·추가 학습·D/F 자산 변경은 하지 않는다.

## 0. 상태 확인
```powershell
git checkout dev
git pull
ollama list
Invoke-RestMethod http://localhost:11434/v1/models
python --version
```

## 1. LoRA(자체모델) 평가
```powershell
cd ml/career-strategy-llm
python scripts/eval_fit_model.py `
  --cases eval/golden_fit_cases.jsonl `
  --base-url http://localhost:11434/v1 `
  --model careertuner-c-career-strategy-3b `
  --out out/eval/c-fit-3b-eval.json
```
→ 콘솔 요약(success/json_parse_rate/...) + `out/eval/c-fit-3b-eval.json`.

## 2. base 3B 비교 (GGUF 변환 없이 Ollama 기본 모델 사용)
base 가 아직 없으면 받는다(커스텀 변환 아님):
```powershell
ollama pull qwen2.5:3b-instruct
python scripts/eval_fit_model.py `
  --cases eval/golden_fit_cases.jsonl `
  --base-url http://localhost:11434/v1 `
  --model qwen2.5:3b-instruct `
  --out out/eval/c-fit-3b-base-eval.json
```
→ §17 리포트에 base vs LoRA 델타로 채운다. (base 비교가 어려우면 LoRA 단독부터.)

## 3. 재시도 효과(선택) — 백엔드 경유
백엔드를 provider=oss 로 띄워 `CAREERTUNER_ANALYSIS_AI_OSS_MAX_RETRIES` 를 0 vs 2 로 바꿔 동일 케이스 N회 호출 후 `ai_usage_log` 의 model 분포(자체모델 vs mock)를 비교. (백엔드 실행은 별도)

## 4. 보고
```text
1. ollama list / python 버전
2. LoRA 평가 요약(success, json_parse_rate, forbidden_key_rate, cjk_leak_rate, hallucination_flag_rate, p95_latency)
3. base 평가 요약(했다면) + 델타
4. failure_reasons 분포
5. 저장 경로(out/eval/*.json) — 커밋 안 함
6. 남은 이슈
```
※ 결과 JSON·로그는 out/eval/ 에만. 커밋 금지(gitignore). 요약 수치만 reports/17 에 옮긴다.

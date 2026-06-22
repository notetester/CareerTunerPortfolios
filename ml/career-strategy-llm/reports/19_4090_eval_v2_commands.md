# 4090 평가 v2 실행 명령 — raw output + warm latency + pairwise

> 결과는 `out/eval/`(미커밋) → artifact repo(`out/eval-sync/`)로 push(reports/20). 7B/재학습/GGUF 변환/추가데이터/RAG 금지. D/F 자산 변경 금지.

## 0. 상태
```powershell
git checkout dev
git pull
cd ml/career-strategy-llm
ollama list
Invoke-RestMethod http://localhost:11434/v1/models
python --version
```

## 1. LoRA 평가 (raw 저장 + 워밍업 + 3회 반복 + 타임아웃 180)
```powershell
python scripts/eval_fit_model.py `
  --cases eval/golden_fit_cases.jsonl `
  --base-url http://localhost:11434/v1 `
  --model careertuner-c-career-strategy-3b `
  --out out/eval/c-fit-3b-eval-v2.json `
  --save-raw --warmup 1 --repeat 3 --timeout 180
```

## 2. base 평가 (동일 조건)
```powershell
python scripts/eval_fit_model.py `
  --cases eval/golden_fit_cases.jsonl `
  --base-url http://localhost:11434/v1 `
  --model qwen2.5:3b-instruct `
  --out out/eval/c-fit-3b-base-eval-v2.json `
  --save-raw --warmup 1 --repeat 3 --timeout 180
```

## 3. pairwise 입력 생성
```powershell
python scripts/eval_fit_model.py --pairwise `
  --lora-result out/eval/c-fit-3b-eval-v2.json `
  --base-result out/eval/c-fit-3b-base-eval-v2.json `
  --pairwise-out out/eval/c-fit-3b-pairwise-input.json
```

## 4. (선택, 정확한 warm 격리) D 모델 언로드 후 단독 측정
GPU 경합을 줄이려면 평가 중 다른 모델을 잠깐 내린다(D 모델 삭제 아님, 언로드만):
```powershell
ollama stop interview-3b   # 잠깐 언로드(파일/등록은 그대로). 평가 후 필요 시 다시 로드됨.
```
> ★ D 모델을 **삭제/교체하지 말 것**. `ollama stop` 은 메모리 언로드일 뿐. 확실치 않으면 생략하고 그대로 측정.

## 5. artifact repo 로 결과 push
`reports/20_eval_artifact_sync.md` 절차대로 `out/eval-sync/` 에 결과를 복사하고 push.
(메인 repo 에는 절대 커밋하지 않는다 — out/ 는 gitignore.)

## 6. 보고
```text
1. LoRA 요약: success_rate, json_parse, cjk_leak, timeout, cold_start / warm_avg / warm_p95
2. base 요약: 동일 지표
3. cold vs warm 비교(1차 latency artifact 검증)
4. CJK 누출: 어떤 케이스·어느 필드·전체깨짐/일부토큰 (raw_output 근거)
5. pairwise-input 생성 여부 + artifact repo push 여부
6. out/eval/ 결과는 메인 repo 미커밋 확인
```

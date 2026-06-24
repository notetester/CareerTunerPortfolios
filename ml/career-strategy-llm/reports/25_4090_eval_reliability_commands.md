# 4090 평가 신뢰도 라운드 — 통합 실행 명령

> 목적: ① pairwise selected-run 재생성(케이스 1·2 복원) ② blind 입력 생성 ③ 격리 latency 측정.
> 결과는 `out/eval/`(미커밋) → artifact repo(CareerTunerAI) push. 메인 repo 커밋 금지.
> 금지: 7B/재학습/GGUF 재생성/추가데이터 대량생성/RAG, D·F 변경, 모델 삭제(stop은 언로드).

## 0. 코드 최신화
```powershell
git checkout dev
git pull
cd ml/career-strategy-llm
```

## 1. pairwise selected-run + blind 재생성 (모델 불필요 — 기존 결과 재사용)
artifact repo에 이미 있는 v2 결과로 selected-run pairwise + blind 생성:
```powershell
# (artifact repo가 out/eval-sync에 clone돼 있다고 가정. 기존 결과를 out/eval로 복사하거나 경로 직접 지정)
python scripts/eval_fit_model.py --pairwise `
  --lora-result out/eval-sync/c-fit-3b-eval-v2.json `
  --base-result out/eval-sync/c-fit-3b-base-eval-v2.json `
  --pairwise-out out/eval/c-fit-3b-pairwise-input.json `
  --blind --blind-out out/eval/c-fit-3b-pairwise-blind.json `
  --blind-key-out out/eval/c-fit-3b-pairwise-blind-key.json
```
→ 성공 run 선택으로 12케이스 전체 복원(selectedRun/selectionReason 기록) + blind A/B 입력 + 매핑키.

## 2. (선택) 정정 골든셋으로 재평가 — 계약 수치 정밀화
골든셋 forbiddenClaims 오탐 정정(이 라운드)을 반영하려면 재평가(모델 필요):
```powershell
python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl `
  --model careertuner-c-career-strategy-3b --out out/eval/c-fit-3b-eval-v3.json `
  --save-raw --warmup 1 --repeat 3 --timeout 180
```

## 3. 격리 latency 측정 (reports/22)
```powershell
ollama ps
ollama stop interview-3b; ollama stop qwen2.5:3b-instruct
python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl `
  --model careertuner-c-career-strategy-3b --out out/eval/c-fit-3b-isolated.json `
  --save-raw --warmup 1 --repeat 3 --timeout 180
ollama ps; nvidia-smi
ollama stop careertuner-c-career-strategy-3b
python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl `
  --model qwen2.5:3b-instruct --out out/eval/base-isolated.json `
  --save-raw --warmup 1 --repeat 3 --timeout 180
ollama ps; nvidia-smi
```

## 4. artifact repo push
```powershell
Copy-Item out/eval/c-fit-3b-pairwise-input.json,out/eval/c-fit-3b-pairwise-blind.json,out/eval/c-fit-3b-pairwise-blind-key.json,out/eval/c-fit-3b-isolated.json,out/eval/base-isolated.json out/eval-sync/ -Force
cd out/eval-sync
git add c-fit-3b-pairwise-input.json c-fit-3b-pairwise-blind.json c-fit-3b-pairwise-blind-key.json c-fit-3b-isolated.json base-isolated.json
git commit -m "eval reliability: selected-run pairwise + blind + isolated latency"
git push
```

## 5. 보고
```text
1. selected-run 복원: 12케이스 모두 available 인지, case 1·2의 selectedRun/selectionReason
2. blind 생성: 쌍 수, 매핑키 별도 저장 확인
3. 격리 latency: C 단독 warm_avg/p95 + tok/s, base 단독 warm_avg/p95 + tok/s, nvidia-smi VRAM
   → '단독에서도 LoRA가 ~5배 느린가' vs 'base에 근접한가' 판정
4. artifact repo push 완료 / 메인 repo 미커밋 확인
```

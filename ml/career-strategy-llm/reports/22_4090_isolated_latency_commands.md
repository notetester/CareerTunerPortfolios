# 4090 격리 latency 측정 — 모델 자체 vs VRAM 경합 분리

> v2에서 LoRA warm latency가 base보다 ~7배(34s vs 4.7s). 460 vs 319 토큰(1.4배)으로는 다 설명 안 됨 → per-token도 ~5배 느림. 이게 **모델 자체**인지 **VRAM 경합/동시 로드**인지 분리한다(단일 모델만 로드해 측정).
> ★ `ollama stop` = 메모리 언로드(삭제 아님). 모델 삭제·GGUF 재생성·재학습·D/F 변경 금지.

## 절차 (PowerShell, 4090)
```powershell
cd ml/career-strategy-llm
ollama ps                                   # before 스냅샷
# 1) 다른 모델 언로드(삭제 아님)
ollama stop interview-3b
ollama stop qwen2.5:3b-instruct
ollama ps                                   # C 외 언로드 확인
nvidia-smi                                  # VRAM 여유 확인

# 2) C LoRA 단독 측정 (warmup 후 repeat)
python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl `
  --model careertuner-c-career-strategy-3b --out out/eval/c-fit-3b-isolated.json `
  --save-raw --warmup 1 --repeat 3 --timeout 180
ollama ps; nvidia-smi                       # C 단독 로드 상태/VRAM

# 3) base 단독 측정 (C 언로드 후)
ollama stop careertuner-c-career-strategy-3b
python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl `
  --model qwen2.5:3b-instruct --out out/eval/base-isolated.json `
  --save-raw --warmup 1 --repeat 3 --timeout 180
ollama ps; nvidia-smi
```

## 기록할 것 (모델별)
```text
model
selected_cases / repeat_count
warm_avg_latency_ms / warm_p95_latency_ms   (하니스 summary)
avg_output_chars / avg_output_tokens        (usage.completion_tokens)
tokens_per_second = completion_tokens / (latency_ms/1000)
ollama_ps_before_after
nvidia_smi_snapshot (VRAM used/total, 동시 로드 모델)
```

## 해석 기준
- **단독에서도 LoRA가 여전히 ~5배 느리면** → 모델/quant/Modelfile 자체 문제(데모 리스크, 후속 조사).
- **단독에서 LoRA가 base에 근접하면** → 공유 4090 VRAM 경합(C+D+base 동시)이 원인 → 데모 땐 단일 모델만 로드하면 됨.
- 결과는 `out/eval/`(미커밋) → artifact repo push. 수치만 `reports/17`/별도 요약에 반영.

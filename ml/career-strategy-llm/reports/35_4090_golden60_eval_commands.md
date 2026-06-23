# 4090 골든셋 평가 실행 명령 (확장 골든셋 — 계약 + E1 + E2 + latency)

> 목적: 확장 골든셋으로 자체모델(LoRA) vs base 를 한 번에 측정 — **계약·E1 grounding·E2 named-entity·PARSE_FAIL·latency** 전부.
> 전제: 골든셋 확장 + E1/E2 관측기 PR 이 dev 에 merge. Ollama 에 `careertuner-c-career-strategy-3b` + `qwen2.5:3b-instruct`.

## A. 작업 큐로 실행(권장 — 복붙 0)
4090 에서 **"작업 진행"** → `run_latest_job.ps1` 이 `eval_golden_set` job 을 잡아 **두 모델 각각** 평가하고 `results/<jobId>/` 에 push.
```powershell
cd C:\Users\careertuner\Desktop\CareerTuner\ml\career-strategy-llm\out\eval-sync
git pull
.\scripts\run_latest_job.ps1 -CareerTunerRepo C:\Users\careertuner\Desktop\CareerTuner
```
job(`eval_golden_set`)은 `params.models` 의 각 모델을 돌려 `<jobId>-<model>.json` 으로 저장. 의존성 게이트가 E1 관측기 머지를 확인.

## B. 수동 실행(대체)
```powershell
git checkout dev; git pull
cd ml\career-strategy-llm
# LoRA(자체모델)
python scripts\eval_fit_model.py --cases eval\golden_fit_cases.jsonl `
  --base-url http://localhost:11434/v1 --model careertuner-c-career-strategy-3b `
  --save-raw --warmup 1 --repeat 3 --timeout 180 --out out\eval\golden-lora.json
# base(대조)
python scripts\eval_fit_model.py --cases eval\golden_fit_cases.jsonl `
  --base-url http://localhost:11434/v1 --model qwen2.5:3b-instruct `
  --save-raw --warmup 1 --repeat 3 --timeout 180 --out out\eval\golden-base.json
```

## 콘솔/JSON 에서 보는 지표
```
[E1관측] grounding_violation count=<N> rate=<r>   (부족 역량을 보유로 서술 — 백엔드 guard 발동 프록시)
[E2관측] unsupported_named_entity count=<N> rate=<r> (high=가짜 제품 식별자)
success=<x>/<n>  json_parse=<r>  cjk_leak=<r>  hallucination=<r>  timeout=<n>
cold_start/warm_avg/warm_p95 latency
```
`summary` 키: `grounding_violation_{count,rate,by_case}`, `unsupported_named_entity_{count,rate,by_case}`,
`success_rate`, `json_parse_rate`, `failure_reasons`(PARSE_FAIL 케이스 확인), latency.

## 케이스 검증(네트워크 불필요)
```powershell
python scripts\golden_case_tools.py eval\golden_fit_cases.jsonl   # 사실 필드·밴드 정합(0 오류여야)
python scripts\test_entity_observer.py                            # E1/E2 관측기 단위테스트
```

## grounding-retries 정책 (코드 변경 없음)
운영 기본 `1`. 데모/리허설만 env 로 `2`(자체모델 노출률↑). 백엔드 실행 시:
```powershell
$env:CAREERTUNER_ANALYSIS_AI_OSS_GROUNDING_RETRIES="2"   # 데모만
```
(주의: eval 하니스는 Ollama 직접 호출이라 grounding-retries 와 무관 — E1 관측기는 모델 raw 출력의 위반율을 '측정'만 한다.)

## 산출물 규칙
- 결과 JSON/로그는 `out/eval/`(미커밋) → **artifact repo `CareerTunerAI`** push(`results/<jobId>/`). 메인 repo 엔 요약만.
- D/F 모델·설정 미수정. 7B/재학습/RAG 금지. 점수/판단 로직 미변경.

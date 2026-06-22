# 4090 E2 관측 평가 실행 명령 (named-entity observer)

> 목적: 자체모델이 골든셋에서 **입력 밖 고유명사/제품코드를 얼마나 날조하는지**를 관측(reports/30 지표). reject 없음 — 수치만 본다.
> 전제: dev 에 E2 observer PR merge 완료. 4090 에 `careertuner-c-career-strategy-3b` + Ollama(`OLLAMA_HOST=0.0.0.0`).

## 실행 (4090, PowerShell)
```powershell
git checkout dev; git pull            # E2 observer 포함 확인
cd ml\career-strategy-llm

# 1) 자체모델로 골든셋 평가(반복 3회로 stochastic 관측)
python scripts\eval_fit_model.py --cases eval\golden_fit_cases.jsonl `
  --base-url http://localhost:11434/v1 --model careertuner-c-career-strategy-3b `
  --save-raw --warmup 1 --repeat 3 --timeout 180 `
  --out out\eval\c-fit-3b-e2-observer.json

# 2) (대조) base 모델로 동일 평가 — 날조 빈도 비교용(선택)
python scripts\eval_fit_model.py --cases eval\golden_fit_cases.jsonl `
  --base-url http://localhost:11434/v1 --model qwen2.5:3b-instruct `
  --save-raw --warmup 1 --repeat 3 --timeout 180 `
  --out out\eval\c-fit-3b-base-e2-observer.json

# 3) (드라이런) 모델 없이 파이프라인 점검
python scripts\eval_fit_model.py --cases eval\golden_fit_cases.jsonl --mock --repeat 1 `
  --out out\eval\e2-mock-check.json
```

## 콘솔에서 바로 보는 지표
```
[E2관측] unsupported_named_entity count=<N> rate=<r> (high=제품코드; review 별도)
  - <caseId>: high=[CRM465, ...] (runs_flagged=k)
```
JSON 의 `summary` 에서:
```
unsupported_named_entity_count / unsupported_named_entity_rate / unsupported_named_entities_by_case
```
각 run 의 `named_entities.{high,review}` 로 어떤 토큰이 어디서 나왔는지 확인(`--save-raw` 면 `raw_output` 과 대조).

## 단위테스트(네트워크 불필요, 노트북/4090 어디서나)
```powershell
python scripts\test_entity_observer.py    # 11 케이스: CRM465류 포착 + 오탐 미발생 + success 불변
```

## grounding-retries 정책 (코드 변경 없음)
기본 운영값은 코드에서 `1` 로 둔다(변경 금지). **데모/리허설에서 자체모델 노출률을 조금 더 올리고 싶을 때만** 환경변수로 조정:
```powershell
# 운영 기본
$env:CAREERTUNER_ANALYSIS_AI_OSS_GROUNDING_RETRIES="1"
# 데모/리허설 선택값(자체모델 노출률↑, 위반 시 재시도 1회 더 — 약간 느려짐)
$env:CAREERTUNER_ANALYSIS_AI_OSS_GROUNDING_RETRIES="2"
```
| 상황 | grounding-retries |
| --- | --- |
| 운영 기본 | `1` |
| 데모/리허설 | `2` (env 로만) |

## 산출물 규칙
- 결과 JSON/로그는 `out/eval/`(미커밋) → **artifact repo `CareerTunerAI`** push. 메인 repo 엔 **요약 수치만**.
- D/F 모델·설정 미수정. 7B/재학습/RAG 금지.
- 작업큐(reports/32) 사용 시 `taskType=eval_e2_observer` job 으로도 실행 가능(복붙 불필요).

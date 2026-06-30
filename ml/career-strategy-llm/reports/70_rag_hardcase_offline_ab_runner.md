# RAG hard-case offline A/B runner

## 1. 작업 목적
#190 에서 추가한 RAG hard-case fixture 와 dry-run runner 를 확장해, offline Ollama 기준 A/B 실행 결과를 저장할 수 있는 최소 실행 골격을 만들었다. 이번 작업은 RAG runtime production 연결이 아니며, raw output/result JSON 은 CareerTuner main repo 가 아니라 CareerTunerAI artifact repo 에 저장하는 정책을 반영한다.

## 2. runner 확장 내용
수정 파일:

```text
ml/career-strategy-llm/scripts/run_rag_hardcase_benchmark.py
```

추가된 기능:

- `--provider ollama` 기반 offline model run 옵션.
- `--base-url`, `--model`, `--timeout-seconds`, `--continue-on-error`, `--allow-remote` 옵션.
- A/B request payload 저장 유지.
- non-dry-run 시 variant 별 raw output, result JSON 저장.
- dry-run 시에도 `results/*.result.json` placeholder 를 생성해 summarizer 를 검증할 수 있게 함.
- CareerTuner main repo 내부 output 은 `ml/career-strategy-llm/reports/generated/` 아래만 허용.
- localhost/private/Tailscale 대역 외 base-url 은 `--allow-remote` 없이는 거부.

## 3. dry-run 유지 여부
기본 안전 흐름은 계속 `--dry-run` 이다. `--dry-run` 은 모델 호출을 하지 않고 request payload 와 result placeholder 만 생성한다.

실행:

```bash
cd ml/career-strategy-llm
python scripts/run_rag_hardcase_benchmark.py \
  --fixture data/rag_hardcase_benchmark/rag_hardcases_v1.jsonl \
  --out reports/generated/rag_hardcase_benchmark_v1 \
  --dry-run
```

결과:

```text
[run_rag_hardcase_benchmark]
  fixture=data\rag_hardcase_benchmark\rag_hardcases_v1.jsonl
  out=reports\generated\rag_hardcase_benchmark_v1
  cases=12 variants=2 modelCalls=0 dryRun=True
```

## 4. offline Ollama 호출 옵션
권장 예:

```bash
python scripts/run_rag_hardcase_benchmark.py \
  --fixture data/rag_hardcase_benchmark/rag_hardcases_v1.jsonl \
  --out D:/dev/CareerTunerAI/benchmarks/rag-hardcase/runs/rag_hardcase_v1_YYYYMMDD_HHMM \
  --provider ollama \
  --base-url http://127.0.0.1:11434 \
  --model careertuner-c-career-strategy-3b
```

구현 방식:

- Python 표준 라이브러리 `urllib.request` 사용.
- `POST /api/generate`, `stream=false`.
- 외부 OpenAI/Claude API 호출 없음.
- production backend 호출 없음.
- benchmark 전용 prompt 로 명시하고, production prompt 로 취급하지 않음.

## 5. output/result schema
출력 구조:

```text
rag_hardcase_benchmark_v1/
  benchmark_manifest.json
  requests/
  outputs/
  results/
  aggregate_summary.json
```

각 result JSON 은 다음 필드를 가진다.

- `caseId`
- `category`
- `variant`
- `model`
- `provider`
- `baseUrl`
- `latencyMs`
- `rawOutputPath`
- `rawOutputExists`
- `error`
- `parsedOutput`
- `metrics`
- `gateResult`

metric 자리:

- `contract_success`
- `json_parse_success`
- `unsupported_possession_claim_count`
- `r3_gate_status`
- `r3_reason_count`
- `r3_max_severity`
- `raw_hallucinated_skill_count`
- `normalized_hallucinated_skill_count`
- `semantic_judge_hallucinated_skill_count`
- `cjk_leak`
- `latency_ms`
- `output_length`

현재 계산하는 값은 `json_parse_success`, `latency_ms`, `output_length`, `error`, `rawOutputExists` 이다. R3 gate 와 semantic judge 는 아직 연결하지 않았다.

## 6. aggregate summarizer 추가 여부
추가했다.

```text
ml/career-strategy-llm/scripts/summarize_rag_hardcase_results.py
```

실행:

```bash
python scripts/summarize_rag_hardcase_results.py \
  --results reports/generated/rag_hardcase_benchmark_v1/results \
  --out reports/generated/rag_hardcase_benchmark_v1/aggregate_summary.json
```

결과:

```text
[summarize_rag_hardcase_results]
  results=reports\generated\rag_hardcase_benchmark_v1\results
  out=reports\generated\rag_hardcase_benchmark_v1\aggregate_summary.json
  resultCount=24 variantCount=2
  A_lora_only: cases=12 errors=0 avgLatency=None avgOutputLength=None
  B_structured_evidence_buckets: cases=12 errors=0 avgLatency=None avgOutputLength=None
```

dry-run placeholder 라서 latency/output length 는 `None` 이다.

## 7. generated/raw output 커밋 방지 정책
- CareerTuner main repo 의 `ml/career-strategy-llm/reports/generated/` 는 `.gitignore` 대상이다.
- raw output, generated request/result JSON, aggregate summary 는 main repo 에 커밋하지 않는다.
- main repo 에는 runner, validator, small synthetic fixture, 요약 report, artifact path/commit SHA 참조만 남긴다.

## 8. CareerTunerAI artifact 저장 정책
실제 모델 호출 산출물은 다음 repo 에 저장한다.

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/runs/<run-id>/
```

저장 대상:

- generated requests
- raw model outputs
- result JSON
- aggregate summary
- benchmark manifest

실제 artifact 를 저장한 경우 CareerTuner main repo report 에는 artifact path 와 CareerTunerAI commit SHA 만 기록한다.

## 9. CareerTunerAIDocs 장문 보고서 저장 정책
긴 실험 분석 보고서는 CareerTuner main repo 에 누적하지 않는다. 필요 시 다음 repo 에 저장한다.

```text
D:/dev/CareerTunerAIDocs/areas/c-career-strategy/reports/
```

이번 작업에서는 장문 분석 문서를 작성하지 않았다.

## 10. 실행한 검증 명령
```bash
cd ml/career-strategy-llm
python scripts/validate_rag_hardcase_fixture.py data/rag_hardcase_benchmark/rag_hardcases_v1.jsonl

python scripts/run_rag_hardcase_benchmark.py \
  --fixture data/rag_hardcase_benchmark/rag_hardcases_v1.jsonl \
  --out reports/generated/rag_hardcase_benchmark_v1 \
  --dry-run

python scripts/summarize_rag_hardcase_results.py \
  --results reports/generated/rag_hardcase_benchmark_v1/results \
  --out reports/generated/rag_hardcase_benchmark_v1/aggregate_summary.json

git diff --check

cd D:/dev/CareerTuner/backend
./gradlew.bat test --tests com.careertuner.fitanalysis.service.EvidenceGateServiceTest

git -C D:/dev/CareerTunerAI status
git -C D:/dev/CareerTunerAIDocs status
```

## 11. 실제 모델 호출 실행 여부
실행하지 않았다. 이 PR 에서는 runner 의 offline Ollama 호출 옵션과 output schema 를 추가하고, dry-run/schema/summarizer 검증까지만 수행했다. 4090/Ollama endpoint 와 모델 가용성 확인은 다음 단계에서 수행한다.

## 12. CareerTunerAI 저장 여부
저장하지 않았다. 실제 모델 호출을 실행하지 않았으므로 CareerTunerAI artifact push 없음.

## 13. CareerTunerAI artifact path / commit SHA
- artifact path: 해당 없음.
- commit SHA: 해당 없음.

## 14. CareerTunerAIDocs 저장 여부
저장하지 않았다. 긴 분석 보고서 작성 없음.

## 15. 다음 작업 후보
- 4090/Ollama 환경에서 `careertuner-c-career-strategy-3b` 실제 A/B 실행.
- CareerTunerAI 에 raw output/result/aggregate artifact 저장 및 commit SHA 기록.
- R3 gate evaluator 를 result schema 에 연결.
- semantic judge packet 과 aggregate summarizer 를 reports/67 성공/실패 기준까지 확장.

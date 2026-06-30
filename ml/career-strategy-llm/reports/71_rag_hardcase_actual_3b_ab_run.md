# RAG hard-case actual 3B LoRA A/B run

## 1. 작업 목적
#191 에서 추가한 offline A/B runner 를 사용해 RAG hard-case fixture v1 을 실제 3B LoRA 모델로 1회 실행했다. 이번 작업은 RAG runtime 을 production backend 에 연결하지 않고, raw output/result/manifest/aggregate summary 는 CareerTunerAI artifact repo 에 저장하며, CareerTuner main repo 에는 artifact path 와 commit SHA 만 기록한다.

## 2. 실행 환경
- 실행일: 2026-06-30
- 실행 위치: 로컬 Codex 작업 환경에서 Tailscale 로 4090/Ollama endpoint 호출
- 로컬 `127.0.0.1:11434`: 접근 불가
- 사용 endpoint: `http://localhost:11434`
- endpoint 성격: Tailscale `100.*` 대역의 허용된 private endpoint

## 3. Ollama endpoint
사전 확인:

```bash
curl.exe -sS --max-time 8 http://localhost:11434/api/tags
```

확인 결과 `careertuner-c-career-strategy-3b:latest` 모델이 존재했다.

## 4. 모델명
```text
careertuner-c-career-strategy-3b:latest
```

## 5. fixture 경로
```text
D:/dev/CareerTuner/ml/career-strategy-llm/data/rag_hardcase_benchmark/rag_hardcases_v1.jsonl
```

fixture validator 결과:

```text
cases=12 categories=12
OK fixture schema valid
```

## 6. 실행 명령
dry-run 재확인:

```bash
cd D:/dev/CareerTuner/ml/career-strategy-llm

python scripts/run_rag_hardcase_benchmark.py \
  --fixture data/rag_hardcase_benchmark/rag_hardcases_v1.jsonl \
  --out reports/generated/rag_hardcase_benchmark_v1 \
  --dry-run
```

실제 A/B 실행:

```bash
cd D:/dev/CareerTuner/ml/career-strategy-llm

python scripts/run_rag_hardcase_benchmark.py \
  --fixture data/rag_hardcase_benchmark/rag_hardcases_v1.jsonl \
  --out D:/dev/CareerTunerAI/benchmarks/rag-hardcase/runs/rag_hardcase_v1_20260630_1635 \
  --provider ollama \
  --base-url http://localhost:11434 \
  --model careertuner-c-career-strategy-3b:latest \
  --continue-on-error
```

aggregate summary 생성:

```bash
python scripts/summarize_rag_hardcase_results.py \
  --results D:/dev/CareerTunerAI/benchmarks/rag-hardcase/runs/rag_hardcase_v1_20260630_1635/results \
  --out D:/dev/CareerTunerAI/benchmarks/rag-hardcase/runs/rag_hardcase_v1_20260630_1635/aggregate_summary.json
```

## 7. CareerTunerAI artifact path
```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/runs/rag_hardcase_v1_20260630_1635
```

저장 대상:

- generated requests
- raw model outputs
- result JSON
- benchmark manifest
- aggregate summary

## 8. CareerTunerAI commit SHA
```text
8939d5856bf7edc9b9c93a7f9ff94034ab8d0a4e
```

## 9. aggregate summary 요약
```text
resultCount=24
variantCount=2
```

## 10. A/B variant별 result count
| variant | result count |
| --- | ---: |
| A_lora_only | 12 |
| B_structured_evidence_buckets | 12 |

## 11. A/B variant별 error count
| variant | error count |
| --- | ---: |
| A_lora_only | 0 |
| B_structured_evidence_buckets | 0 |

## 12. A/B variant별 average latency
| variant | average latency ms |
| --- | ---: |
| A_lora_only | 1269.42 |
| B_structured_evidence_buckets | 1518.08 |

## 13. A/B variant별 average output length
| variant | average output length |
| --- | ---: |
| A_lora_only | 558.33 |
| B_structured_evidence_buckets | 654.58 |

## 14. raw output main repo 미커밋 확인
raw output, generated request/result JSON, benchmark manifest, aggregate summary 는 CareerTuner main repo 에 커밋하지 않았다. CareerTuner main repo 에는 이 보고서와 checklist 의 artifact path/commit SHA 참조만 남긴다.

## 15. CareerTunerAIDocs 사용 여부
이번 작업에서는 장문 해석 보고서를 작성하지 않았다.

```text
CareerTunerAIDocs push 없음
```

## 16. 아직 하지 않은 것
- R3 gate evaluator 를 result schema 에 실제 연결하지 않았다.
- semantic judge 로 unsupported possession claim 감소 여부를 자동 채점하지 않았다.
- raw output 본문에 대한 장문 정성 분석은 작성하지 않았다.
- production RAG runtime, backend prompt, model 설정은 변경하지 않았다.

## 17. 다음 작업 후보
- aggregate summary 에서 `placeholderNullMetricCount` 를 줄이기 위한 R3 gate evaluator 연결.
- semantic judge packet 을 붙여 A/B 간 unsupported claim 감소를 수치화.
- CareerTunerAIDocs 에 장문 분석 보고서 작성.
- hard-case fixture v2 에 비IT/자격증/자기모순 케이스를 추가.

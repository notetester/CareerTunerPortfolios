# A-only production 경로 baseline v1 실측 summary

> 상세: [AIDocs report 79](../../../docs/ai-reports/areas/c-career-strategy/reports/79_a_only_baseline_v1_run.md)
> (AIDocs commit `d42fb38`). 방향 근거: [AIDocs report 77](../../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md) §8
> — 다음 실측의 중심은 B(evidence-bucket)가 아니라 A(production 경로)다. 명명은 `evidence-attribution-*` 계열.

## 실행

- 픽스처: [data/evidence_attribution_baseline/a_only_baseline_v1.jsonl](../data/evidence_attribution_baseline/a_only_baseline_v1.jsonl)
  — 60 synthetic 케이스, 7 카테고리(confusion_pair 12 · requirement/cert/company_stack 각 8 · decision 3밴드 각 8), PII 0.
- 하니스: `scripts/build|validate|run|evaluate_a_only_baseline_*.py`(러너는 rag-hardcase 러너 헬퍼 import 재사용).
- 4090 Ollama `careertuner-c-career-strategy-3b`, SSH 터널 loopback, 60/60 콜 성공.
- raw/평가 artifact: CareerTunerAI `benchmarks/evidence-attribution-baseline/{runs,evaluations}/a_only_baseline_v1_20260702_1159*`,
  commit `e40022f`.

## 결과 (offline 결정론 관찰 — 확정 아님)

| 지표 | 값 |
| --- | --- |
| PASS_OFFLINE | 50/60 (83.3%) |
| CANDIDATE_UNSUPPORTED_POSSESSION | 5케이스/6문장 (confusion_pair 3 + requirement_as_owned 2 집중) |
| PARSE_FAIL | 5 (8.3%, 알려진 3B JSON 취약성 — production 은 폴백이 흡수) |
| CJK leak / latency | 0 / avg 1183ms |

- 후보 6건은 **확정 위반 아님**(발췌상 다수가 rubric v2 의 RISK_WARNING_ONLY/UNCLEAR 성격, 기존 체인에서도
  offline 후보는 top-LLM consensus 에서 대부분 기각). 검토 패킷 10건 생성 — human/judge rubric v2 확정이 다음 단계.
- 한계: benchmark 프롬프트는 payload-JSON 형(production 뉴로-심볼릭 프롬프트 아님), R3 gate 는 offline observer 근사, repeat=1.

## 판단

A(production 경로)의 offline 후보율 8.3%(확정 전)·안전 카테고리 광범위 0 은 **A+E1+R3 기본 경로 유지** 근거와
일치. B 는 연구 후보 유지. "A true unsupported ≈ 0" 확정은 패킷 검토 후. 후속: gold label 확정 → repeat≥2 →
CURRENT_STATE 반영 → 120케이스 확장 여부 결정.

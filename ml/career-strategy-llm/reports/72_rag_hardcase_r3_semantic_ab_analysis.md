# RAG hard-case R3 semantic A/B analysis summary

> **용어 정정 (2026-07-02, errata):** 이 보고서에서 "RAG"로 지칭한 실험은 **true external retrieval RAG**
> (런타임 벡터검색·웹·카탈로그 조회)가 아니라, 이미 입력된 profile/job 정보와 정적 합성 사실을
> evidence bucket 으로 재구조화해 prompt 에 추가한 **evidence-bucket prompt augmentation** 실험이다.
> true external retrieval RAG 는 아직 구현·평가되지 않았다. 수치·판단(`KEEP_RAG_DISABLED` 포함)은 유지된다.
> 상세: [AIDocs report 77](../../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md)

## 1. 작업 목적
#193 의 실제 RAG hard-case A/B output 을 대상으로 offline R3-like evaluator 와 semantic/rule-based observer 를 적용해 A/B 품질 차이를 분석했다.

이번 작업은 production RAG runtime 연결이 아니며, backend production prompt, model 설정, EvidenceGateService, SkillAliasNormalizer production 코드는 변경하지 않았다.

## 2. 입력 artifact path / CareerTunerAI run commit SHA
입력 run artifact:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/runs/rag_hardcase_v1_20260630_1635
```

기록된 입력 run commit:

```text
8939d5856bf7edc9b9c93a7f9ff94034ab8d0a4e
```

## 3. evaluator script 경로
```text
ml/career-strategy-llm/scripts/evaluate_rag_hardcase_outputs.py
```

## 4. evaluator output path / CareerTunerAI evaluation commit SHA
evaluation output:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/evaluations/rag_hardcase_v1_20260630_1635_r3_semantic_eval
```

CareerTunerAI evaluation commit:

```text
78167ea981f7d85035116cd1c65e15460223e1c4
```

## 5. CareerTunerAIDocs analysis path / commit SHA
장문 분석:

```text
D:/dev/CareerTunerAIDocs/areas/c-career-strategy/reports/73_rag_hardcase_v1_r3_semantic_analysis.md
```

CareerTunerAIDocs commit:

```text
e93a7f8443fb5638bcf253c381a3e9953149006b
```

## 6. aggregate evaluation summary 요약
```text
resultCount=24
variantCount=2
A_lora_only cases=12
B_structured_evidence_buckets cases=12
```

## 7. A/B unsafe claim 비교
| metric | A_lora_only | B_structured_evidence_buckets | B-A |
| --- | ---: | ---: | ---: |
| combined unsafe claims | 9 | 5 | -4 |
| deterministic unsafe claims | 0 | 0 | 0 |
| model-reported unsupported claims | 9 | 5 | -4 |
| REVIEW_REQUIRED | 11 | 7 | -4 |
| PASSED | 1 | 5 | +4 |

`combined unsafe claims` 는 deterministic detector 결과와 model-reported unsupported count 중 큰 값을 사용한다. deterministic detector 는 direct ownership-like sentence 를 보수적으로 찾으며, 이번 run 에서는 direct unsafe sentence 를 확정하지 않았다.

## 8. B improved / regressed / unchanged case 수
```text
B improved cases: 5
B regressed cases: 1
unchanged cases: 6
```

개선된 case:

```text
RAG-HC-001, RAG-HC-003, RAG-HC-004, RAG-HC-005, RAG-HC-008
```

악화된 case:

```text
RAG-HC-007
```

## 9. RAG 재도입 판단
판단: **보류, 제한 재평가 유지**.

B variant 는 structured `evidenceBuckets` 로 unsupported claim self-report 와 REVIEW_REQUIRED 수를 줄였다. 하지만 B 에도 combined unsafe claim 5건과 REVIEW_REQUIRED 7건이 남아 있어 production prompt 에 RAG/evidenceBuckets 를 자동 주입할 근거로는 부족하다.

## 10. raw output main repo 미커밋 확인
raw output, generated request/result JSON, benchmark manifest, aggregate summary, evaluator output JSON 은 CareerTuner main repo 에 커밋하지 않았다.

main repo 에 남긴 것은 evaluator script, 이 짧은 summary report, checklist/current-state 링크, submodule pointer 뿐이다.

## 11. 아직 하지 않은 것
- 외부 또는 별도 local semantic judge LLM 을 호출하지 않았다.
- production Java R3 gate 를 직접 실행하지 않고 Python offline evaluator 로 개념을 재현했다.
- `hallucinatedSkillCount` 는 아직 null 이다.
- RAG runtime 자동 주입과 rewrite 자동 노출은 하지 않았다.

## 12. 다음 작업 후보
- local/external semantic judge packet 으로 model-reported unsupported count 를 독립 검증.
- RAG-HC-009, RAG-HC-011 잔여 실패군 중심의 prompt contract 보강.
- Spring/Spring Boot regression 을 hard-case fixture v2 에 확장.
- Python evaluator 와 Java SkillAliasNormalizer mention-boundary parity test 추가.

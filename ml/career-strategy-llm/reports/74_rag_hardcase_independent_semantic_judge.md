# RAG hard-case independent semantic judge summary

## 1. 작업 목적

#198의 offline R3-like evaluator 결과를 별도 local semantic judge로 독립 검증했다. 이번 작업은 production RAG runtime 연결이 아니며, backend prompt, model 설정, EvidenceGateService, SkillAliasNormalizer production 코드는 변경하지 않았다.

## 2. 입력 artifact path / commit SHA

실제 A/B run artifact:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/runs/rag_hardcase_v1_20260630_1635
```

기록된 run commit:

```text
8939d5856bf7edc9b9c93a7f9ff94034ab8d0a4e
```

offline evaluator artifact:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/evaluations/rag_hardcase_v1_20260630_1635_r3_semantic_eval
```

기록된 evaluation commit:

```text
78167ea981f7d85035116cd1c65e15460223e1c4
```

## 3. judge packet path / CareerTunerAI commit SHA

judge packet:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/judge-packets/rag_hardcase_v1_20260630_1635_judge_packets.jsonl
```

manifest:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/judge-packets/rag_hardcase_v1_20260630_1635_judge_packets.manifest.json
```

CareerTunerAI commit:

```text
4bf1fc70b3acd8946d7eee8f06787477446466af
```

## 4. judge result path / CareerTunerAI commit SHA

judge result:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/judge-results/rag_hardcase_v1_20260630_1635_semantic_judge
```

aggregate summary:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/judge-results/rag_hardcase_v1_20260630_1635_semantic_judge/aggregate_judge_summary.json
```

CareerTunerAI commit:

```text
4bf1fc70b3acd8946d7eee8f06787477446466af
```

## 5. CareerTunerAIDocs report path / commit SHA

장문 분석:

```text
D:/dev/CareerTunerAIDocs/areas/c-career-strategy/reports/74_rag_hardcase_v1_independent_semantic_judge.md
```

CareerTunerAIDocs commit:

```text
60c3f908a730f59c0c3e1860dbdc17c58f072bf6
```

## 6. judge provider/model

```text
provider=ollama
baseUrl=http://localhost:11434
model=qwen2.5:7b-instruct
```

Tailscale private Ollama endpoint를 사용했고, OpenAI/Claude 같은 외부 API는 호출하지 않았다.

## 7. aggregate judge summary

```text
resultCount=24
variantCount=2
modelCalls=24
judgeErrorCount=0
recommendation=KEEP_RAG_DISABLED
```

| variant | PASS | UNSUPPORTED_POSSESSION_CLAIM | unsupported claim count |
| --- | ---: | ---: | ---: |
| A_lora_only | 10 | 2 | 2 |
| B_structured_evidence_buckets | 9 | 3 | 3 |

## 8. offline evaluator와 judge의 agreement/disagreement

```text
judgeVsOfflineAgreementCount=11
judgeVsOfflineDisagreementCount=13
judgeVsOfflineNotComparableCount=0
```

offline evaluator는 B의 combined unsafe claim이 9에서 5로 줄었다고 보았지만, qwen2.5 7B 독립 judge는 B의 unsupported claim count를 A보다 1건 많게 봤다. 일부 불일치는 judge가 missing/verification warning을 residual unsupported risk로 과잉 해석한 데서 나온다.

## 9. A/B unsafe claim 비교

| metric | A_lora_only | B_structured_evidence_buckets | B-A |
| --- | ---: | ---: | ---: |
| #198 combined unsafe claim count | 9 | 5 | -4 |
| #198 unsafe result count | 7 | 3 | -4 |
| independent judge unsupported claim count | 2 | 3 | +1 |
| #198 REVIEW_REQUIRED | 11 | 7 | -4 |
| source contract failed count | 1 | 1 | 0 |

B improved cases:

```text
RAG-HC-007, RAG-HC-012
```

B regressed cases:

```text
RAG-HC-001, RAG-HC-006, RAG-HC-009
```

unchanged cases:

```text
RAG-HC-002, RAG-HC-003, RAG-HC-004, RAG-HC-005,
RAG-HC-008, RAG-HC-010, RAG-HC-011
```

## 10. RAG 재도입 판단

판단: **KEEP_RAG_DISABLED**.

독립 judge 기준으로는 B가 unsupported possession claim을 안정적으로 줄였다고 볼 수 없고, offline evaluator 기준으로도 B에 REVIEW_REQUIRED 7건이 남았다. 따라서 RAG runtime 자동 주입과 rewrite 자동 노출은 계속 금지하고, RAG는 offline/scoped 재평가 대상으로만 유지한다.

## 11. raw/judge output main repo 미커밋 확인

CareerTuner main repo에는 raw output, judge packet JSONL, judge raw output, judge result JSON, aggregate JSON 전문을 커밋하지 않았다.

main repo에 남긴 것은 다음뿐이다.

- packet builder / runner / summarizer script
- 짧은 summary report
- CURRENT_STATE / AI_ROADMAP_CHECKLIST / reports README 갱신
- CareerTunerAI, CareerTunerAIDocs submodule pointer

## 12. 아직 하지 않은 것

- multi-judge consensus는 수행하지 않았다.
- disagreement 13건의 human review는 수행하지 않았다.
- production Java R3 gate를 직접 호출하지 않았다.
- RAG runtime production 연결은 하지 않았다.

## 13. 다음 작업 후보

- qwen2.5 7B judge와 다른 local judge model의 multi-judge consensus 구성.
- disagreement 13건 human review packet 분리.
- risk-warning과 positive ownership claim을 별도 label로 나누는 judge rubric v2.
- RAG-HC-001, RAG-HC-006, RAG-HC-009를 hard-case v2 regression set으로 고정.

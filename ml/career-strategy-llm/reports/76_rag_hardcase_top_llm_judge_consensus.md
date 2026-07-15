# RAG hard-case top LLM judge consensus summary

> **용어 정정 (2026-07-02, errata):** 이 보고서에서 "RAG"로 지칭한 실험은 **true external retrieval RAG**
> (런타임 벡터검색·웹·카탈로그 조회)가 아니라, 이미 입력된 profile/job 정보와 정적 합성 사실을
> evidence bucket 으로 재구조화해 prompt 에 추가한 **evidence-bucket prompt augmentation** 실험이다.
> true external retrieval RAG 는 아직 구현·평가되지 않았다. 수치·판단(`KEEP_RAG_DISABLED` 포함)은 유지된다.
> 상세: [AIDocs report 77](../../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md)

## 1. 작업 목적

PR #201의 top LLM judge pack에 대해 사용자가 수동 수집한 ChatGPT / Claude / Gemini interface judge 결과 3개를 저장하고, schema validation / aggregate summary / disagreement matrix / human review 후보를 생성했다.

이번 작업은 production RAG runtime 연결이 아니며, backend production prompt, model 설정, EvidenceGateService, SkillAliasNormalizer production 코드는 변경하지 않았다. OpenAI / Anthropic / Google API도 호출하지 않았다.

## 2. 입력 response 파일명

```text
openai_chatgpt_judge_result.json
anthropic_claude_judge_result.json
google_gemini_judge_result.json
```

저장 위치:

```text
docs/ai-artifacts/benchmarks/rag-hardcase/top-llm-judge-responses/rag_hardcase_v1_20260630_1635_top_llm_judge_responses
```

## 3. judgeMeta 요약

| key | provider | interface | modelNameAsReportedByInterface |
| --- | --- | --- | --- |
| openai_chatgpt | OpenAI | ChatGPT | GPT-5.5 Thinking |
| anthropic_claude | Anthropic | Claude | Claude Opus 4.8 |
| google_gemini | Google | Gemini | Gemini 3.1 Pro |

모든 결과는 `rag-hardcase-judge-rubric-v2` 기준으로 작성되었고, evaluationDate는 `2026-07-01`이다.

## 4. schema validation 결과

validator:

```text
ml/career-strategy-llm/scripts/validate_rag_hardcase_top_llm_judge_results.py
```

결과:

```text
valid=True
openai_chatgpt: itemJudgments=24, pairJudgments=12, unsupported A=0/B=0
anthropic_claude: itemJudgments=24, pairJudgments=12, unsupported A=0/B=0
google_gemini: itemJudgments=24, pairJudgments=12, unsupported A=0/B=0
```

## 5. aggregate artifact path / commit SHA

CareerTunerAI response:

```text
docs/ai-artifacts/benchmarks/rag-hardcase/top-llm-judge-responses/rag_hardcase_v1_20260630_1635_top_llm_judge_responses
```

CareerTunerAI aggregate:

```text
docs/ai-artifacts/benchmarks/rag-hardcase/top-llm-judge-aggregates/rag_hardcase_v1_20260630_1635_top_llm_judge_aggregate
```

CareerTunerAI commit:

```text
949e8ad29d08c21f768d09d748e9ccd3437f949b
```

## 6. AIDocs report path / commit SHA

장문 분석:

```text
docs/ai-reports/areas/c-career-strategy/reports/76_rag_hardcase_top_llm_judge_consensus.md
```

CareerTunerAIDocs commit:

```text
1f0829f23985da6d0a60de41aa350e6c7542a0c3
```

## 7. top LLM consensus 요약

top LLM consensus 기준 true unsupported possession claim:

```json
{
  "A_lora_only": 0,
  "B_structured_evidence_buckets": 0
}
```

pair consensus:

```json
{
  "B_BETTER": 6,
  "B_WORSE": 1,
  "UNCHANGED_SAFE": 4,
  "UNCHANGED_UNSAFE": 0,
  "MIXED": 0,
  "NOT_COMPARABLE": 1
}
```

B_BETTER case:

```text
RAG-HC-001
RAG-HC-003
RAG-HC-004
RAG-HC-005
RAG-HC-006
RAG-HC-011
```

B_WORSE case:

```text
RAG-HC-007
```

NOT_COMPARABLE case:

```text
RAG-HC-008
```

regression candidate consensus:

```text
RAG-HC-001
RAG-HC-007
RAG-HC-011
```

## 8. Qwen / offline evaluator 대비 차이

Qwen 대비:

```text
disagreementCount=5
overFlagCount=5
```

Qwen over-flag item:

```text
RAG-HC-001 / B_structured_evidence_buckets / java_vs_javascript
RAG-HC-006 / B_structured_evidence_buckets / react_vs_react_native
RAG-HC-007 / A_lora_only / spring_vs_spring_boot
RAG-HC-009 / B_structured_evidence_buckets / job_requirement_as_user_owned
RAG-HC-012 / A_lora_only / certificate_requirement_as_owned
```

offline evaluator 대비:

```text
disagreementCount=10
overCountCount=10
```

offline evaluator는 empty output, metric self-report, warning/verification 문맥을 실제 user-facing possession claim처럼 과다 카운트한 것으로 보인다.

## 9. human review 후보 요약

human review candidate는 12개 case 모두다. 우선순위는 다음과 같이 둔다.

```text
RAG-HC-001: regression candidate, Qwen over-flag, offline over-count, NOT_JUDGEABLE
RAG-HC-007: B_WORSE, regression candidate, Qwen over-flag, offline over-count, NOT_JUDGEABLE
RAG-HC-011: regression candidate, offline over-count, NOT_JUDGEABLE
RAG-HC-008: NOT_COMPARABLE, offline over-count, NOT_JUDGEABLE
RAG-HC-006: Qwen over-flag, NOT_JUDGEABLE
RAG-HC-009: Qwen over-flag, offline over-count
RAG-HC-012: Qwen over-flag
RAG-HC-002/RAG-HC-004/RAG-HC-010: label/severity/pair disagreement
RAG-HC-003/RAG-HC-005: offline over-count, NOT_JUDGEABLE
```

전체 후보 원문은 CareerTunerAI `human_review_candidates.json`에 있다.

## 10. RAG runtime 판단

최종 판단:

```text
KEEP_RAG_DISABLED
```

분리해서 봐야 할 사실:

- top LLM consensus 기준 true unsupported possession claim은 0건이다.
- 그러나 empty output / NOT_JUDGEABLE / B_WORSE / NOT_COMPARABLE / regression candidate가 남아 있다.
- 따라서 이번 결과는 Qwen/offline evaluator 보정 필요성을 보여주지만, production RAG runtime 자동 주입을 허용하는 근거는 아니다.

## 11. 아직 하지 않은 것

- human review candidate 실제 검토.
- gold label 확정.
- Qwen judge prompt/rubric 보정.
- offline evaluator calibration.
- hard-case fixture v2 regression set 반영.
- production RAG runtime 연결.

## 12. 다음 작업 후보

- human review candidate를 우선순위대로 검토하고 gold label을 확정한다.
- Qwen over-flag 5건을 기준으로 local/private semantic judge rubric을 보정한다.
- offline over-count 10건을 기준으로 empty output, metric self-report, warning-only 처리 규칙을 보정한다.
- RAG-HC-001, RAG-HC-007, RAG-HC-011을 regression candidate로 fixture v2에 반영한다.

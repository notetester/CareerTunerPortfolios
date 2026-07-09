# RAG hard-case top LLM judge pack summary

> **용어 정정 (2026-07-02, errata):** 이 보고서에서 "RAG"로 지칭한 실험은 **true external retrieval RAG**
> (런타임 벡터검색·웹·카탈로그 조회)가 아니라, 이미 입력된 profile/job 정보와 정적 합성 사실을
> evidence bucket 으로 재구조화해 prompt 에 추가한 **evidence-bucket prompt augmentation** 실험이다.
> true external retrieval RAG 는 아직 구현·평가되지 않았다. 수치·판단(`KEEP_RAG_DISABLED` 포함)은 유지된다.
> 상세: [AIDocs report 77](../../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md)

## 1. 작업 목적

#199의 Qwen2.5 7B independent judge 결과를 바로 gold label로 확정하지 않고, ChatGPT/Claude/Gemini 인터페이스에서 현재 사용 가능한 최상위 reasoning/analysis 모델에 동일하게 붙여넣어 평가받을 수 있는 judge pack을 생성했다.

이번 작업은 production RAG runtime 연결이 아니며, backend production prompt, model 설정, EvidenceGateService, SkillAliasNormalizer production 코드는 변경하지 않았다. OpenAI/Anthropic/Google API도 호출하지 않았다.

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

Qwen2.5 7B independent judge artifact:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/judge-results/rag_hardcase_v1_20260630_1635_semantic_judge
```

기록된 Qwen judge commit:

```text
4bf1fc70b3acd8946d7eee8f06787477446466af
```

## 3. prompt template 경로

```text
ml/career-strategy-llm/judge_templates/rag_hardcase_top_llm_judge_prompt_v1.md
```

## 4. judge pack builder script 경로

```text
ml/career-strategy-llm/scripts/build_rag_hardcase_top_llm_judge_pack.py
```

## 5. CareerTunerAI judge pack path / commit SHA

전체 24건 pack:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/top-llm-judge-packs/rag_hardcase_v1_20260630_1635_top_llm_judge_pack
```

disagreement13 독립 subset pack:

```text
D:/dev/CareerTunerAI/benchmarks/rag-hardcase/top-llm-judge-packs/rag_hardcase_v1_20260630_1635_top_llm_judge_pack_disagreement13
```

CareerTunerAI commit:

```text
5be737de2f2c9fbfd5dc36d3d0f42654d23e254e
```

## 6. CareerTunerAIDocs report path / commit SHA

장문 계획 문서:

```text
D:/dev/CareerTunerAIDocs/areas/c-career-strategy/reports/75_rag_hardcase_top_llm_judge_pack_plan.md
```

CareerTunerAIDocs commit:

```text
0ef6bc1aa6c3193958fd50812b1965f859cee9b7
```

## 7. label taxonomy v2 요약

prompt template은 다음 primary label을 포함한다.

```text
POSITIVE_UNSUPPORTED_OWNERSHIP
IMPLIED_UNSUPPORTED_OWNERSHIP
RISK_WARNING_ONLY
JOB_REQUIREMENT_ONLY
COMPANY_CONTEXT_ONLY
CATALOG_FACT_ONLY
MISSING_SKILL_STATEMENT
NEGATED_OWNERSHIP_STATEMENT
SAFE_SUPPORTED_OWNERSHIP
SAFE_GENERIC_ADVICE
AMBIGUOUS_ATTRIBUTION
CONTRADICTORY_OUTPUT
FORMAT_OR_PARSE_PROBLEM
UNCLEAR
```

핵심은 Qwen judge가 흔들렸던 risk-warning, missing statement, negated ownership, positive ownership claim을 분리하는 것이다.

## 8. 생성된 prompt 파일 목록

전체 24건:

```text
prompts/openai_chatgpt_judge_prompt.md
prompts/anthropic_claude_judge_prompt.md
prompts/google_gemini_judge_prompt.md
```

전체 pack 내부 disagreement13 subset:

```text
prompts_subset_disagreement13/openai_chatgpt_disagreement13_judge_prompt.md
prompts_subset_disagreement13/anthropic_claude_disagreement13_judge_prompt.md
prompts_subset_disagreement13/google_gemini_disagreement13_judge_prompt.md
```

독립 disagreement13 pack:

```text
rag_hardcase_v1_20260630_1635_top_llm_judge_pack_disagreement13/prompts/openai_chatgpt_judge_prompt.md
rag_hardcase_v1_20260630_1635_top_llm_judge_pack_disagreement13/prompts/anthropic_claude_judge_prompt.md
rag_hardcase_v1_20260630_1635_top_llm_judge_pack_disagreement13/prompts/google_gemini_judge_prompt.md
```

## 9. all 24 packet / disagreement13 packet 생성 여부

```text
all packet count=24
disagreement13 packet count=13
```

전체 pack에는 `judge_packets.jsonl` 24건과 `judge_packets_disagreement13.jsonl` 13건이 함께 있다. 별도 disagreement13 pack에는 `judge_packets.jsonl` 13건이 있다.

## 10. PII 검사 결과

생성 manifest 기준:

```text
piiPatternDetectedCount=0
```

email, phone, resident-id, API-key-like 패턴을 검사했다. 입력 fixture는 synthetic data다.

## 11. 아직 하지 않은 것

- ChatGPT/Claude/Gemini 인터페이스의 현재 최상위 reasoning/analysis 모델에 실제 prompt를 붙여넣어 판정받지는 않았다.
- top LLM 응답 JSON을 저장하거나 취합하지 않았다.
- top LLM consensus와 human gold label 확정은 아직 수행하지 않았다.
- production RAG runtime 연결은 하지 않았다.

## 12. 다음 작업 후보

- ChatGPT/Claude/Gemini interface judge 결과 JSON 수집.
- top LLM 응답 schema validator와 aggregator 추가.
- Qwen/offline/top LLM/human disagreement matrix 작성.
- human gold label 확정 후 hard-case fixture v2 regression set 반영.
- RAG runtime 판단은 top LLM judge 취합 전까지 `KEEP_RAG_DISABLED` 유지.

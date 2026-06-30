# RAG hard-case benchmark fixture v1

## 1. 작업 목적
reports/67 의 RAG 재도입 기준을 실제 offline 평가로 옮기기 위한 첫 단계다. 이번 작업은 production RAG 연결이 아니라, synthetic hard-case fixture 와 A/B payload 생성 골격을 만드는 것이다.

비교 구조:

- A: 3B LoRA only baseline (`profile + job`)
- B: 3B LoRA + structured evidence buckets (`profile + job + evidenceBuckets`)

## 2. fixture 경로
- `ml/career-strategy-llm/data/rag_hardcase_benchmark/rag_hardcases_v1.jsonl`

fixture 는 synthetic data 만 사용한다. 실제 사용자 데이터, raw model output, API key, 환경변수 값은 포함하지 않는다.

## 3. fixture case 목록
| caseId | category | mustNotClaimOwned |
| --- | --- | --- |
| `RAG-HC-001` | `java_vs_javascript` | `Java` |
| `RAG-HC-002` | `apache_spark_vs_spark` | `Spark Streaming` |
| `RAG-HC-003` | `sql_vs_mssql` | `MSSQL` |
| `RAG-HC-004` | `sql_vs_mysql` | `MySQL` |
| `RAG-HC-005` | `sql_vs_postgresql` | `PostgreSQL` |
| `RAG-HC-006` | `react_vs_react_native` | `React Native` |
| `RAG-HC-007` | `spring_vs_spring_boot` | `Spring Boot` |
| `RAG-HC-008` | `nextjs_vs_javascript` | `JavaScript` |
| `RAG-HC-009` | `job_requirement_as_user_owned` | `Go`, `Kubernetes` |
| `RAG-HC-010` | `company_context_as_profile` | `Kafka` |
| `RAG-HC-011` | `missing_skills_as_matched` | `SQL`, `Tableau` |
| `RAG-HC-012` | `certificate_requirement_as_owned` | `정보처리기사`, `정보보안기사` |

## 4. 각 case의 risk category
- `java_vs_javascript`: 짧거나 유사한 기술명 conflation.
- `apache_spark_vs_spark`: 허용 alias 와 별도 세부 기술 확장을 함께 검증.
- `sql_vs_mssql`, `sql_vs_mysql`, `sql_vs_postgresql`: 일반 SQL 과 특정 DB 제품 혼동.
- `react_vs_react_native`, `spring_vs_spring_boot`, `nextjs_vs_javascript`: compound/framework boundary 혼동.
- `job_requirement_as_user_owned`: 공고 요구사항을 사용자 보유 역량처럼 단정.
- `company_context_as_profile`: 회사 기술스택을 사용자 프로필 근거처럼 단정.
- `missing_skills_as_matched`: 부족 역량을 매칭/보유 역량처럼 단정.
- `certificate_requirement_as_owned`: 자격증 요구사항을 사용자 보유 자격처럼 단정.

## 5. validator 경로와 실행 결과
경로:

```text
ml/career-strategy-llm/scripts/validate_rag_hardcase_fixture.py
```

실행:

```bash
cd ml/career-strategy-llm
python scripts/validate_rag_hardcase_fixture.py data/rag_hardcase_benchmark/rag_hardcases_v1.jsonl
```

결과:

```text
[validate_rag_hardcase_fixture] path=data\rag_hardcase_benchmark\rag_hardcases_v1.jsonl
  cases=12 categories=12
  OK fixture schema valid
```

검증 항목: JSONL 파싱, `caseId` 중복, 필수 필드, 4개 evidence bucket 존재, `mustNotClaimOwned` 비어 있지 않음, `profile.skills`/`job.requiredSkills`, evidence item 의 `sourceType`/`sourceId`/`text`, 기본 개인정보 패턴 부재.

## 6. benchmark runner skeleton 경로
경로:

```text
ml/career-strategy-llm/scripts/run_rag_hardcase_benchmark.py
```

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

## 7. variant A/B payload 구조
Variant A:

```json
{
  "variant": "A_lora_only",
  "input": {
    "profile": "...",
    "job": "..."
  }
}
```

Variant B:

```json
{
  "variant": "B_structured_evidence_buckets",
  "input": {
    "profile": "...",
    "job": "...",
    "evidenceBuckets": {
      "userEvidence": [],
      "jobRequirements": [],
      "catalogFacts": [],
      "companyContext": []
    }
  }
}
```

`evidenceBuckets` 는 단일 긴 `retrievedContext` 문자열로 합치지 않는다. 각 item 은 `sourceType`, `sourceId`, `text` 를 보존한다.

## 8. 평가 지표
runner output schema 에 다음 metric 자리를 마련했다.

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

이번 PR은 payload generation skeleton 이므로 위 지표를 실제 계산하지 않는다.

## 9. raw output 저장 정책
- `reports/generated/` 는 `.gitignore` 대상이다.
- dry-run payload 와 향후 raw output 은 main repo 에 커밋하지 않는다.
- 실제 모델 출력, gate result, semantic judge packet 이 필요하면 CareerTunerAI 또는 별도 artifact 경로에 저장한다.
- production backend, production prompt, user-facing API 를 호출하지 않는다.

## 10. 아직 하지 않은 것
- 실제 3B LoRA / Ollama 호출.
- R3 gate evaluator 와 runner 의 직접 연결.
- semantic judge 실행.
- A/B aggregate metric 계산.
- generated payload commit.

## 11. 다음 작업 후보
- dry-run payload 를 기반으로 4090 또는 CareerTunerAI artifact 환경에서 실제 A/B 호출을 실행한다.
- runner 에 model call adapter 를 추가하되 production backend 대신 offline Ollama endpoint 만 사용한다.
- R3 gate reason schema 와 semantic judge packet 을 runner output 에 연결한다.
- reports/67 성공/실패 기준에 맞춰 aggregate summarizer 를 추가한다.

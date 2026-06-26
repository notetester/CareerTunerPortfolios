# R2 — 3B LoRA + retrievedContext prompt 오프라인 비교 (2026-06-26)

> reports/50~52(RAG 설계·R1 lexical·R1b embedding) 위에, retrieval 로 만든 `retrievedContext` 가 **실제 3B LoRA 응답 품질**에 도움이 되는지 A/B 오프라인 비교. **backend 통합 아님** — Spring API·서비스 runtime prompt·기본 모델·LangChain/Spring AI 변경 없음. **synthetic 케이스(실제 개인정보 미사용).**
> **실행 상태(2026-06-26 갱신): SSH 복구 후 4090 GPU 에서 실제 A/B 실행 완료.** 결과: retrievedContext 주입이 **계약·안전 지표를 유지**하고 context 를 실제 활용(overlap 0.40)하나, **합성 케이스가 쉬워 개선 폭은 불명확**(A 도 이미 perfect). → 효과 드러날 어려운 케이스로 R2b 재평가 후 R3(아래 §7~10·13).

## 1. #149 merge 확인 결과
PR #149(R1b local embedding) **merged=True** (merge_commit `ac94eaa`), dev 에 `rag_poc/`(embedding_retriever 등) + `reports/52` 반영 확인. dev 최신에서 LEE-JEONG-GUCK 동기화 후 작업.

## 2. R2 목표
A(3B LoRA only) vs B(3B LoRA + retrievedContext) 를 **같은 입력·retrievedContext 유무만** 다르게 비교해, RAG 근거 주입이 (1)contract success 유지 (2)E1 grounding 위반 감소 (3)HALLUCINATED_SKILL raw/normalized/semantic 감소 (4)CJK/PARSE_FAIL 미악화 (5)latency 감당 가능 (6)점수/applyDecision 미침범 인지 검증. C(7B base)는 reports/49 수치 **참고용**(재실행 안 함).

## 3. 구현 파일 (rag_poc, backend 무관)
- `scripts/rag_prompt_builder.py` — retrievedContext 부가 주입(점수/판단 불변, sourceType/sourceId/text 만, score/vector metadata 제거). system 은 `synth_prompts.FIT_EXPLAIN_SYS` 재사용(train/serve 일관) + B 변형에 RAG 지침 addendum.
- `scripts/build_rag_eval_cases.py` — synthetic 8 케이스 + A/B pair(같은 caseId, ctx 유무만 차이).
- `scripts/compare_lora_with_rag.py` — A/B 실행기. `--mock`(오프라인 하니스 검증) / `--base-url`·`--model`(실제 Ollama). 채점은 `eval_fit_model.evaluate` 재사용.
- `tests/test_rag_prompt_builder.py`(5) · `tests/test_rag_eval_cases.py`(5).

## 4. A/B 비교 설계
| 변형 | retrievedContext | 그 외 입력 |
| --- | --- | --- |
| A `lora_only` | 없음 | 동일 |
| B `lora_with_retrieved_context` | 있음(case 별) | 동일 |
- 동일 모델 `careertuner-c-career-strategy-3b`, repeat 2. **차이는 retrievedContext 유무뿐**(test_rag_eval_cases 로 보장).
- 지표: contract success·json_parse·CJK·PARSE_FAIL·E1 grounding·E2 high/review·raw/normalized hallucinated skill·latency + **RAG 전용**(context 제공/overlap, 향후 supported/unsupported claim). semantic valid_error 는 normalized residual>0 시 judge packet 절차(별도) — 하니스만으로 단정 안 함.

## 5. 평가 케이스 구성 (synthetic 8)
SQL/SQLD grounding(MSSQL 날조 억제 기대) · Spring Boot/REST API catalog · Spark/파이프라인 · 자격증 추천(SQLD) · 회사/직무 조사 context · 복수 catalog · APPLY-with-context(점수 불변 확인) · **negative control(retrievedContext 비어있음)**. 모두 synthetic(이메일/전화/주민번호 없음 — test 로 확인), fitScore/applyDecision 서버값 고정.

## 6. retrievedContext 예시 (rag-sqld-001, 변형 B)
```json
{"retrievedContext": [
  {"sourceType":"certification_catalog","sourceId":"cert-sqld","text":"SQLD는 SQL 기본 이해와 데이터 모델링 역량을 검증하는 국가공인 자격입니다."},
  {"sourceType":"skill_catalog","sourceId":"skill-springboot","text":"Spring Boot는 Java 기반 백엔드 프레임워크로 REST API 개발과 의존성 관리에 쓰입니다."}
]}
```
`score`/`vectorDistance`/`fitScore`/`applyDecision` 없음 — builder 가 제거·미생성(test_3).

## 7. 실행 여부 (완료)
- **하니스 오프라인 검증(완료):** `compare_lora_with_rag.py --mock --repeat 2` 정상.
- **실제 3B LoRA A/B(완료):** 2026-06-26 4090 SSH 복구(self-heal 부트스트랩, ACL `SYSTEM/Administrators:F` 교정) 후 **GPU 에서 실행**(`careertuner-c-career-strategy-3b`, repeat 2, 8케이스). Ollama GPU 정상 확인. raw 는 CareerTunerAI `results/2026-06-26-rag-r2-prompt-001/rag_r2_ab_raw.json`(main repo 미커밋).
- 실행 명령(CareerTunerAI `jobs/open/2026-06-26-rag-r2-prompt-001.json`):
  ```bash
  cd ml/career-strategy-llm
  python rag_poc/scripts/compare_lora_with_rag.py --base-url http://localhost:11434/v1 \
    --model careertuner-c-career-strategy-3b --repeat 2 \
    --out-dir <CareerTunerAI>/results/2026-06-26-rag-r2-prompt-001
  ```
  raw outputs 는 CareerTunerAI results 에만, main repo 미커밋.

## 8. 지표 결과 (실측, golden 아닌 synthetic 8케이스 × repeat 2 = 변형당 16 run, GPU)
| 지표 | A `lora_only` | B `lora_with_retrieved_context` |
| --- | --- | --- |
| contract success | 1.0 | 1.0 |
| json_parse_rate | 1.0 | 1.0 |
| CJK leak | 0.0 | 0.0 |
| E1 grounding violation | 0 | 0 |
| E2 high | 0 | 0 |
| raw / normalized hallucination | 0 / 0 | 0 / 0 |
| avg latency | 2098.3 ms | 1975.5 ms |
| **retrievedContext used(overlap)** | 0.0 | **0.3975** |
- normalized residual **0** → semantic judge 후보 없음 → `semantic_hallucination_count = 0 (후보 없음)`. (잔여>0 이었다면 judge packet 절차였음 — 이번엔 불필요.)

## 9. RAG 가 개선한 점
- **계약/안전 무회귀 + context 실제 활용:** B 가 retrievedContext 를 실제로 참조(token overlap **0.40**)하면서 contract success·json·CJK·E1·hallucination 을 모두 A 와 동일(전부 perfect)하게 유지. latency 도 증가 없음(B 1976ms ≤ A 2098ms, 노이즈 범위). 즉 **RAG 주입이 품질을 깨지 않고 안전하게 얹힌다**는 것을 실모델로 확인. 점수/applyDecision 불변(설계대로).

## 10. RAG 가 악화시킨 점
- **측정상 악화 없음.** 단 **개선도 측정 안 됨** — 합성 8케이스가 쉬워 A 도 이미 success 1.0·hallucination 0·E1 0 이라 **개선 폭(headroom)이 없다.** 즉 이 케이스셋은 RAG 효과를 *증명도 반증도* 못 한다(효과 불명확). RAG 가치는 base 가 grounding 에 실패하는 어려운 케이스(예: reports/49 의 MSSQL 류 입력 밖 제품명, E1 위반·hallucination 발생 케이스)에서만 드러난다.

## 11. 개인정보 / 보안 확인
- 케이스 synthetic — 이메일/전화/주민번호 패턴 없음(test_7). 실제 이력서/지원 건 미사용.
- retrievedContext 에 score/fitScore/applyDecision 없음(test_3). 점수/판단은 rule engine/server 소유, builder 미생성.
- raw outputs 는 CareerTunerAI 에만(main repo 미커밋). OpenAI embedding/외부 API 호출 없음.

## 12. backend 미변경 확인
PR diff 는 `rag_poc/`(scripts/tests) + `reports/53` 뿐. `backend/` 파일·서비스 runtime prompt·기본 모델 변경 0. Ollama 호출은 (복구 후) 오프라인 평가 목적 한정.

## 13. 다음 단계 제안
**판정: 효과 불명확(이 케이스셋 한정) → R3 backend 통합 보류, R2b 어려운 케이스로 재평가 우선.** RAG 는 안전하게 얹히나(무회귀·context 활용 0.40) 합성 쉬운 케이스에선 개선이 안 보임.
- **R2b(권장):** RAG 효과가 드러날 **어려운 케이스셋** 구성 — base 가 grounding 에 실패하는 입력(MSSQL 류 입력 밖 제품명, E1 위반·hallucination 유발) + golden60 중 base 가 틀린 케이스. A/B 재측정으로 valid_error/E1 감소를 본다.
- (환경 구비 시) R1b 의 실제 의미 embedding(bge-m3/e5)으로 retrievedContext 품질↑ 후 A/B 재측정.
- 유의미 개선 확인 시에만 **R3 backend(Spring AI/LangChain4j) 통합**(점수/판단·E1/E2 불변).

## 자체 검증
- ✅ PR diff 에 backend 파일 없음(rag_poc + reports/53).
- ✅ 서비스 runtime prompt 미수정(synth_prompts.FIT_EXPLAIN_SYS 는 import 재사용, 변경 없음).
- ✅ 실제 개인정보 없음(synthetic, test_7).
- ✅ raw result JSON/log main repo 미커밋(실제 실행 자체가 보류, raw 는 CareerTunerAI 전용).
- ✅ retrievedContext 에 score/fitScore/applyDecision 없음(test_3).
- ✅ A/B pair 가 retrievedContext 유무만 다름(test_6).
- ✅ fresh checkout 에서 테스트 실행 가능(mock·builder·tests 의존성 0).

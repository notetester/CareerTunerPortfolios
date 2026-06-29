# R2b — RAG 가 도움 되는 어려운 케이스(hard-case)에서 A/B 재평가 **(실측 완료)** (2026-06-26 설계 · 2026-06-27 실측)

> reports/53(R2)에서 합성 8케이스가 **너무 쉬워** A(LoRA only)도 이미 success 1.0·E1 0·hallucination 0 → RAG 개선 폭(headroom)이 없어 효과가 불명확했다. R2b 는 **base 가 grounding/hallucination 에서 실제로 헷갈릴 hard-case**(입력 밖 제품명 날조·유사기술 혼동·자격 catalog 근거 등)로 A/B 를 다시 측정한다. **backend 통합 아님** — Spring API·서비스 runtime prompt·기본 모델·LangChain/Spring AI 변경 없음. **synthetic 케이스(실제 개인정보 미사용).**
> **실행 상태(2026-06-27): 실측 완료.** 4090 복구 후 origin/dev 격리 worktree(타 팀원 체크아웃 미간섭) + 로컬 Ollama(careertuner-c-career-strategy-3b)로 repeat 2 A/B 실행. raw → CareerTunerAI `results/2026-06-26-rag-r2b-hardcase-001/`(commit f4fbc90). 수치·판정은 §8·§13.

## 1. #152 merge 확인 결과
PR #152(R2 RAG A/B 실측) **merged** (merge commit `d7ea37d`, dev 반영). 이어 #153 도 merge(`3cb480b`). 본 작업은 dev 최신에서 `LEE-rag-r2b-hardcase` 브랜치를 끊어 진행. R2b 는 R2 의 후속(어려운 케이스 재평가)이다.

## 2. R2b 목표
A(3B LoRA only) vs B(3B LoRA + retrievedContext) 를 **같은 입력·retrievedContext 유무만** 다르게, 단 이번엔 **base 가 틀리기 쉬운 hard-case** 로 비교해 RAG 근거 주입이 (1)허용 밖 스킬 날조(hallucinated_skill raw/normalized) 감소 (2)E1 grounding 위반 감소 (3)E2 high(입력 밖 제품코드 날조) 감소 (4)contract success·json·CJK 무회귀 (5)latency 감당 (6)점수/applyDecision 미침범 을 검증한다. 핵심은 R2 와 달리 **A 에 개선 여지(headroom)가 있는 케이스셋** 을 쓴다는 것.

## 3. 구현 파일 (rag_poc, backend 무관)
- `rag_poc/fixtures/hard_cases.jsonl` (신규) — synthetic hard-case **16건**(8 hardType × 2). 각 케이스에 base input(profileSnapshot/jobPostingSummary/fitScore/applyDecision/matchedSkills/missingSkills + 평가기 스캔 키 requiredSkills/missingRequiredSkills/companyName/profileCertificates) + expected.allowedSkills + retrievedContext(sourceType/sourceId/text 만).
- `rag_poc/scripts/build_rag_hard_cases.py` (신규) — hard_cases.jsonl 로드 + A/B pair 생성. **build_rag_eval_cases.build_pairs + rag_prompt_builder 를 import 재사용**(중복 구현 금지). A=ctx없음, B=ctx있음, 같은 caseId, 차이는 retrievedContext 유무뿐.
- `rag_poc/scripts/compare_lora_with_rag_hard_cases.py` (신규) — hard cases A/B 실행기. **compare_lora_with_rag 의 run_variant/aggregate/VARIANTS 를 import 재사용**(케이스만 build_hard_pairs 로 교체). `--mock` / `--base-url`·`--model` / `--out-dir`. 채점 eval_fit_model.evaluate. RAG 전용: per-case A vs B 비교로 rag_improvement/rag_regression/neutral 카운트 + context overlap + semantic judge 후보(normalized residual>0).
- `rag_poc/tests/test_rag_hard_cases.py` (신규, 8 test) — fixture synthetic / A·B 같은 caseId / 차이 ctx 유무뿐 / retrievedContext 에 score·fitScore·applyDecision 없음 / negative control 포함 / MSSQL vs SQL 포함 / 점수·판단 양 variant 불변 / 8 hardType 전수 포함. stdlib unittest, fresh checkout 실행 가능.
- `reports/54_rag_r2b_hardcase_eval.md` (이 문서).

## 4. hard-case 구성 (synthetic 16건 = 8유형 × 2)
| hardType | 개수 | RAG 가 막아야 할 실패 |
| --- | --- | --- |
| `mssql_vs_sql` | 2 | 허용엔 일반 SQL 만 — 특정 제품 SQL(MSSQL 등) 학습스킬 날조(reports/49 유일 valid_error 유형) |
| `fake_product_name` | 2 | 입력 밖 제품코드(CRM465/ERP900류 E2 high)·벤더 솔루션명 날조 |
| `cert_catalog_grounding` | 2 | 자격증 catalog 근거(SQLD/정보처리기사) 없으면 임의 자격증 추천 |
| `company_research_context` | 2 | 회사/직무 조사 context 없으면 일반론 또는 회사 사실 날조 |
| `similar_stack_confusion` | 2 | Java/Spring Boot/JPA vs Node/Express 혼동(허용 밖 스킬) |
| `data_role_confusion` | 2 | 데이터직 Spark/Pandas/SQL/ETL/Airflow 도구 혼동 |
| `negative_control` | 2 | retrievedContext 빈 케이스 — B==A(무근거시 무해·builder 견고성) |
| `score_decision_invariant` | 2 | ctx 있어도 fitScore/applyDecision 불변(APPLY/보유 cert grounding 포함) |

모두 synthetic(이메일/전화/주민번호 패턴 없음 — test_1). fitScore/applyDecision 은 서버 입력값 고정(LLM 불변). retrievedContext 는 sourceType/sourceId/text 만(score/fitScore/applyDecision 절대 없음 — test_4).

## 5. A/B 비교 설계
| 변형 | retrievedContext | 그 외 입력 |
| --- | --- | --- |
| A `lora_only` | 없음 | 동일 |
| B `lora_with_retrieved_context` | 있음(case 별) | 동일 |
- 동일 모델 `careertuner-c-career-strategy-3b`, repeat 2. **차이는 retrievedContext 유무뿐**(test_3 로 보장).
- 지표: contract success·json_parse·CJK·E1 grounding·E2 high·raw/normalized hallucinated skill·latency + **RAG 전용**(context overlap, per-case rag_improvement/rag_regression/neutral, headroom 케이스 수). semantic valid_error 는 normalized residual>0 시 judge 필요 후보로만 기록(하니스 단독 단정 안 함).

## 6. retrievedContext 예시 (hard-mssql-001, 변형 B)
```json
{"retrievedContext": [
  {"sourceType":"skill_catalog","sourceId":"skill-sql","text":"SQL은 관계형 데이터베이스를 다루는 표준 질의 언어로, 특정 벤더 제품(예: 상용 RDBMS)과 구분되는 일반 역량입니다."}
]}
```
`score`/`vectorDistance`/`fitScore`/`applyDecision` 없음 — builder 가드(test_4). 허용 allowedSkills 엔 일반 SQL 만 있고 MSSQL 은 없다(test_6) — 모델이 MSSQL 을 학습스킬로 만들면 입력 밖 제품명 날조.

## 7. 실행 여부
- **하니스 오프라인 검증(완료):** `compare_lora_with_rag_hard_cases.py --mock --repeat 2` **무크래시** 확인. `build_rag_hard_cases.py` 정상(16 pair). 테스트 8/8 통과, 기존 rag_poc 테스트 6종 무회귀.
- **실제 3B LoRA A/B(미실행 — 4090 timed out):** 2026-06-26 SSH 시도 2회 모두 `Connection timed out`(박스 sleep/off 또는 Tailscale 드롭 추정 — publickey 거부 아님). fallback 으로 job spec·report skeleton 작성, **raw 미커밋**.
- 실행 명령(CareerTunerAI `jobs/open/2026-06-26-rag-r2b-hardcase-001.json`, SSH 복구 후 4090 에서):
  ```bash
  cd ml/career-strategy-llm
  python rag_poc/scripts/compare_lora_with_rag_hard_cases.py --base-url http://localhost:11434/v1 \
    --model careertuner-c-career-strategy-3b --repeat 2 \
    --out-dir <CareerTunerAI>/results/2026-06-26-rag-r2b-hardcase-001
  ```
  raw outputs 는 CareerTunerAI `results/2026-06-26-rag-r2b-hardcase-001/` 에만, main repo 미커밋.
- **미실행 사유:** 4090 SSH `Connection timed out`(인증이 아니라 박스 도달 불가). 복구 시 위 command 한 줄로 실측 가능.

## 8. 지표 결과
**실측 완료(2026-06-27, 4090 Ollama, repeat 2 → variant 당 n=32).** raw → CareerTunerAI `results/2026-06-26-rag-r2b-hardcase-001/rag_r2b_hardcase_ab_raw.json`(commit f4fbc90, `mock=false`).

| 지표 | A `lora_only` | B `lora_with_retrieved_context` |
| --- | --- | --- |
| contract success | 0.938 | **1.0** ↑ |
| json_parse_rate | 1.0 | 1.0 |
| CJK leak | 0.062 | **0.0** ↓ |
| E1 grounding violation | 3 | **5** ↑(악화) |
| E2 high(제품코드 날조) | 0 | 0 |
| raw / normalized hallucination | 0 / 0 | 0 / 0 |
| avg latency | 1796 ms | 1706 ms |
| retrievedContext used(overlap) | 0.0 | 0.299 |

per-case: headroom(A 위반>0) **4/16** · rag_improvement **3** · rag_regression **3** · neutral **10** (net wash).
- improvement: `hard-mssql-001`(A E1:1→B 0) · `hard-fakeprod-004`(A contract_fail 2→B 0) · `hard-invariant-016`(A E1:1→B 0)
- regression: `hard-cert-006`(B E1:0→1) · `hard-data-011`(B E1:0→2) · `hard-data-012`(B E1:0→1) — **전부 E1 grounding 증가**
- semantic judge 필요 후보(normalized residual>0): **없음**(양 variant hallucination 0/0).

## 9. RAG 가 개선한 점
실측 improvement 3건 — 공통적으로 **retrievedContext 가 '구분(disambiguation)'을 제공할 때** 개선됐다.
- `hard-mssql-001`(MSSQL vs SQL): ctx 가 "SQL 은 일반 역량, 특정 제품(MSSQL)과 구분"을 명시 → A 의 E1 과claim(부족 역량을 보유로 서술)을 B 가 해소(E1 1→0).
- `hard-fakeprod-004`(입력 밖 제품명): A 는 contract fail 2건(CJK leak 등으로 success 깨짐) → B 가 ctx 근거로 안정화(fail 2→0). 전체 success 0.938→1.0, CJK 0.062→0 의 주된 원인.
- `hard-invariant-016`(score/decision 불변): A E1 1→B 0.
또한 B 는 ctx 를 실제 활용(overlap 0.0→0.299)했고 latency 회귀 없음(1796→1706ms). fitScore/applyDecision 은 양 variant 불변(§10).

## 10. RAG 가 악화시킨 점
실측 regression 3건 — **전부 E1 grounding 위반 증가**이고, 공통 원인은 **'직무 요건을 나열하는 ctx'를 모델이 '지원자 보유'로 혼동(conflation)** 한 것이다.
- `hard-data-011`·`hard-data-012`(데이터 직무 혼동): ctx 가 Spark/Pandas/ETL 등 **공고 요구 스킬**을 언급 → 모델이 부족 역량을 보유로 서술(E1 0→2, 0→1).
- `hard-cert-006`(자격 catalog): ctx 의 자격 설명을 보유로 과claim(E1 0→1).
즉 RAG 의 grounding 효과는 **양방향**이다 — ctx 가 '구분'을 주면 도움(§9), '직무 요건 나열'이면 보유 conflation 을 유발해 악화. negative control 2건(`hard-negctrl-013/014`)은 ctx 0건이라 B==A(악화 없음 확인), score_decision_invariant 2건은 fitScore/applyDecision 불변 확인.

## 11. 개인정보 / 보안 확인
- 케이스 synthetic — 이메일/전화/주민번호 패턴 없음(test_1). 실제 이력서/지원 건 미사용. 회사명은 "회사 A~P" placeholder.
- retrievedContext 에 score/fitScore/applyDecision 없음, 키는 sourceType/sourceId/text 만(test_4). 점수/판단은 rule engine/server 소유, builder 미생성.
- raw outputs 는 CareerTunerAI results 에만(main repo 미커밋, commit f4fbc90). 외부 API 호출 없음 — 로컬 Ollama(4090) careertuner-c-career-strategy-3b 만 사용.

## 12. backend 미변경 확인
PR diff 는 `rag_poc/`(fixtures/scripts/tests) + `reports/54` 뿐. `backend/` 파일·서비스 runtime prompt·기본 모델·LangChain/Spring AI 변경 0. `docs/ops`·`scripts/ops`·`.github` 미수정(다른 에이전트 담당). synth_prompts.FIT_EXPLAIN_SYS·eval_fit_model·build_rag_eval_cases·rag_prompt_builder 는 **import 재사용만**(수정 없음).

## 13. 다음 단계 판단
**판정: 실측상 RAG 효과는 net wash(rag_improvement 3 = rag_regression 3) — '하드케이스로 RAG 우위 입증'은 성립하지 않는다.** 단 메커니즘은 정보가치가 크다:
- RAG 는 **계약 안정성(success 0.938→1.0)·CJK(0.062→0)를 개선**하고 ctx 를 실제 활용(overlap 0.299)하며 latency 회귀 없음.
- 그러나 **E1 grounding 은 악화(3→5)** — ctx 가 직무 요건을 나열하면 모델이 '보유'로 conflation(§10). hallucination(allowed-skill 날조)은 양쪽 0/0 이라 이 차원엔 애초에 headroom 이 없었다.
- 따라서 **R3 backend(Spring AI/LangChain4j) 통합은 보류**한다 — 현 기준(rag_improvement > rag_regression, E2/hallucination 감소)을 충족하지 못한다. RAG 를 도입하려면 먼저 **grounding conflation 을 차단**해야 한다: ctx 를 '제품/자격 구분'형으로 스코프하거나, prompt 에서 '공고 요구 ≠ 지원자 보유'를 분리하거나, E1 guard 로 출력 교정(E1 guard 는 백엔드 미러라 프로덕션에선 이 위반을 잡지만, raw 모델 품질 자체가 악화함을 이 실측이 보여준다).
- semantic judge: normalized residual 양쪽 0 → judge 필요 후보 없음(이번 셋 valid_error 0).
- 후속 제안(별건): conflation 완화 prompt/ctx 스코프 A/B(R2c) 후 재측정. 측정 하니스 한정, 점수/판단·E1/E2·D·F 불변 유지.

## 자체 검증
- ✅ PR diff 에 backend 파일 없음(rag_poc + reports/54). docs/ops·scripts/ops·.github 미수정.
- ✅ 서비스 runtime prompt 미수정(synth_prompts.FIT_EXPLAIN_SYS·rag_prompt_builder import 재사용, 변경 없음).
- ✅ 기존 스크립트 재사용(build_pairs·rag_prompt_builder·run_variant·aggregate·evaluate import) — 중복 구현 없음.
- ✅ 실제 개인정보 없음(synthetic, test_1).
- ✅ raw result JSON/log main repo 미커밋(실측 미실행, raw 는 CareerTunerAI 전용).
- ✅ retrievedContext 에 score/fitScore/applyDecision 없음, 키 sourceType/sourceId/text 만(test_4).
- ✅ A/B pair 가 retrievedContext 유무만 다름(test_3), 점수/applyDecision 불변(test_7).
- ✅ negative control(test_5)·MSSQL vs SQL(test_6)·8 hardType 전수(test_8) 포함.
- ✅ fresh checkout 에서 테스트·mock 실행 가능(stdlib·의존성 0). mock --repeat 2 무크래시.

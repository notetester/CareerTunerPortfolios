# R2c — scoped/guarded retrievedContext 로 grounding conflation 완화 A/B/C (2026-06-29)

> R2b(reports/54) 실측에서 RAG(B)는 net wash 였고 **E1 grounding 이 3→5 로 악화**했다. 원인은 모델이
> retrievedContext 의 '직무 요구/catalog 정의'를 '지원자 보유 역량'으로 혼동(conflation)한 것. R2c 는 그
> conflation 을 직접 겨냥해, retrievedContext 에 **역할/소유/주장정책(contextRole/ownership/claimPolicy)** 을
> 분리하고 프롬프트 가드를 더한 **C 변형**을 추가해 A/B/C 로 비교한다. **backend 통합 아님** — Spring API·
> 서비스 runtime prompt·기본 모델·LangChain/Spring AI 변경 없음. rag_poc 하니스·reports 만. synthetic 전용.

## 1. 선행 확인
- **#168 merged**(merge `55c421dd`) — R2b 실측 결과가 dev 의 reports/54 에 반영됨("실측 완료").
- **R2b raw** = CareerTunerAI `results/2026-06-26-rag-r2b-hardcase-001`(commit `f4fbc90`) 존재.
- **4090 reachable**(chanssick) + Ollama `/v1` GPU(careertuner-c-career-strategy-3b·qwen2.5:3b-instruct).
- 메인 repo 가 타 팀원 브랜치(PARK-SEONG-HO)라 **건드리지 않고 origin/feat/c-rag-r2c 격리 worktree** 사용.

## 2. R2b 결과 요약 (출발점)
A=LoRA only, B=LoRA+retrievedContext. **net wash**: per-case improvement 3 = regression 3, neutral 10.
B 는 success(0.938→1.0)·CJK(0.062→0)는 개선했으나 **E1 grounding 은 3→5 로 악화**, regression 3건이 전부
E1 증가였다. 메커니즘: ctx 가 '직무 요구 스킬/자격 정의'를 나열하면 모델이 그것을 '지원자 보유'로 혼동
(예: catalog 가 "정보처리기사는 자료구조·알고리즘 검증"이라 했을 뿐인데 지원자가 자료구조·알고리즘을 보유한다고 서술).

## 3. R2c 목표
retrievedContext 를 넣되 **job requirement / catalog fact / company context / user evidence 를 명확히 분리**해
모델이 "공고에 있음 / 정의됨"을 "지원자가 보유함"으로 착각하지 않게 한다. 목표는 **C 가 B 대비 E1 grounding·
conflation 을 줄이는지**(회귀 없이) 확인하는 것.

## 4. A/B/C 실험 설계
| 변형 | retrievedContext | 비고 |
| --- | --- | --- |
| A `lora_only` | 없음 | 기존 자체모델만 |
| B `lora_with_retrieved_context` | 기존(역할 없음) | R2b 에서 쓴 방식 |
| C `lora_with_scoped_context` | **역할/소유/주장정책 부여 + claim guard** | source role·ownership 분리 |

- 같은 모델 `careertuner-c-career-strategy-3b`, 같은 hard_cases 16건, repeat 2 → 총 **96 call**(3변형×16×2).
- **차이는 C 만 scoped ctx + guard**(A/B/C 가 같은 base input 공유, test_7/test_8 로 보장).
- 지표: 기존(success/json/CJK/E1/E2/hallucination/latency/overlap) + **conflation 신규**
  (`job_requirement_as_user_owned` / `catalog_fact_as_user_owned` / `context_conflation` / `other_grounding_claim`).

## 5. scoped context schema
`build_rag_scoped_context.py` 가 sourceType → 역할을 결정론 매핑한다(score/vectorDistance 등은 화이트리스트로
제외, text 값-수준 점수/판단 누수는 기존 가드 재사용):

| sourceType | contextRole | ownership | claimPolicy |
| --- | --- | --- | --- |
| `job_posting`/`job_requirement` | `job_requirement` | `employer_required` | `do_not_treat_as_user_owned` |
| `skill_catalog`/`certification_catalog` | `catalog_fact` | `global_fact` | `definition_only_not_user_owned` |
| `company_research_summary` | `company_context` | `global_fact` | `context_only_not_user_owned` |
| `user_profile_summary` | `user_evidence` | `user_owned` | `may_treat_as_user_owned_if_text_supports` |
| (미지정) | `unknown_context` | `unknown` | `do_not_treat_as_user_owned` (보수) |

```json
{"sourceType":"certification_catalog","sourceId":"cert-info","text":"정보처리기사는 자료구조·알고리즘 등을 검증합니다.",
 "contextRole":"catalog_fact","ownership":"global_fact","claimPolicy":"definition_only_not_user_owned"}
```

## 6. prompt guard (C 변형 시스템 메시지 addendum)
```text
- contextRole=job_requirement 는 공고가 '요구'하는 역량이다. 거기 나온 기술을 지원자가 '보유'한다고 서술하지 마라.
- contextRole=catalog_fact 는 정의/설명 근거일 뿐, 지원자의 보유 역량 증거가 아니다(정의를 보유로 바꾸지 마라).
- contextRole=company_context 는 회사/직무 배경일 뿐, 지원자 보유 역량이 아니다.
- contextRole=user_evidence 의 내용만 지원자 보유 역량으로 서술할 수 있다.
- fitScore/applyDecision 은 서버 입력값 그대로 두고 절대 바꾸지 않는다.
```

## 7. 테스트 결과
- `test_rag_scoped_context.py` **11**(역할/소유/주장정책 부여·job_requirement/catalog 는 user_owned 아님·user_evidence 만 보유 가능·점수/판단 불변·score/vectorDistance 미주입·A/B/C 같은 base·C 만 scoped/guard·값-수준 누수 차단·UTF-8 guard).
- `test_rag_conflation_metrics.py` **8**(job_requirement/catalog 보유 혼동 감지·결핍/실보유 서술 오탐 없음·ctx 없으면 other·비JSON 무크래시·집계).
- 기존 rag_poc 7종 **무회귀**. cp949 가드는 4090 실콘솔에서 검증(env 없이 ✓ 출력, validate RC=0).

## 8. 실행 여부와 jobId
**실측 완료(2026-06-29, 4090 Ollama, repeat 2 → variant 당 n=32, 총 96 call).** jobId `2026-06-28-rag-r2c-scoped-context-001`.
4090 복구 후 origin/feat/c-rag-r2c **격리 worktree**(타 팀원 PARK-SEONG-HO 체크아웃 미간섭)에서 실행. raw →
CareerTunerAI `results/2026-06-28-rag-r2c-scoped-context-001/rag_r2c_scoped_ab_raw.json`(commit `654c08d`, `mock=false`).

## 9. 주요 지표 결과
| 지표 | A `lora_only` | B `+RAG` | C `+scoped RAG` |
| --- | --- | --- | --- |
| contract success | 0.938 | 0.969 | 0.969 |
| CJK leak | 0.062 | 0.031 | **0.0** |
| E1 grounding 위반 | 12 | **8** | 10 |
| E2 high | 0 | 0 | 0 |
| hallucination raw/residual | 0/0 | 0/0 | **1/1** |
| avg latency | 1843 ms | 1725 ms | 1786 ms |
| context overlap | 0.0 | 0.304 | 0.298 |
| **context_conflation(catalog)** | 0 | **8** | **8** |

⚠ **이번 run 의 절대값은 R2b run(reports/54)과 다르다**(생성 stochasticity, temp~0.2) — variant 비교는 **동일 run 내에서만** 유효.

## 10. conflation 개선/악화 케이스 (적대 검증 반영)
두 가지를 **분리**해서 본다 — (가) B↔C 우열은 노이즈, (나) **context 주입 자체의 conflation 은 구조적 신호**.

**(가) C 는 B 대비 conflation 을 못 줄였다 — 단 'no-op'은 아니다.** context_conflation(catalog)은 B=8, C=8 로 **net wash**다. 가드는 모델에 도달해 동작을 바꿨으나(E1 8→10, other_grounding 7→15, CJK 0.031→0, hallucination 0→1) catalog conflation 은 **케이스 재분배만** 했다:
- C 가 개선(C<B) **3건**: `hard-cert-006`(catalog 4→0, 큰 개선) · `hard-data-011`(1→0) · `hard-research-008`(E1 1→0) — 합 −5.
- C 가 악화(C>B) **5건**: `hard-mssql-002`(1→3) · `hard-fakeprod-004`(0→2) · `hard-stack-009`(1→2) · `hard-research-007`(E1 0→1) · `hard-negctrl-014` — 합 +5.
- 합산 0(8=8). per-case net improvement 3 < regression 5, **hallucination 1건 신규**(B 0→C 1) → C 가 B 보다 약간 나쁨.

**(나) context 주입이 conflation 을 새로 만든다(robust).** catalog conflation 이 **A=0 · B=8 · C=8** — no-context A 엔 없는 실패유형을 ctx 주입(B/C)이 도입한다. 이건 샘플링 노이즈가 아니라 **방향성 있는 구조적 효과**다.

⚠ **B-vs-C 는 단일 run 으로 단정 불가(노이즈 대역).** negative control `hard-negctrl-014` 는 A/B/C 모두 ctx 0(overlap 0.0)인 동일 입력인데 E1=2/0/2 로 갈렸다 — 순수 샘플링 노이즈. per-case 3:5 split 은 sign test **p=0.73**(비유의), 집계 E1 B=8 vs C=10 은 풀드 평균(~9)의 1 SD 이내. A 의 E1 도 R2b run([reports/54](54_rag_r2b_hardcase_eval.md)) 3 → 이번 run 12 로 요동(run 간 변동성). 절대 conflation 값은 한국어 보유/부정 휴리스틱(`_claimed_possessed`) 기반이라 크기 자체는 보수적으로 보되, **B-vs-C 비교는 동일 검출기라 대칭(공정)**.

## 11. latency / 운영 영향
avg latency A 1843ms / B 1725ms / **C 1786ms** — C 의 역할/가드 추가로 인한 latency 회귀 없음(B 와 유사, A 보다 빠름). scoped 컨텍스트가 프롬프트를 약간 늘리나 운영상 영향 미미.

## 12. 개인정보 / 보안 확인
- 케이스 synthetic — 이메일/전화/주민번호 패턴 없음(test_1, R2b 재사용). 실제 이력서/지원 건 미사용.
- scoped retrievedContext 에 score/fitScore/applyDecision/vectorDistance 없음(화이트리스트 SCOPED_KEYS), text 값-수준 점수/판단 누수 차단(scan_text_for_score_leak). 점수/판단은 rule engine/server 소유, builder 미생성.
- raw outputs 는 CareerTunerAI results 에만(main repo 미커밋). 외부 API 호출 없음 — 로컬 Ollama(4090)만.

## 13. backend / runtime / model 미변경 확인
- PR diff 는 `rag_poc/`(scripts/tests) + `reports/56` 뿐. `backend/`·서비스 runtime prompt·기본 모델·LangChain/Spring AI 변경 0. `docs/ops`·`scripts/ops`·`.github` 미수정.
- `_call_ollama`/`_mock_output`/`_ctx_support`/`aggregate`(compare_lora_with_rag), `build_hard_pairs`(build_rag_hard_cases), `evaluate`·grounding helpers(eval_fit_model), `scan_text_for_score_leak`(build_retrieved_context)는 **import 재사용만**(수정 없음). cp949 guard 는 기존 실행 스크립트에 출력 인코딩만 추가(채점 로직·지표 불변).

## 14. 다음 단계 판단
**판정(3: RAG 보류·재검토).** 두 결론을 분리해서 정리한다:

1. **scoped/guarded RAG(C)는 conflation 을 줄이지 못했다 — 프롬프트 수준 가드는 3B 모델에 불충분.** catalog conflation 이 B=C=8(net wash), per-case net 은 C 가 B 보다 약간 나쁘고(regression 5 > improvement 3) hallucination 1건이 새로 생겼다. 가드는 동작을 바꿨지만 catalog 혼동을 케이스 재분배만 했을 뿐 총량을 못 낮췄다. **B↔C 우열은 단일 repeat-2 run 으로 단정 불가**(sign test p=0.73, 1 SD 이내, negctrl 노이즈).
2. **그러나 context 주입 자체가 conflation 을 새로 만든다(robust, A=0 → B/C=8).** 이건 노이즈가 아니라 ctx 주입의 구조적 비용이다. 즉 RAG 의 문제는 'B가 C보다 낫냐'가 아니라 '**ctx 를 넣으면(scoping 해도) 모델이 직무요구/catalog 정의를 보유로 혼동하는 새 실패유형이 생긴다**'는 것.

→ **R3 backend(Spring AI/LangChain4j) 통합은 보류.** RAG 의 grounding conflation 은 컨텍스트 역할 분리·프롬프트 가드만으로 해결되지 않는다.
- 후속(측정 하니스 한정 · 점수/판단·E1/E2·D·F 불변):
  - **변동성 축소 먼저(R2c-rep)**: repeat 5~10 + 다중 seed 로 B-vs-C 신호가 노이즈를 넘는지, A-vs-context conflation(0→8)이 재현되는지 재측정. 단일 run 결정 금지.
  - **학습 기반**: scoped-context 예시로 LoRA 재학습(role 구분을 프롬프트가 아닌 가중치로). 단 기본 모델·D/F 불변 원칙상 별도 트랙.
  - **프로덕션 방어는 E1 guard**: E1 backend guard 가 이 conflation 을 라이브에서 잡으므로(미러) RAG 미도입 상태에서도 출력 안전은 유지. RAG 는 현재 raw 모델 품질을 못 올리므로 미도입.
- semantic judge: normalized residual A 0 / B 0 / **C 1** → C 에서 잔여 1건(judge 필요 후보)이나 하니스 단독으로 valid_error 단정 안 함(judge packet 절차 별도).

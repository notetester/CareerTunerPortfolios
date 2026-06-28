# R2d — evidence-gated RAG (근거 버킷 + evidence audit) A/B/C/D (2026-06-29)

> R2c(reports/56)에서 contextRole/claimPolicy + 프롬프트 가드를 넣어도 3B 모델은 conflation 을 못 줄였다
> (catalog B=C=8). 결론: **모델에게 '구분'을 맡기는 방향은 3B 에서 한계**. R2d 는 방향을 바꿔, 근거를
> **evidence bucket 으로 물리 분리**하고 출력을 **결정론 evidence audit**(userEvidence 가 뒷받침하지 않는 보유
> claim 검출)으로 검증한다. audit 은 서버측 evidence gate 의 프록시다. **backend 통합 아님** — Spring API·
> runtime prompt·기본 모델·LangChain/Spring AI 변경 없음. rag_poc 하니스·reports 만. synthetic 전용.

## 1. 선행 확인
- **#169 merged**(merge `e1dc3692`) — R2c 결과가 dev 의 reports/56 에 반영, compare_lora_with_scoped_rag·conflation detector 가 dev 에 존재(재사용).
- **R2c raw** = CareerTunerAI `results/2026-06-26-rag-r2c-scoped-context-001`(commit `654c08d`).
- **4090 reachable**(chanssick) + Ollama GPU(careertuner-c-career-strategy-3b). 메인 repo 타 팀원 브랜치(PARK-SEONG-HO) → **격리 worktree** 사용.

## 2. R2c 결과 요약 (출발점)
A=LoRA, B=+RAG, C=+scoped RAG. **context_conflation: A=0 · B=8 · C=8** — ctx 주입이 conflation 을 새로 만들고(구조적), scoped/guard(C)는 그걸 못 줄였다(net wash). per-case net 은 C 가 B 보다 약간 나쁨 + hallucination 1 신규. B↔C 우열은 단일 run 노이즈(p=0.73). → 프롬프트 수준 가드는 3B 에 불충분.

## 3. R2d 목표
모델에게 구분을 맡기지 말고 **하니스/서버에서 user-owned claim 의 근거를 제한**한다. user-owned claim 은
userEvidence 에서만 나올 수 있고, jobRequirements/catalogFacts/companyContext 는 보유 근거가 될 수 없다.
출력 후 **evidence audit** 으로 'userEvidence 가 뒷받침하지 않는 보유 claim'을 검출한다.

## 4. A/B/C/D 실험 설계
| 변형 | context | 비고 |
| --- | --- | --- |
| A `lora_only` | 없음 | 기존 자체모델 |
| B `lora_with_retrieved_context` | flat retrievedContext | R2b/R2c 의 B |
| C `lora_with_scoped_context` | 역할/주장정책 부여 + claim guard | R2c 의 C |
| D `lora_with_evidence_gated_context` | **evidence bucket 분리 + Evidence Rules + audit** | 신규 |

- 같은 모델·hard_cases 16건·repeat 2 → 총 **128 call**(4변형×16×2). 같은 base input(test 9), D 만 evidenceBuckets/Evidence Rules(test 10/11).
- audit 은 **출력을 바꾸지 않고 측정만** 한다(A/B/C/D 모두 동일 audit 적용 — D 는 명시 버킷, A/B/C 는 retrievedContext 에서 파생해 비교 일관).

## 5. evidence bucket schema
`build_rag_evidence_buckets.py` 가 sourceType → 버킷으로 분리(score/vector 제외, 값-수준 누수 차단). userEvidence 는
입력 matchedSkills 를 재표현(새 정보 아님 — 지원자 실보유 근거를 명시 분리).

| sourceType | bucket |
| --- | --- |
| `user_profile_summary` (+ matchedSkills 재표현) | **userEvidence** (보유 근거 가능) |
| `job_posting`/`job_requirement` | jobRequirements (요구) |
| `skill_catalog`/`certification_catalog` | catalogFacts (정의) |
| `company_research_summary` (+ 미지정) | companyContext (맥락) |

```json
{"evidenceBuckets":{"userEvidence":[{"sourceType":"user_profile_summary","sourceId":"profile-matched","text":"지원자는 SQL 경험이 있습니다."}],
 "jobRequirements":[],"catalogFacts":[{"sourceType":"skill_catalog","sourceId":"skill-spark","text":"Spark는 대용량 처리 엔진입니다."}],"companyContext":[]}}
```

## 6. evidence audit 설계 (R2d 핵심)
출력 후 결정론 검사(eval_fit_model grounding 재사용). user-owned claim 은 universe 스킬 중 '보유'로 서술된 것:
- `allowed_user_owned_set` = matchedSkills/profile/certs + **userEvidence 텍스트가 뒷받침하는 스킬**.
- `detect_unsupported_user_owned_claims` = 보유 서술됐으나 allowed 가 아닌 것(= gate 위반).
- `detect_requirement_as_owned` / `detect_catalog_fact_as_owned` = 위반을 출처별 분류.

신규 지표(기존 E1 대체 아님, 병렬): `unsupported_user_owned_claim_count`, `requirement_as_owned_count`,
`catalog_as_owned_count`, `evidence_gate_violation_count`, `evidence_gate_pass_rate`.

## 7. 테스트 결과
- `test_rag_evidence_buckets.py` **12**(버킷 분리·userEvidence 외 출처는 allowed 진입 불가[jobRequirements/catalogFacts/companyContext]·userEvidence 뒷받침 시 허용·점수/판단 불변·A/B/C/D 같은 base·D 만 buckets/Evidence Rules·score/vector 미주입·값 누수 차단).
- `test_rag_evidence_audit.py` **8**(requirement/catalog 보유 서술 검출·matched/userEvidence 뒷받침 통과·결핍 서술 오탐 없음·집계 pass_rate).
- 기존 rag_poc 9종 **무회귀**. cp949 가드(`_io_utils`)는 신규 스크립트에 내장, 4090 실콘솔 검증(env 없이 ✓).

## 8. 실행 여부와 jobId
**실측 완료(2026-06-29, 4090 Ollama, repeat 2 → variant 당 n=32, 총 128 call).** jobId `2026-06-28-rag-r2d-evidence-gate-001`.
origin/feat/c-rag-r2d 격리 worktree(PARK-SEONG-HO 미간섭). raw → CareerTunerAI `results/2026-06-28-rag-r2d-evidence-gate-001/rag_r2d_evidence_gate_raw.json`(commit `f46c726`, `mock=false`).

## 9. 주요 지표 결과
| 지표 | A `lora_only` | B `+RAG` | C `+scoped` | D `+evidence-gate` |
| --- | --- | --- | --- | --- |
| contract success | 0.906 | 1.0 | 0.969 | 0.969 |
| CJK leak | 0.094 | 0.0 | 0.031 | 0.031 |
| E1 grounding | **4** | 8 | 11 | 7 |
| hallucination raw/residual | 0/0 | 0/0 | 0/0 | 0/0 |
| avg latency | 1819 ms | 1779 ms | 1842 ms | **1655 ms** |
| context overlap | 0.54 | 0.35 | 0.35 | 0.38 |
| **context_conflation(catalog)** | **0** | 5 | 6 | **4** |
| **evidence_gate_violation** | 10 | 15 | 22 | **14** |
| **gate_pass_rate** | **0.697** | 0.625 | 0.569 | 0.65 |

⚠ 절대값은 run 마다 다름(stochasticity, temp~0.2) — 비교는 **동일 run 내에서만**.

## 10. evidence gate 개선/악화 케이스 (적대 검증 반영)
**A(no-context)가 모든 축에서 최선 — D 는 RAG 변형 중 'least-bad' 일 뿐, 게이팅이 RAG 를 정당화하진 못한다.**
- 정규화 **gate_pass_rate 순위: A(0.697) > D(0.65) > B(0.625) > C(0.569)**. ctx 주입(B/C/D)은 모두 A 대비 evidence-gate 위반을 늘린다 — evidence 게이팅으로도 ctx 주입의 grounding 비용을 모델 단계에선 못 없앤다. (raw gate_violation A 10 < D 14 < B 15 < C 22.)
- **D vs C(scoped): gate_violation 22→14, E1 11→7.** 단 C 의 raw 22 일부는 claim 수(C claimed 51 vs D 40) 탓 — per-claim 위반율로도 D(0.35) < C(0.43)라 D 가 낫지만 '확실히'는 과장. 버킷 분리 > inline 역할주석.
- **D vs B(current RAG): pass_rate 0.625→0.65, gate_violation 15→14(claimed 동일 40), catalog_as_owned 5→4, E1 grounding 8→7.** 그러나 **per-case D vs B = improvement 5 = regression 5(net wash), aggregate 1-unit/n=32 → 노이즈 대역**이라 D 가 B 를 이긴다고 단정 불가.
  - D 개선(catalog 혼동 차단): `hard-cert-006`(score 3→0) · `hard-data-011`(3→0) · `hard-mssql-002`(6→4) · `hard-research-008`(3→0).
  - D 악화: `hard-fakeprod-003/004`(0→2/3, 입력 밖 제품명을 버킷/규칙이 오히려 보유로 유도) · `hard-research-007` · `hard-data-012`.
- ⚠ negative control `hard-negctrl-013/014` 는 **retrievedContext 가 빈 fixture**(A/B/C 프롬프트엔 ctx 없음, D 만 matchedSkills→userEvidence 버킷 보유)인데 score 가 0/3/3/0·3/3/3/6 으로 갈린다 — run 노이즈 + D 의 userEvidence 버킷 framing 혼재. 두 건은 D−B 에서 −3/+3 으로 상쇄(net 0)라 aggregate 를 주도하지 않는다. (overlap 0.5 는 빈 ctx 시 합성 userEvidence 로 폴백한 값이지 distractor ctx 아님.)

## 11. E1 / conflation 분석
- **구조적 결론(robust, R2c 재확인): context 주입이 conflation 을 새로 만든다** — A catalog_conflation **0** vs B/C/D **5/6/4**. no-context A 가 E1(4)·gate_pass_rate(0.697) 모두 최선.
- D 는 B/C 보다 conflation/E1 을 줄이나 **A 수준엔 못 미친다** — evidence bucket+audit 은 ctx 주입의 부작용을 **완화하지만 모델 단계에선 제거하지 못한다**.
- hallucination 전 변형 0/0(이번 셋·run).
- ⚠ **변동성**: A E1=4(이번) vs R2c run A E1=12 — 같은 케이스가 run 간 요동. D 의 B 대비 1-unit 우위는 노이즈 대역 안일 가능성.

## 12. latency / 운영 영향
avg latency A 1819 / B 1779 / C 1842 / **D 1655ms** — D 가 버킷 직렬화로 프롬프트가 길어도 latency 최저(출력 수렴이 빠름). 운영상 회귀 없음.

## 13. 개인정보 / 보안 확인
- 케이스 synthetic — 이메일/전화/주민번호 패턴 없음(validate preflight). 실제 이력서/지원 건 미사용.
- evidenceBuckets 항목은 sourceType/sourceId/text 만(score/fitScore/applyDecision/vectorDistance 없음, test_12), text 값-수준 점수/판단 누수 차단(scan_text_for_score_leak). userEvidence 는 base input(matchedSkills) 재표현으로 새 PII 도입 없음. 점수/판단은 rule engine/server 소유, builder 미생성.
- raw outputs 는 CareerTunerAI results 에만(main repo 미커밋). 외부 API 호출 없음 — 로컬 Ollama(4090)만.

## 14. backend / runtime / model 미변경 확인
- PR diff 는 `rag_poc/`(scripts/tests) + `reports/57` 뿐. `backend/`·서비스 runtime prompt·기본 모델·LangChain/Spring AI 변경 0. `docs/ops`·`scripts/ops`·`.github` 미수정.
- `_call_ollama`/`_mock_output`/`_ctx_support`/`aggregate`(compare_lora_with_rag), `build_abc_pairs`(R2c), `_claimed_possessed`/`detect_conflation`/`aggregate_conflation`(R2c), `evaluate`·grounding helpers(eval_fit_model), `scan_text_for_score_leak`(build_retrieved_context)는 **import 재사용만**. evidence audit 은 출력 측정만(채점/E1/E2 로직 불변).

## 15. 다음 단계 판단
**판정(2: 효과 불명확 — 단, 결정론 gate 가 진짜 레버).** 모델 단계에서 D 는:
- **C(scoped inline)보다 확실히 낫다**(gate 22→14, E1 11→7) → ctx 를 넣어야 한다면 inline 역할주석보다 **evidence bucket 분리**가 낫다.
- **B(flat RAG)보다 미세하게 낫다**(전 지표 1-unit, per-case net wash) — 변동성 대역 안이라 단정 불가.
- **no-context A 는 못 이긴다**(A 가 E1·gate_pass_rate 최선) — ctx 주입의 구조적 conflation 비용은 evidence 게이팅으로도 모델 단계에선 제거되지 않는다.

**그러나 R2d 의 진짜 산출은 evidence audit 자체다** — userEvidence 가 뒷받침하지 않는 보유 claim 을 **결정론으로 검출·출처 분류**한다(서버측 gate 프록시). 모델에 자기규율을 맡기는 대신 **이 audit 을 서버측 post-filter** 로 써서 unsupported claim 을 reject/rewrite 하면 모델 변동성과 무관하게 출력 안전을 보장할 수 있다.

→ **RAG runtime 통합은 계속 보류**(모델 단계 ctx 주입은 conflation 을 못 없앰). 후속(측정 하니스 한정·점수/판단·E1/E2·D·F 불변):
- **R2e(권장)**: evidence audit 을 **실제 gate(reject/rewrite)** 로 적용해 게이트 통과 출력을 측정(구성상 violation 0). deterministic post-filter 가 RAG conflation 을 end-to-end 로 잡는지 검증.
- **변동성 축소**: repeat 5~10 + 다중 seed 로 D-vs-B 1-unit 우위가 신호인지 재측정(단일 run 결정 금지).
- 프로덕션 방어는 E1 guard + (도입 시) evidence gate 의존. RAG 미도입 상태에서도 출력 안전 유지.

# C Career Strategy AI Roadmap Checklist

Last updated: 2026-07-02
기준 branch: dev
기준 PR 범위: #174, #175, #180, #182, #183, #184, #186, #187, #188, #190, #191, #193, #198, #199, #200, #201, #210, #211, #212 포함

> **용어 정정 (2026-07-02):** 이 문서의 "RAG" 항목들이 가리키는 완료 실험은 true external retrieval RAG
> (런타임 벡터검색·웹·카탈로그 조회 — **미구현·미평가**)가 아니라 **evidence-bucket prompt augmentation**
> (이미 입력된 profile/job 재구조화 + 정적 합성 사실 주입)이다. `rag-hardcase` 는 legacy experiment name 으로
> 유지하고, 신규 벤치마크는 `evidence-attribution-*` 계열로 명명한다.
> 상세: [AIDocs report 77](../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md)

상태 표기:
- `[x] 완료`
- `[~] 보류 또는 조건부 유지`
- `[ ] 미완료`
- `[!] 주의 필요`

## 1. Production runtime 상태

- [x] 완료 — C 적합도 분석 runtime 은 `FallbackFitAnalysisAiService` primary dispatcher 기준으로 운영한다(PR #183, PR #184, [reports/65](../../docs/ai-reports/areas/c-career-strategy/reports/65_r3_evidence_gate_dev_integration_check.md)).
- [x] 완료 — R3 review-first evidence gate 가 backend service layer 에 연결되어 `safety`/gate result/evidence source 를 기록한다(PR #174, [reports/61](../../docs/ai-reports/areas/c-career-strategy/reports/61_rag_r3_review_first_gate_implementation.md)).
- [x] 완료 — gate 는 `fitScore`, `applyDecision`, `matchedSkills`, `missingSkills` 를 변경하지 않는다(PR #174, PR #175, [reports/61](../../docs/ai-reports/areas/c-career-strategy/reports/61_rag_r3_review_first_gate_implementation.md), [reports/62](../../docs/ai-reports/areas/c-career-strategy/reports/62_rag_r3_evidence_gate_user_evidence_hotfix.md)).
- [x] 완료 — 관리자 적합도 상세/목록, 홈, 대시보드에 gate 검토 상태와 최신 지원 건 기준 검토 대기 수가 연결되어 있다(PR #174, PR #175, [reports/62](../../docs/ai-reports/areas/c-career-strategy/reports/62_rag_r3_evidence_gate_user_evidence_hotfix.md)).
- [x] 완료 — dev 통합 기준 R3 계열과 Spring provider 회귀 테스트가 통과한 상태를 확인했다(PR #186, [reports/65](../../docs/ai-reports/areas/c-career-strategy/reports/65_r3_evidence_gate_dev_integration_check.md)).
- [x] 완료 — R3 자동 검증 보강과 체크리스트 정리를 반영했다(PR #187, [reports/66](../../docs/ai-reports/areas/c-career-strategy/reports/66_r3_auto_verification_and_ai_checklist.md)).
- [~] 보류 또는 조건부 유지 — RAG runtime 자동 주입과 rewrite 자동 사용자 노출은 현재 production runtime 에 연결하지 않는다([reports/60](../../docs/ai-reports/areas/c-career-strategy/reports/60_rag_r3_pre_backend_gate_design.md), [reports/61](../../docs/ai-reports/areas/c-career-strategy/reports/61_rag_r3_review_first_gate_implementation.md)).

## 2. Model / benchmark 상태

- [x] 완료 — 3B LoRA 계열은 C fit analysis 설명 생성의 현재 기준 모델로 유지한다([model-card](model-card.md), [reports/49](../../docs/ai-reports/areas/c-career-strategy/reports/49_7b_smoke_benchmark_result.md)).
- [x] 완료 — 7B base smoke 결과만으로는 3B LoRA 교체 근거가 부족하다고 판단했다([reports/49](../../docs/ai-reports/areas/c-career-strategy/reports/49_7b_smoke_benchmark_result.md)).
- [x] 완료 — golden/eval 계열 하니스와 4090 실행 명령 아카이브가 남아 있다([reports/25](../../docs/ai-reports/areas/c-career-strategy/reports/25_4090_eval_reliability_commands.md), [reports/35](../../docs/ai-reports/areas/c-career-strategy/reports/35_4090_golden60_eval_commands.md)).
- [~] 보류 또는 조건부 유지 — 7B 전환, 7B LoRA 재학습, GGUF 재생성은 RAG/evidence gate 이후 별도 재평가 전까지 보류한다([reports/49](../../docs/ai-reports/areas/c-career-strategy/reports/49_7b_smoke_benchmark_result.md)).
- [x] 완료 — model-card 에 R3 production safety 상태를 반영했다([model-card](model-card.md), [reports/68](../../docs/ai-reports/areas/c-career-strategy/reports/68_model_card_r3_safety_update.md)).
- [ ] 미완료 — R3 이후 상태를 반영한 정식 모델 벤치마크 재실행은 아직 별도 작업으로 남아 있다.

## 3. RAG 상태

- [x] 완료 — AI 장문 보고서와 raw artifact 저장 경계를 A~F 공통 submodule 로 분리했다(`docs/ai-reports/`, `docs/ai-artifacts/`, [docs/AI_REPOSITORY_BOUNDARIES](../../docs/AI_REPOSITORY_BOUNDARIES.md)).
- [x] 완료 — RAG 설계, offline retrieval PoC, local embedding/vector PoC 를 문서화했다(PR #147 계열, [reports/50](../../docs/ai-reports/areas/c-career-strategy/reports/50_rag_design_plan.md), [reports/51](../../docs/ai-reports/areas/c-career-strategy/reports/51_rag_offline_poc_result.md), [reports/52](../../docs/ai-reports/areas/c-career-strategy/reports/52_rag_local_embedding_poc_result.md)).
- [x] 완료 — R2b~R2f 실측에서 retrievedContext 주입은 단순 개선으로 확정되지 않았고, review/reject gate 쪽이 더 안정적인 안전 레버로 확인되었다([reports/54](../../docs/ai-reports/areas/c-career-strategy/reports/54_rag_r2b_hardcase_eval.md), [reports/57](../../docs/ai-reports/areas/c-career-strategy/reports/57_rag_r2d_evidence_gate_eval.md), [reports/59](../../docs/ai-reports/areas/c-career-strategy/reports/59_rag_r2f_output_capture_gate_eval.md)).
- [~] 보류 또는 조건부 유지 — production prompt 에 retrievedContext 를 자동 주입하지 않는다([reports/60](../../docs/ai-reports/areas/c-career-strategy/reports/60_rag_r3_pre_backend_gate_design.md), [reports/61](../../docs/ai-reports/areas/c-career-strategy/reports/61_rag_r3_review_first_gate_implementation.md)).
- [~] 보류 또는 조건부 유지 — RAG 는 점수/지원판단이 아니라 설명 근거 보강 전용 후보로만 유지한다([reports/50](../../docs/ai-reports/areas/c-career-strategy/reports/50_rag_design_plan.md)).
- [x] 완료 — RAG 재도입 전 조건과 hard-case benchmark 기준을 문서화했다([reports/67](../../docs/ai-reports/areas/c-career-strategy/reports/67_rag_reentry_criteria_and_hardcase_benchmark.md)).
- [x] 완료 — RAG 재도입 hard-case fixture v1 과 dry-run payload 생성 골격을 구성했다([reports/69](../../docs/ai-reports/areas/c-career-strategy/reports/69_rag_hardcase_benchmark_fixture.md)).
- [x] 완료 — RAG hard-case offline A/B runner 골격과 aggregate summarizer 를 추가했다([reports/70](../../docs/ai-reports/areas/c-career-strategy/reports/70_rag_hardcase_offline_ab_runner.md)).
- [x] 완료 — 4090/Ollama 기준 RAG hard-case 3B LoRA A/B run 을 1회 실행하고 CareerTunerAI artifact path/commit SHA 를 기록했다([reports/71](../../docs/ai-reports/areas/c-career-strategy/reports/71_rag_hardcase_actual_3b_ab_run.md), CareerTunerAI `benchmarks/rag-hardcase/runs/rag_hardcase_v1_20260630_1635`, commit `8939d5856bf7edc9b9c93a7f9ff94034ab8d0a4e`).
- [x] 완료 — RAG hard-case 실제 출력의 offline R3-like evaluator / semantic observer A/B 분석을 수행했다([reports/72 summary](reports/72_rag_hardcase_r3_semantic_ab_analysis.md), [AIDocs report 73](../../docs/ai-reports/areas/c-career-strategy/reports/73_rag_hardcase_v1_r3_semantic_analysis.md), CareerTunerAI `benchmarks/rag-hardcase/evaluations/rag_hardcase_v1_20260630_1635_r3_semantic_eval`, commit `78167ea981f7d85035116cd1c65e15460223e1c4`).
- [x] 완료 — RAG hard-case 실제 출력의 local/private independent semantic judge 검증을 수행했다([reports/74 summary](reports/74_rag_hardcase_independent_semantic_judge.md), [AIDocs report 74](../../docs/ai-reports/areas/c-career-strategy/reports/74_rag_hardcase_v1_independent_semantic_judge.md), CareerTunerAI `benchmarks/rag-hardcase/judge-results/rag_hardcase_v1_20260630_1635_semantic_judge`, commit `4bf1fc70b3acd8946d7eee8f06787477446466af`).
- [x] 완료 — ChatGPT/Claude/Gemini 인터페이스의 현재 최상위 reasoning/analysis 모델용 평가팩과 label taxonomy v2 를 생성했다([reports/75 summary](reports/75_rag_hardcase_top_llm_judge_pack.md), [AIDocs report 75](../../docs/ai-reports/areas/c-career-strategy/reports/75_rag_hardcase_top_llm_judge_pack_plan.md), CareerTunerAI `benchmarks/rag-hardcase/top-llm-judge-packs/rag_hardcase_v1_20260630_1635_top_llm_judge_pack`, commit `5be737de2f2c9fbfd5dc36d3d0f42654d23e254e`).
- [x] 완료 — ChatGPT/Claude/Gemini interface judge 결과를 수집하고 schema validation / aggregate summary / disagreement matrix 를 생성했다([reports/76 summary](reports/76_rag_hardcase_top_llm_judge_consensus.md), [AIDocs report 76](../../docs/ai-reports/areas/c-career-strategy/reports/76_rag_hardcase_top_llm_judge_consensus.md), CareerTunerAI `benchmarks/rag-hardcase/top-llm-judge-aggregates/rag_hardcase_v1_20260630_1635_top_llm_judge_aggregate`, commit `949e8ad29d08c21f768d09d748e9ccd3437f949b`).
- [~] 보류 또는 조건부 유지 — raw output/result JSON 은 CareerTunerAI artifact repo 에 저장한다.
- [~] 보류 또는 조건부 유지 — 긴 실험 분석 문서는 CareerTunerAIDocs 에 저장한다.
- [~] 보류 또는 조건부 유지 — top LLM consensus 에서 true unsupported possession claim 은 A=0, B=0 이지만, empty output / NOT_JUDGEABLE / B_WORSE / NOT_COMPARABLE / regression candidate 가 남아 RAG runtime 은 `KEEP_RAG_DISABLED` 를 유지한다.
- [x] 완료 — RAG 용어 정정: 완료 실험은 evidence-bucket prompt augmentation 이며 true external retrieval RAG 는 미구현·미평가임을 상태 문서와 보고서 errata 로 고정했다([AIDocs report 77](../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md)).
- [~] 보류 또는 조건부 유지 — B(evidence-bucket augmentation)는 production 후보에서 **연구 후보로 격하**한다. 다음 실측의 중심은 A(production 경로) 대량 베이스라인이다([AIDocs report 77](../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md) §8).
- [x] 완료 — A-only baseline v1 실측(60케이스×2 run)과 rubric v2 judge 판정을 수행했다. "A true ≈ 0" 기각(진짜 보유단정 3/120관측), 전부 검출기 포착 — 계층 안전장치 필요성 정량 입증([AIDocs report 79](../../docs/ai-reports/areas/c-career-strategy/reports/79_a_only_baseline_v1_run.md)·[80](../../docs/ai-reports/areas/c-career-strategy/reports/80_a_only_baseline_repeat2_judge.md), PR #212).
- [ ] 미완료 — judge 판정 11건(특히 UNCLEAR 1건)의 **human gold label 확정**은 사용자 검토 대기(패킷: CareerTunerAI `judge-results/a_only_baseline_v1_claude_judge_verdicts.json`).

## 4. Evidence gate / safety 상태

- [x] 완료 — R3 review-first gate 는 `PASSED`, `REVIEW_REQUIRED`, `REJECTED` 상태를 결정론으로 산출한다(PR #174, [reports/61](../../docs/ai-reports/areas/c-career-strategy/reports/61_rag_r3_review_first_gate_implementation.md)).
- [x] 완료 — userEvidence 는 #175 이후 `profileSkills + profileCertificates` 로 고정하고, AI 파생 `matchedSkills` 는 보유 근거로 신뢰하지 않는다(PR #175, [reports/62](../../docs/ai-reports/areas/c-career-strategy/reports/62_rag_r3_evidence_gate_user_evidence_hotfix.md)).
- [x] 완료 — `SkillAliasNormalizer` 는 curated alias map 만 사용하며 substring/fuzzy matching 을 추가하지 않는다(PR #180, [reports/63](../../docs/ai-reports/areas/c-career-strategy/reports/63_rag_r3_evidence_gate_skill_alias_normalizer.md)).
- [x] 완료 — mention-boundary 정책은 `Next.js`/`React Native`/`Spring Boot`/구체 DB명 false-positive 를 차단한다(PR #182, [reports/64](../../docs/ai-reports/areas/c-career-strategy/reports/64_rag_r3_skill_alias_mention_boundary.md)).
- [x] 완료 — 관리자 응답 변환은 legacy `gateStatus=null` 과 `gateReasonsJson` null/빈 배열/정상/깨진 JSON 을 안전하게 처리하도록 자동 검증을 추가했다(PR #186 후속, [reports/66](../../docs/ai-reports/areas/c-career-strategy/reports/66_r3_auto_verification_and_ai_checklist.md)).
- [!] 주의 필요 — alias map 은 보수적 allow-list 이므로 새 alias 추가 시 false-negative 위험 케이스를 함께 테스트해야 한다.

## 5. Data / training backlog

- [x] 완료 — Phase 1 MVP 데이터/평가 문서와 model-card 기준은 유지되어 있다([README](README.md), [model-card](model-card.md)).
- [~] 보류 또는 조건부 유지 — 비IT 직군 정밀 자격증/역량 카탈로그와 RAG grounding 은 Phase 2 확장 대상으로 둔다([README](README.md), [model-card](model-card.md)).
- [ ] 미완료 — CJK/자기모순/직군별 hard case 를 반영한 추가 학습 데이터 큐레이션은 별도 backlog 다.
- [ ] 미완료 — R3 gate reason 축적 데이터를 모델 개선 학습셋으로 환류하는 파이프라인은 아직 없다.
- [!] 주의 필요 — raw eval/output 파일은 main repo 에 커밋하지 않고 artifact 경로 또는 별도 저장소 원칙을 유지한다.

## 6. Admin / observability 상태

- [x] 완료 — 관리자 fit-analysis 목록/상세에서 gate status, reason count, severity, 상세 reason 을 확인할 수 있다(PR #175, [reports/62](../../docs/ai-reports/areas/c-career-strategy/reports/62_rag_r3_evidence_gate_user_evidence_hotfix.md)).
- [x] 완료 — 관리자 홈과 대시보드의 검토 대기 카운트는 지원 건별 최신 fit_analysis 기준으로 통일되어 있다(PR #175, [reports/62](../../docs/ai-reports/areas/c-career-strategy/reports/62_rag_r3_evidence_gate_user_evidence_hotfix.md)).
- [x] 완료 — `reviewRequiredOnly=true` 목록 필터는 서버 SQL 에서 `REVIEW_REQUIRED` 만 반환하도록 고정했다(PR #175, [reports/66](../../docs/ai-reports/areas/c-career-strategy/reports/66_r3_auto_verification_and_ai_checklist.md)).
- [~] 보류 또는 조건부 유지 — 운영자가 gate reason 을 처리/해결 완료로 표시하는 별도 workflow 는 아직 도입하지 않았다.
- [ ] 미완료 — gate status 분포, false-positive rate, alias 추가 요청량을 보는 장기 운영 리포트는 추후 후보로 남아 있다.

## 7. Current next candidates

- [x] 완료 — R3 자동 검증 보강과 최상위 체크리스트 정리(PR #186 후속, [reports/66](../../docs/ai-reports/areas/c-career-strategy/reports/66_r3_auto_verification_and_ai_checklist.md)).
- [x] 완료 — model-card R3 safety 반영과 RAG 재평가 기준 정리([reports/67](../../docs/ai-reports/areas/c-career-strategy/reports/67_rag_reentry_criteria_and_hardcase_benchmark.md), [reports/68](../../docs/ai-reports/areas/c-career-strategy/reports/68_model_card_r3_safety_update.md)).
- [x] 완료 — RAG 재도입 hard-case fixture v1 구성([reports/69](../../docs/ai-reports/areas/c-career-strategy/reports/69_rag_hardcase_benchmark_fixture.md)).
- [x] 완료 — RAG hard-case offline A/B runner 골격 추가([reports/70](../../docs/ai-reports/areas/c-career-strategy/reports/70_rag_hardcase_offline_ab_runner.md)).
- [x] 완료 — 4090/Ollama 기준 실제 3B LoRA A/B 실행 및 CareerTunerAI artifact 저장([reports/71](../../docs/ai-reports/areas/c-career-strategy/reports/71_rag_hardcase_actual_3b_ab_run.md)).
- [x] 완료 — RAG hard-case 실제 출력의 offline R3-like/semantic A/B 분석 및 CareerTunerAI/CareerTunerAIDocs 산출물 저장([reports/72 summary](reports/72_rag_hardcase_r3_semantic_ab_analysis.md), [AIDocs report 73](../../docs/ai-reports/areas/c-career-strategy/reports/73_rag_hardcase_v1_r3_semantic_analysis.md)).
- [x] 완료 — RAG hard-case 실제 출력의 local/private independent semantic judge 검증 및 CareerTunerAI/CareerTunerAIDocs 산출물 저장([reports/74 summary](reports/74_rag_hardcase_independent_semantic_judge.md), [AIDocs report 74](../../docs/ai-reports/areas/c-career-strategy/reports/74_rag_hardcase_v1_independent_semantic_judge.md)).
- [x] 완료 — RAG hard-case top LLM judge 평가팩 생성 및 CareerTunerAI/CareerTunerAIDocs 산출물 저장([reports/75 summary](reports/75_rag_hardcase_top_llm_judge_pack.md), [AIDocs report 75](../../docs/ai-reports/areas/c-career-strategy/reports/75_rag_hardcase_top_llm_judge_pack_plan.md)).
- [x] 완료 — RAG hard-case top LLM judge 결과 3종 validation/aggregation 및 disagreement matrix 생성([reports/76 summary](reports/76_rag_hardcase_top_llm_judge_consensus.md), [AIDocs report 76](../../docs/ai-reports/areas/c-career-strategy/reports/76_rag_hardcase_top_llm_judge_consensus.md)).
- [x] 완료 — A-only production 경로 안전성 베이스라인 v1(60케이스×2 run + judge, PR #212, [AIDocs report 80](../../docs/ai-reports/areas/c-career-strategy/reports/80_a_only_baseline_repeat2_judge.md)). **120케이스 확장은 보류** — 운영 FP/FN 신호 시 confusion_pair 집중으로 재개.
- [x] 완료 — fallback provider 판단값 소유 통일(PR #211, [AIDocs report 78](../../docs/ai-reports/areas/c-career-strategy/reports/78_provider_judgment_ownership_unification.md)) — 전 provider 뉴로-심볼릭.
- [ ] 미완료 — **(1순위)** 관리자 gate review 처리 workflow: 검토 완료, 재분석 요청, memo/reason 연결 — 운영 전환의 핵심([AIDocs report 80](../../docs/ai-reports/areas/c-career-strategy/reports/80_a_only_baseline_repeat2_judge.md) §4).
- [ ] 미완료 — **(2순위)** R3 gate reason 로그 기반 false-positive 샘플 리뷰와 alias 후보 triage.
- [ ] 미완료 — human gold label 확정(사용자): rag-hardcase disagreement13 + A-only judge 판정 11건.
- [ ] 미완료 — model-card 다음 개정: R3 운영 데이터와 gate reason 분포 반영.

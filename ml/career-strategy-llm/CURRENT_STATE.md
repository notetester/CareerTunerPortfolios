# C Career Strategy LLM current state

Last updated: 2026-07-01

이 문서는 C 영역 자체 LLM 작업을 처음 읽을 때의 진입점이다. 장문 실험 보고서는
CareerTuner 본체에 계속 쌓지 않고 `CareerTunerAIDocs` 서브모듈에 둔다.

상세 출처:

- C 보고서 archive: [docs/ai-reports/areas/c-career-strategy/reports/](../../docs/ai-reports/areas/c-career-strategy/reports/)
- C raw artifact map: [72_c_raw_artifact_classification_map.md](../../docs/ai-reports/areas/c-career-strategy/reports/72_c_raw_artifact_classification_map.md)
- C archive index: [reports/README.md](../../docs/ai-reports/areas/c-career-strategy/reports/README.md)

## 읽는 순서

1. 이 문서에서 현재 판단을 확인한다.
2. 구현 상태는 [AI_ROADMAP_CHECKLIST.md](AI_ROADMAP_CHECKLIST.md)와 [model-card.md](model-card.md)를 본다.
3. 근거가 필요한 항목만 아래 출처 링크의 장문 보고서를 연다.
4. raw output, generated request/result JSON, benchmark aggregate 는 `docs/ai-artifacts/`에서 찾는다.

## 현재 결론

### Production runtime

- C 적합도 설명 생성은 `FallbackFitAnalysisAiService` primary dispatcher 기준으로 운영한다.
- OSS 3B LoRA 출력은 그대로 신뢰하지 않고 E1 grounding hard guard와 R3 review-first evidence gate를 통과한다.
- R3 gate는 `PASSED`, `REVIEW_REQUIRED`, `REJECTED` review state와 evidence source를 남긴다.
- R3 gate는 `fitScore`, `applyDecision`, `matchedSkills`, `missingSkills`를 변경하지 않는다.
- `userEvidence`는 `profileSkills + profileCertificates` 기준이다. AI/규칙 산출인 `matchedSkills`는 사용자 보유 근거로 보지 않는다.

출처:

- [61_rag_r3_review_first_gate_implementation.md](../../docs/ai-reports/areas/c-career-strategy/reports/61_rag_r3_review_first_gate_implementation.md)
- [62_rag_r3_evidence_gate_user_evidence_hotfix.md](../../docs/ai-reports/areas/c-career-strategy/reports/62_rag_r3_evidence_gate_user_evidence_hotfix.md)
- [63_rag_r3_evidence_gate_skill_alias_normalizer.md](../../docs/ai-reports/areas/c-career-strategy/reports/63_rag_r3_evidence_gate_skill_alias_normalizer.md)
- [64_rag_r3_skill_alias_mention_boundary.md](../../docs/ai-reports/areas/c-career-strategy/reports/64_rag_r3_skill_alias_mention_boundary.md)
- [65_r3_evidence_gate_dev_integration_check.md](../../docs/ai-reports/areas/c-career-strategy/reports/65_r3_evidence_gate_dev_integration_check.md)
- [66_r3_auto_verification_and_ai_checklist.md](../../docs/ai-reports/areas/c-career-strategy/reports/66_r3_auto_verification_and_ai_checklist.md)

### Model decision

- 현재 기준 모델은 `careertuner-c-career-strategy-3b` 계열이다.
- 7B base smoke 결과만으로는 3B LoRA 교체 근거가 부족하다.
- 7B 전환, 7B LoRA 재학습, GGUF 재생성은 RAG/evidence gate 이후 별도 재평가 전까지 보류한다.

출처:

- [49_7b_smoke_benchmark_result.md](../../docs/ai-reports/areas/c-career-strategy/reports/49_7b_smoke_benchmark_result.md)
- [68_model_card_r3_safety_update.md](../../docs/ai-reports/areas/c-career-strategy/reports/68_model_card_r3_safety_update.md)

### RAG decision

- RAG runtime 자동 주입은 production prompt에 연결하지 않는다.
- retrievedContext 주입은 R2b-R2f 실측에서 단순 개선으로 확정되지 않았고, 일부 케이스에서는 grounding conflation을 늘렸다.
- rewrite 자동 사용자 노출도 보류한다. detector-safe와 score-preserving 신호는 있었지만 의미손실과 malformed 문제가 남았다.
- RAG는 점수/지원판단이 아니라 설명 근거 보강 후보로만 유지한다.
- 재도입하려면 hard-case benchmark에서 unsupported possession claim 감소를 먼저 검증한다.
- 2026-07-01 offline R3-like/semantic observer 분석에서 B variant 는 combined unsafe claim 을 9에서 5로 줄였지만, B 에도 unsafe claim 5건과 REVIEW_REQUIRED 7건이 남아 production 자동 주입 근거로는 부족하다.
- 2026-07-01 local/private qwen2.5 7B independent semantic judge 검증에서는 B variant 가 unsupported claim count 를 안정적으로 줄였다고 확인되지 않았다(A=2, B=3, agreement=11/disagreement=13). 따라서 RAG runtime 재도입 판단은 `KEEP_RAG_DISABLED` 로 유지한다.
- 2026-07-01 ChatGPT/Claude/Gemini interface judge 결과 3개를 수집하고 schema validation / aggregate summary / disagreement matrix 를 생성했다. top LLM consensus 기준 true unsupported possession claim 은 A=0, B=0 이지만, empty output / NOT_JUDGEABLE / B_WORSE / NOT_COMPARABLE / regression candidate 가 남아 RAG runtime 판단은 `KEEP_RAG_DISABLED` 로 유지한다.

출처:

- [50_rag_design_plan.md](../../docs/ai-reports/areas/c-career-strategy/reports/50_rag_design_plan.md)
- [54_rag_r2b_hardcase_eval.md](../../docs/ai-reports/areas/c-career-strategy/reports/54_rag_r2b_hardcase_eval.md)
- [57_rag_r2d_evidence_gate_eval.md](../../docs/ai-reports/areas/c-career-strategy/reports/57_rag_r2d_evidence_gate_eval.md)
- [59_rag_r2f_output_capture_gate_eval.md](../../docs/ai-reports/areas/c-career-strategy/reports/59_rag_r2f_output_capture_gate_eval.md)
- [60_rag_r3_pre_backend_gate_design.md](../../docs/ai-reports/areas/c-career-strategy/reports/60_rag_r3_pre_backend_gate_design.md)
- [67_rag_reentry_criteria_and_hardcase_benchmark.md](../../docs/ai-reports/areas/c-career-strategy/reports/67_rag_reentry_criteria_and_hardcase_benchmark.md)
- [74_rag_hardcase_v1_independent_semantic_judge.md](../../docs/ai-reports/areas/c-career-strategy/reports/74_rag_hardcase_v1_independent_semantic_judge.md)
- [75_rag_hardcase_top_llm_judge_pack_plan.md](../../docs/ai-reports/areas/c-career-strategy/reports/75_rag_hardcase_top_llm_judge_pack_plan.md)
- [76_rag_hardcase_top_llm_judge_consensus.md](../../docs/ai-reports/areas/c-career-strategy/reports/76_rag_hardcase_top_llm_judge_consensus.md)

### Benchmark and artifact state

- RAG hard-case fixture v1, offline A/B runner, aggregate summarizer는 구성되어 있다.
- 4090/Ollama 기준 실제 3B LoRA A/B run은 1회 실행했다.
- 해당 raw artifact는 CareerTunerAI `benchmarks/rag-hardcase/runs/rag_hardcase_v1_20260630_1635`에 있고, 기록된 artifact commit은 `8939d5856bf7edc9b9c93a7f9ff94034ab8d0a4e`다.
- 해당 출력의 offline R3-like/semantic evaluation artifact 는 CareerTunerAI `benchmarks/rag-hardcase/evaluations/rag_hardcase_v1_20260630_1635_r3_semantic_eval`에 있고, commit은 `78167ea981f7d85035116cd1c65e15460223e1c4`다.
- 장문 분석은 CareerTunerAIDocs `areas/c-career-strategy/reports/73_rag_hardcase_v1_r3_semantic_analysis.md`에 있고, commit은 `e93a7f8443fb5638bcf253c381a3e9953149006b`다.
- independent semantic judge packet/result artifact 는 CareerTunerAI `benchmarks/rag-hardcase/judge-packets/rag_hardcase_v1_20260630_1635_judge_packets.jsonl` 및 `benchmarks/rag-hardcase/judge-results/rag_hardcase_v1_20260630_1635_semantic_judge`에 있고, commit은 `4bf1fc70b3acd8946d7eee8f06787477446466af`다.
- independent semantic judge 장문 분석은 CareerTunerAIDocs `areas/c-career-strategy/reports/74_rag_hardcase_v1_independent_semantic_judge.md`에 있고, commit은 `60c3f908a730f59c0c3e1860dbdc17c58f072bf6`다.
- top LLM judge pack artifact 는 CareerTunerAI `benchmarks/rag-hardcase/top-llm-judge-packs/rag_hardcase_v1_20260630_1635_top_llm_judge_pack` 및 `rag_hardcase_v1_20260630_1635_top_llm_judge_pack_disagreement13`에 있고, commit은 `5be737de2f2c9fbfd5dc36d3d0f42654d23e254e`다.
- top LLM judge pack 장문 계획 문서는 CareerTunerAIDocs `areas/c-career-strategy/reports/75_rag_hardcase_top_llm_judge_pack_plan.md`에 있고, commit은 `0ef6bc1aa6c3193958fd50812b1965f859cee9b7`다.
- top LLM judge response/aggregate artifact 는 CareerTunerAI `benchmarks/rag-hardcase/top-llm-judge-responses/rag_hardcase_v1_20260630_1635_top_llm_judge_responses` 및 `benchmarks/rag-hardcase/top-llm-judge-aggregates/rag_hardcase_v1_20260630_1635_top_llm_judge_aggregate`에 있고, commit은 `949e8ad29d08c21f768d09d748e9ccd3437f949b`다.
- top LLM judge consensus 장문 분석은 CareerTunerAIDocs `areas/c-career-strategy/reports/76_rag_hardcase_top_llm_judge_consensus.md`에 있고, commit은 `1f0829f23985da6d0a60de41aa350e6c7542a0c3`다.

출처:

- [69_rag_hardcase_benchmark_fixture.md](../../docs/ai-reports/areas/c-career-strategy/reports/69_rag_hardcase_benchmark_fixture.md)
- [70_rag_hardcase_offline_ab_runner.md](../../docs/ai-reports/areas/c-career-strategy/reports/70_rag_hardcase_offline_ab_runner.md)
- [71_rag_hardcase_actual_3b_ab_run.md](../../docs/ai-reports/areas/c-career-strategy/reports/71_rag_hardcase_actual_3b_ab_run.md)
- [73_rag_hardcase_v1_r3_semantic_analysis.md](../../docs/ai-reports/areas/c-career-strategy/reports/73_rag_hardcase_v1_r3_semantic_analysis.md)
- [74_rag_hardcase_v1_independent_semantic_judge.md](../../docs/ai-reports/areas/c-career-strategy/reports/74_rag_hardcase_v1_independent_semantic_judge.md)
- [75_rag_hardcase_top_llm_judge_pack_plan.md](../../docs/ai-reports/areas/c-career-strategy/reports/75_rag_hardcase_top_llm_judge_pack_plan.md)
- [76_rag_hardcase_top_llm_judge_consensus.md](../../docs/ai-reports/areas/c-career-strategy/reports/76_rag_hardcase_top_llm_judge_consensus.md)

### Phase 1 training baseline

- Phase 1 데이터는 mixed 기준이다. IT 297건과 비IT 119건을 합쳐 train 375, val 41로 구성했다.
- 3B QLoRA 학습, merge, GGUF 변환, Ollama 등록, 백엔드 OSS 연동, 라이브 E2E까지 한 차례 완주했다.

출처:

- [02_dataset_quality_report.md](../../docs/ai-reports/areas/c-career-strategy/reports/02_dataset_quality_report.md)
- [03_dataset_quality_report.mixed.md](../../docs/ai-reports/areas/c-career-strategy/reports/03_dataset_quality_report.mixed.md)
- [12_live_e2e_result.md](../../docs/ai-reports/areas/c-career-strategy/reports/12_live_e2e_result.md)

## 본체에 남기는 것

- 제품/평가 재현에 필요한 C validator, runner, deterministic helper.
- 작은 synthetic fixture와 golden set.
- `CURRENT_STATE.md`, `AI_ROADMAP_CHECKLIST.md`, `model-card.md` 같은 최신 상태 요약.
- artifact path, report path, commit SHA.

## 본체에 새로 쌓지 않는 것

- 장문 실험 보고서와 누적 해석 문서.
- raw model output, generated request/result JSON, aggregate summary.
- 4090/Tailscale/OpenSSH/GitHub Actions/MCP 운영 문서와 운영 스크립트.

새 장문 보고서는 `docs/ai-reports/areas/c-career-strategy/reports/`에 추가하고, raw artifact는
`docs/ai-artifacts/`에 저장한다. 본체에는 필요한 경우 이 문서나 checklist에 짧은 결론과 출처 링크만 남긴다.

4090/Ollama/Tailscale 운영 정책, 모델 registry, 시연 PC 점검 절차는
`docs/ai-artifacts/docs/ops/`와 `docs/ai-artifacts/scripts/ops/4090/`가 원본이다.

# C report compatibility index

이 폴더는 과거 본체 경로를 참조하는 링크를 위한 **소형 호환 요약**만 유지한다. C Career Strategy LLM의
현재 상태는 [CURRENT_STATE](../CURRENT_STATE.md), 구현 체크는
[AI_ROADMAP_CHECKLIST](../AI_ROADMAP_CHECKLIST.md), 모델 배포 판단은 [model-card](../model-card.md)를
정본으로 본다.

장문 분석을 이 폴더에 새로 추가하지 않는다. 전체 보고서와 번호별 이력은
[CareerTunerAIDocs C 보고서 인덱스](../../../docs/ai-reports/areas/c-career-strategy/reports/README.md)에서 찾는다.

## 본체에 남긴 호환 요약

- [72 R3 semantic A/B 요약](72_rag_hardcase_r3_semantic_ab_analysis.md)
- [74 independent semantic judge 요약](74_rag_hardcase_independent_semantic_judge.md)
- [75 top-LLM judge pack 요약](75_rag_hardcase_top_llm_judge_pack.md)
- [76 top-LLM consensus 요약](76_rag_hardcase_top_llm_judge_consensus.md)
- [79 A-only baseline v1 실행 요약](79_a_only_baseline_run.md)
- [80 A-only repeat2·judge 판단 요약](80_a_only_baseline_repeat2_judge.md)

79·80은 외부 링크 호환과 당시 결론의 빠른 확인을 위한 본체 요약이다. 상세 실행 근거와 이후 확정 판단은
각 파일이 연결하는 AIDocs 보고서가 정본이며, 이 두 요약을 새 실험 결과로 갱신하지 않는다.

## 현재 판단을 만든 최신 보고서

- [77 AI 방향 및 RAG 용어 정정](../../../docs/ai-reports/areas/c-career-strategy/reports/77_ai_direction_and_rag_terminology_review.md)
- [78 provider 판단값 소유 통일](../../../docs/ai-reports/areas/c-career-strategy/reports/78_provider_judgment_ownership_unification.md)
- [79 A-only baseline v1](../../../docs/ai-reports/areas/c-career-strategy/reports/79_a_only_baseline_v1_run.md)
- [80 A-only repeat2 및 판정단 검증](../../../docs/ai-reports/areas/c-career-strategy/reports/80_a_only_baseline_repeat2_judge.md)
- [83 production 경로 E2E baseline](../../../docs/ai-reports/areas/c-career-strategy/reports/83_e2e_production_path_baseline.md)
- [84 한국어 alias FP triage와 GPU 제안](../../../docs/ai-reports/areas/c-career-strategy/reports/84_fp_triage_korean_aliases_and_gpu_proposal.md)
- [85 post-R3 재벤치마크와 GPU 이중 트랙](../../../docs/ai-reports/areas/c-career-strategy/reports/85_post_r3_rebenchmark_and_gpu_dual_track.md)
- [86 AI fallback 체인 감사와 C 상태](../../../docs/ai-reports/areas/c-career-strategy/reports/86_ai_fallback_chain_audit_and_c_ai_status.md)
- [87 NCS evidence-grounded RAG PoC](../../../docs/ai-reports/areas/c-career-strategy/reports/87_ncs_evidence_grounded_rag_poc.md)

## 저장 경계

- 장문 실험 해석: [docs/ai-reports](../../../docs/ai-reports/)
- raw model output, request/result JSON, benchmark manifest: [docs/ai-artifacts](../../../docs/ai-artifacts/)

# 골든셋 확장 계획 + 1차(36) 커버리지 (2026-06-23)

> 단순 개수 증가가 아니라 **지금까지 실제로 발견한 오류 유형 커버리지** 중심 확장. 1차 12→36(이 PR), 최종 60(2차).
> 평가용 전용 — 학습 데이터에 섞지 않는다. raw 결과는 메인 repo 미커밋(CareerTunerAI).

## 1. 목적
1. 3B LoRA 설명 품질·안정성을 더 다양한 케이스에서 확인.
2. E1 grounding 관측기가 부족역량 보유오인을 잡고 false-positive 없는지.
3. E2 named-entity 관측기가 CRMONE류를 잡고 노이즈를 줄이는지.
4. PARSE_FAIL 이 특정 도메인/케이스에서 반복되는지.
5. base 대비 LoRA 의 grounded specificity 우위 유지 확인.

## 2. 생성 방식(품질 보증)
- 8그룹 × 3 = 24 신규를 **생성 → 적대적 검증** 2단계 멀티에이전트로 작성.
- 사실 필드(matched/missingRequired/missingPreferred)는 워크플로가 아니라 **`golden_case_tools.compute_matched_missing` 로 결정론 계산**(규칙엔진과 동일: profileSkills 정확일치, cert 제외).
- fitScore/applyDecision 은 경계 테스트 위해 밴드 내 작성(기존 골든셋도 손작성), 검증기가 밴드 정합 강제.
- `golden_case_tools.validate_case` 로 36건 전수 검증 → **0 오류**. 검증기가 모순 2건(`mustNotMention` 에 allowedSkills 스킬을 넣은 Prometheus/Splunk)을 적발·교정.

## 3. 1차(36) 커버리지
**결정 분포:** APPLY 5 · COMPLEMENT 12 · HOLD 19. **IT 25 · 비IT 11.**
신규 24의 오류유형(중복 카운트):
| errorType | 건수 | 의미 |
| --- | --- | --- |
| e1_grounding_bait | 17 | 필수역량을 profileSkills 에서 빼고 인접역량만 → 보유 오인 유도 |
| hold_no_immediate_apply | 13 | HOLD인데 즉시지원 금지(forbiddenClaims 전체 명령형) |
| complement_boundary | 10 | 53~68 경계 점수 |
| nonit_no_it_leak | 9 | 비IT 케이스에 IT 용어 누출 금지(mustNotMention) |
| soft_skill_lacking | 8 | 협업/커뮤니케이션/문서화 부족 |
| apply_with_risk | 7 | 필수 충족(APPLY)이나 우대 공백 리스크 강조 필요 |
| e2_named_entity_bait | 6 | 입력 밖 제품명(CRM/ERP/분석툴) 날조 유발 맥락 |
| cert_tech_gap | 4 | 자격증 보유하나 기술역량 부족(#116 cert 케이스, profileCertificates 7건) |
| parse_fail_prone | 2 | 요구역량 다수로 출력 큼(PARSE_FAIL 재현) |
| 기타 | 4 | wrong_framework_leak·overconfidence·legacy_stack 등 |

## 4. 데이터 분리 원칙(엄수)
- 골든셋은 **평가용**. 학습 데이터에 섞지 않는다. train/val 과 중복·유사 케이스 만들지 않는다(회사명·스킬조합을 기존과 상이하게 작성, 적대적 검증으로 중복 차단).
- raw 평가 결과/로그는 메인 repo 미커밋 → CareerTunerAI `results/<jobId>/`.

## 5. 단계형 계획
| 단계 | 개수 | 내용 | 상태 |
| --- | --- | --- | --- |
| 1차 | 12→**36** | 위 오류유형 커버 24 추가 | **이 PR** |
| 2차 | 36→**60** | 부족 차원 보강 — parse_fail_prone(+4), 비IT 다양성(물류/HR/CS/교육 +6), APPLY-with-risk(+4), e2_named_entity_bait 다양화(+4), 한글 고유명사 날조(라틴 관측 한계 보완 케이스 +6) | 다음 |
| 사람검토 | — | 레인 C(SME)가 비IT 도메인 현실성·라벨 타당성 검토 | 다음 |

## 6. 평가 지표(하니스, 측정 전용)
`eval_fit_model.py` 가 케이스당 산출:
- 계약: success/json_parse/required_key/forbidden_key/cjk_leak/hallucination, `failure_reasons`(PARSE_FAIL 포함).
- **E1**: `grounding_violation_{count,rate,by_case}`(부족역량 보유 서술 — 백엔드 guard 미러, #116 cert 제외).
- **E2**: `unsupported_named_entity_{count,rate,by_case}`(가짜 제품 식별자).
- latency: cold/warm avg·p95.
검증·관측기 단위테스트: `golden_case_tools.py`(검증), `test_entity_observer.py`(E1/E2). 4090 실행은 reports/35(`eval_golden_set` job).

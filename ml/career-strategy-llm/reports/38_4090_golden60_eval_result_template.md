# 4090 골든셋(60) 평가 결과 기록·해석 템플릿

> 확장 골든셋(60) `eval_golden_set` 실행 결과를 기록·해석하는 틀. 실행 명령은 reports/35, 케이스 분포는 reports/37.
> raw 는 CareerTunerAI `results/<jobId>/`. 메인 repo 엔 이 틀에 채운 요약만.

## 실행
큐: 4090 에서 **"작업 진행"** → `run_latest_job.ps1` 이 `eval_golden_set`(LoRA+base 각각) 실행 → push. (reports/35 §A)

## 결과 기록(채울 표)
| 지표 | LoRA(careertuner-c-career-strategy-3b) | base(qwen2.5:3b-instruct) |
| --- | --- | --- |
| 계약 success | _/180 = _ | _/180 = _ |
| json_parse_rate | _ | _ |
| hallucination_rate(범위밖 스킬) | _ | _ |
| **E1 grounding_violation_rate** | _ | _ |
| **E2 unsupported_named_entity** count/rate | _ / _ | _ / _ |
| cjk_leak_rate | _ | _ |
| PARSE_FAIL count | _ | _ |
| warm_avg / warm_p95 latency | _ / _ | _ / _ |
> 60케이스 × repeat 3 = 180 run/모델.

## 해석 기준(골든36 결과 reports/36 대비)
1. **파인튜닝 가치(핵심)**: LoRA hallucination_rate < base 유지되는가(골든36: 0.009 vs 0.046). 확장으로 케이스가 늘어도 LoRA 의 '범위밖 스킬 날조 억제' 우위가 지속되는지.
2. **계약 success**: LoRA ≥ base 유지(골든36: 0.944 vs 0.889).
3. **E1 grounding(트레이드오프)**: LoRA grounding_violation_rate 가 base 보다 높은 경향(골든36: 0.194 vs 0.139) 재확인 — 백엔드 guard 가 흡수하는 유형. by_case 로 어떤 bait 케이스가 발동하는지.
4. **E2**: LoRA 의 CRMOne류 high 빈도(골든36: 1/108≈0.9%). 한글 가짜명은 라틴 관측기 미포착(사람검토 대상, 알려진 한계).
5. **PARSE_FAIL**: 특정 도메인(비IT 긴 출력)에서 반복되는가 — by case 확인. 프로덕션은 폴백 처리(별도 견고성 backlog).
6. **CJK 누출**: 양쪽 비율 + 어느 케이스 — 재학습 범위(지금은 관측).
7. **오탐 점검**: HALLUCINATED_SKILL 은 공백 정규화 적용됨(reports/36 fix). 남은 flag 가 진짜 범위밖인지(회색지대 AFPK/CRM 사용법류 구분) 케이스별 확인 후 결론.

## 결론 작성 가이드
- oversell 금지 — 측정값 그대로. 오탐 의심은 raw 대조로 확인 후 기술.
- LoRA 유지/개선 판단 + guard 정당성 + 다음 데이터 정제 backlog(E2 한글·CJK·gray-zone) 연결.

# Phase 2 — 7B smoke benchmark 결과 (2026-06-26)

> reports/48 계획대로 golden60 동일 하니스로 **3B LoRA vs 3B base vs 7B base** 비교 실행 완료.
> **결론: 7B base는 3B LoRA를 못 이긴다 → 7B 전환 보류, 3B LoRA 유지 + RAG 우선.**
> 4090 GPU 실측. raw 결과는 CareerTunerAI artifact repo에만(`results/2026-06-26-7b-smoke-002/`).

## 0. #145 반영 상태 감사 (이 문서가 follow-up인 이유)
GitHub 실측 감사 결과, **PR #145는 merged diff 기준 `reports/48_7b_smoke_benchmark_plan.md` 1개만 반영**됐다(merged=True, head=`aabb82c`).
- dev에 **누락**: `reports/49`(이 문서), `scripts/test_eval_robustness.py`, `eval_fit_model.py`의 learningTaskReasons 문자열-항목 가드. → dev의 `eval_fit_model.py:343`은 여전히 `sk = (item or {}).get("skill")`라 7B/base가 문자열 학습추천을 내면 다시 크래시할 수 있는 상태였다.
- **불일치 원인**: #145는 head가 `aabb82c`(reports/48 커밋)인 상태로 merge됐고, 하니스 견고성 수정(`d018e29`)·reports/49(`3f0c3b1`)는 그 **이후** LEE-JEONG-GUCK에 push돼 merge에 포함되지 못했다. PR 본문은 사후 편집(PATCH)으로 "48+수정+49"라 적혀 있었으나 **이미 merge된 diff는 reports/48만**이라 본문과 실제가 어긋났다(보고 오류).
- **조치**: 누락된 **하니스 견고성 수정 + 회귀 테스트 + reports/49**를 이 follow-up PR로 닫는다. #145는 reports/48 계획만 반영된 PR로 정정 기록한다.

## 1. 실행 개요 (#142 merge 확인 후 진행)
- **#142(judgeId)·#141(병렬지표) 모두 dev 머지 확인.** dev 최신에서 LEE-JEONG-GUCK 작업.
- **7B preflight**: 4090에 `qwen2.5:7b-instruct` 미설치 → base pull(`ollama pull`, **fine-tuning 아님**, 4.7GB). 3모델 `/v1/models` 확인.
- **단계형**: stage1 small smoke(limit 8, repeat 2) 통과 → stage2 golden60 repeat 2. (stage3 repeat3은 7B 무유망으로 **미실시**.)
- **운영 사고 2건과 복구**(투명 기록): ① 7B가 `learningTaskReasons`를 문자열 리스트로 출력해 하니스 크래시 → `eval_fit_model` 견고성 수정(문자열 항목=스킬명 처리, test 3건). ② 4090 재부팅 후 Ollama가 GPU를 잃고 CPU 서빙 → Ollama 재시작으로 GPU 복구(`100% GPU` 확인) 후 재실행. 데이터 손상 없음.
- 실행: `run_latest_job.ps1`(+`-Branch`로 머지 전 수정 검증, `params.limit`, VRAM 측정), job `2026-06-26-7b-smoke-001/002`.

## 2. 사용한 7B 모델명
**`qwen2.5:7b-instruct`** (Ollama, ID 845dbda0ea48, 4.7GB Q4_K_M, base instruct — 미파인튜닝).

## 3. golden60 결과 (repeat 2 = 120 run/모델, GPU)
| 지표 | 3B LoRA | 3B base | 7B base |
| --- | --- | --- | --- |
| **contract success** | **0.892** (107/120) | 0.875 (105/120) | **0.867 (104/120, 최저)** |
| json_parse_rate | 0.992 | 1.0 | 1.0 |
| CJK leak | 0.05 (6) | 0.017 (2) | 0.017 (2) |
| PARSE_FAIL | 1 | 0 | 0 |
| timeout | 0 | 0 | 0 |
| E1 grounding 위반 | 0.233 (28) | 0.1 (12) | 0.067 (8) |
| E2 high / review | 0 / 0 | 0 / 0 | 0 / 0 |
| warm_avg / p95 latency | 2263 / 2738 ms | 1782 / 2599 ms | **3403 / 4638 ms** |
| VRAM (모델, ollama ps) | ~2.2 GB | ~2.2 GB | **~4.7 GB** |

## 4. HALLUCINATED_SKILL — raw / normalized / semantic (핵심)
하니스가 raw/normalized/resolved_fp까지 자동 산출. **semantic(valid_error)은 judge consensus로 확정**(잔여>0이라 하니스만으로 0 단정 금지 — 3-lens 판정 실시).
| 채널 | 3B LoRA | 3B base | 7B base |
| --- | --- | --- | --- |
| raw_hallucination | 1 | 12 | **16** |
| normalized 잔여(judge 대상) | 1 | 7 | **9** |
| **semantic valid_error** | **0** | **1** | **0** |
| acceptable_gray | 0 | 5 | 4 |
| harness_false_positive | 1 | 1 | 5 |

- **유일한 valid_error = `MSSQL`(3B base) — unique 1건.** allowedSkills엔 일반 `SQL`만 있는데 특정 제품(Microsoft SQL Server)을 학습스킬로 제시(judge 합의 valid_error, needsHumanReview). **표(§4)·합의(§5) 모두 semantic valid_error=1(unique candidate 기준)** — 단일출처는 산출물 `results/2026-06-26-7b-smoke-002-judge/consensus`(occurrence 분해는 그 산출물 기준이며 본문 카운트는 unique). **프로덕션 후보인 3B LoRA는 valid_error 0.**
- **7B의 raw 날조 16건 중 진짜 날조는 0.** 잔여 9건은 전부 `Java와 Spring Boot`·`Spring Boot와 REST API`·`React와 TypeScript` 같은 **'와/과' 복합 스킬구**(정규화 SPLIT_RE에 '와/과' 미포함이라 잔류) — 포맷 일탈이지 범위밖 날조 아님. 즉 7B의 높은 raw 카운트는 **품질 저하가 아니라 포맷 규율 약화**.

## 5. judge 절차 (semantic 처리)
- normalized 잔여 17 occ(16 unique) → `judge_packet_builder`로 packet 생성 → **3-lens(grounding/semantic/mechanics) 독립 판정 → `judge_consensus`**.
- 합의: valid_error 1 · acceptable_gray 8 · harness_fp 7 · needs_policy 0 (conf 0.844). 산출물 CareerTunerAI `results/2026-06-26-7b-smoke-002-judge/`.

## 6. 판단 — 7B LoRA / 3B 유지 / RAG 우선
**→ 3B LoRA 유지 + RAG 우선. 7B 전환·7B LoRA 재학습 보류.** 근거:
- **7B base는 3B LoRA를 못 이긴다**: success 최저(0.867), semantic valid_error 0(=LoRA와 동률, 우위 없음), raw 날조 최다(전부 포맷 노이즈), **latency 50%↑**(3403 vs 2263ms), **VRAM 2배**.
- 7B의 유일한 강점은 E1 grounding(0.067)·CJK(0.017)인데, E1은 LoRA의 유창성 tradeoff(백엔드 guard가 잡음, 점수·판단엔 영향 없음)이고 CJK는 LoRA 재학습 backlog로 별도 해결 대상.
- **7B LoRA 재학습은 이 smoke로 정당화되지 않음**: base 7B가 무이점인데 2배 비용/지연을 감수하고 재학습할 ROI가 낮다. 실제 갭(MSSQL 같은 입력 밖 제품명 grounding, 부족역량 서술)은 **RAG가 더 직접적**으로 해결한다.
- 따라서 우선순위: ① 3B LoRA 유지 ② RAG로 grounding/제품명 정확도 보강 ③ (선택) CJK 재학습 backlog. 7B LoRA는 RAG 이후 재평가.

## 7. 부수 발견 / backlog
- **normalizer '와/과' 미분할**: SPLIT_RE에 '와/과'를 추가하면 7B의 복합구 다수가 결정론 해소돼 judge 부담↓. 단 이번 PR 범위 밖(별도 검토).
- 7B는 학습추천을 `[{skill,why}]` 대신 문자열/복합구로 출력하는 **JSON 계약 규율이 3B LoRA보다 약함** — 7B 채택 시 SFT 필수 시사.
- MSSQL valid_error 1건은 needsHumanReview — 사람 확인 후 base 데이터/그라운딩 정책에 반영.

## 8. 제약 (엄수, 미변경)
backend 서비스 모델/기본값 변경·7B fine-tuning·RAG 착수·D/F 모델·E1 guard 완화·E2 observer→validator 승격 없음. main repo raw JSON/log 미커밋. PR 자가 merge 금지. (reports/48 계획의 stage3 repeat3은 7B 무유망으로 미실시.)

## 산출물
- 메인 repo: reports/48(계획, #145 머지됨) + 이 결과(49, follow-up PR) + 하니스 견고성 수정·test_eval_robustness(follow-up PR). 기반 코드: #141(병렬지표)·#142(judgeId) 머지됨.
- CareerTunerAI: `results/2026-06-26-7b-smoke-002/`(3모델 eval JSON·runtime_metrics·vram_snapshot·preflight), `results/2026-06-26-7b-smoke-002-judge/`(packet·3-lens verdicts·consensus·semantic_metrics).

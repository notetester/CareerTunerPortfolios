# Phase 2 — 7B smoke benchmark 계획 (2026-06-26)

> C 자체모델 v1(3B LoRA) 문서화·judgeId 개선 마감 후 다음 단계. **서비스 모델을 7B로 바로 교체하지 않는다.**
> golden60 동일 하니스로 **3B LoRA vs 3B base vs 7B base**를 비교해 *7B 전환 / 7B LoRA 재학습 / 3B 유지 / RAG 우선*을 판단.
> raw 결과는 CareerTunerAI artifact repo에만. backend 서비스 모델 설정 변경·fine-tuning·RAG 착수 없음.

## 1. 비교 대상 (3 모델)
| 모델 | 역할 | Ollama 태그 | 크기 |
| --- | --- | --- | --- |
| careertuner-c-career-strategy-3b | 현재 3B LoRA 자체모델 | `careertuner-c-career-strategy-3b:latest` | 1.9 GB |
| qwen2.5:3b-instruct | 기존 3B base | `qwen2.5:3b-instruct` | 1.9 GB |
| **qwen2.5:7b-instruct** | **7B base(신규 비교)** | `qwen2.5:7b-instruct` | 4.7 GB |

## 2. Preflight (완료, 2026-06-26)
- 4090에서 `ollama list` / `/v1/models` / `nvidia-smi` 확인 → CareerTunerAI `results/2026-06-26-7b-smoke-001/preflight.txt`.
- `qwen2.5:7b-instruct` 미설치였음 → **base pull 진행(`ollama pull`, fine-tuning 아님)** → 4.7GB(ID 845dbda0ea48) 설치 확인.
- VRAM: RTX 4090 23,028 MiB 중 baseline 1,352 MiB 사용(여유 충분). 3개 모델 모두 `/v1/models`에 노출 확인.
- **7B fine-tuning은 시작하지 않음.** 이번 단계는 base 7B smoke 한정.

## 3. 단계형 smoke (full run 직행 금지)
| 단계 | jobId | 구성 | 목적 |
| --- | --- | --- | --- |
| 1 (현재) | `2026-06-26-7b-smoke-001` | **limit 8, repeat 2**, 3모델 | 파이프라인·VRAM·기본 품질 빠른 확인 |
| 2 | `2026-06-26-7b-smoke-002` | golden60 전체, repeat 2 | 본 비교 |
| 3 (조건부) | `2026-06-26-7b-smoke-003` | golden60, repeat 3 | 7B 유망 시에만 |

- `run_latest_job.ps1`에 **`params.limit`** 지원 추가(앞 N케이스만). allowlist taskType 구조·고정 명령 유지(raw 명령 실행 없음).
- 4090 실행은 기존 큐 방식(`run_latest_job.ps1`). 게이트 `requiresDevContains`로 #141 병렬 지표 코드(`hallucinated_skill_normalized_count`)가 dev에 있어야 실행.

## 4. 평가 지표 (golden-set-002 / golden60 동일 하니스)
contract success · json_parse_rate · **raw / normalized / semantic** hallucination · E1 grounding violation · E2 high/review ·
CJK leak · PARSE_FAIL · timeout · latency avg/p95 · **VRAM**.
- #141 이후 추가된 `hallucinated_skill_{raw,normalized,resolved_fp}_count`(하니스 자동 산출) 반드시 포함.
- **VRAM/런타임:** `run_latest_job.ps1`가 모델별 `nvidia-smi memory.used`(평가 전 baseline + 모델 로드 후), `ollama ps`,
  `warm_avg/p95_latency_ms`를 `runtime_metrics.json` + `vram_snapshot.txt`로 저장.

## 5. semantic metric 처리 (중요)
`eval_fit_model.py`는 **raw / normalized / resolved_fp 까지만 결정론으로 자동 산출**한다. `semantic_hallucination_count`
(valid_error)는 **AI judge consensus가 있어야 확정**된다. 따라서 7B 결과에서:
- **normalized residual = 0** → `semantic_hallucination_count = 0 (후보 없음)`으로 보고 가능.
- **normalized residual > 0** → 별도 단계로 (1) `judge_packet_builder.py`로 judge candidate packet 생성 →
  (2) 기존 AI judge ensemble/`judge_consensus.py` 절차로 semantic valid_error 산출. **하니스 결과만으로 semantic 0 단정 금지.**

## 6. 판단 기준 (결과 해석)
| 조건 | 결론 |
| --- | --- |
| 7B가 success·semantic·E1·CJK에서 명확히 우수 + latency/VRAM 감당 가능 | → **7B LoRA 재학습 후보** |
| 품질 비슷한데 latency/VRAM 비용 큼 | → **3B LoRA 유지** |
| 7B base가 3B LoRA와 비슷하거나 못함 | → **7B 전환 보류, RAG 우선** |

- **7B가 좋아 보여도 바로 교체하지 않는다.** 이번은 의사결정용 smoke이지 프로덕션 전환이 아니다.

## 7. 산출물
- 메인 repo: 이 계획(48), 결과는 별도 PR `reports/49_7b_smoke_benchmark_result.md`(평가 종료 후).
- CareerTunerAI: `jobs/open/2026-06-26-7b-smoke-001.json`, `scripts/run_latest_job.ps1`(limit/VRAM),
  `results/2026-06-26-7b-smoke-001/`(preflight + 모델별 eval JSON + runtime_metrics + vram_snapshot).

## 8. 제약 (엄수)
backend 서비스 모델/기본값 변경 금지 · 7B fine-tuning 금지 · RAG 착수 금지 · D/F 모델·설정 미변경 ·
E1 guard 완화 금지 · E2 observer를 validator/fallback으로 승격 금지 · main repo에 raw JSON/log 미커밋 ·
CareerTunerAI submodule 미등록 · PR 자가 merge 금지.

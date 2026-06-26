# 골든60 HALLUCINATED_SKILL 다모델 교차검증 — Claude + Codex(gpt-5.5) (2026-06-26)

> reports/39 의 semantic skill judge 레이어가 남긴 잔여 13후보를 **2개 독립 프론티어 모델**이
> 각자 판정해 교차검증. 결론: **두 모델 모두 valid_error=0** — base 의 raw HALLUC 에 진짜 범위밖 날조 0(다모델 확증).
> Codex 는 4090에서 SSH 로 `codex exec` 호출(AI↔AI 채널). raw verdicts/consensus 는 CareerTunerAI.

## 방법
- 대상: golden-set-002 의 HALLUCINATED_SKILL 잔여 후보 13건(stage 1 정규화 후, reports/39).
- 판정자 2모델:
  - **Claude Opus 3-lens ensemble** (grounding / semantic / mechanics) — reports/39.
  - **Codex (gpt-5.5)** 독립 1-pass — 4090에서 `ssh … codex exec --sandbox danger-full-access "judge packet 읽고 판정"`.
- 4개 verdict(Claude 3 + Codex 1)을 `judge_consensus.py` 다수결로 병합.

## 결과
| 지표 | 값 |
| --- | --- |
| **semantic_hallucination (valid_error)** | **0** (Claude 단독·Codex 단독·합의 모두 0) |
| Codex 단독 분포 | acceptable_gray 6 · harness_false_positive 7 · valid_error 0 |
| 4-judge consensus(unique) | harness_false_positive 6 · acceptable_gray 3 · needs_policy 4 · valid_error 0 |
| needsHumanReview | 4 (라벨 이견) · judge_confidence 0.639 |
| raw / stage1해소 / 잔여 | 30 / 15 / 15 (불변) |

## 해석
- **핵심: 두 독립 모델이 따로 판정해도 valid_error=0.** base 의 30 raw HALLUC 은 전부 문자열 아티팩트이거나
  duties/allowedSkills 와 의미상 연결된 gray-zone — **진짜 범위밖 날조는 0건.** reports/38 가설("base 12× 날조는
  과장")을 *단일 모델(reports/39)*에 이어 *다모델*로 재확증.
- needs_policy 4건은 "오류 여부"가 아니라 **acceptable_gray vs harness_false_positive 라벨 이견**(Codex 가 일부를
  acceptable_gray 로, Claude 가 harness_false_positive 로). 둘 다 "오류 아님" — 정확한 라벨은 사람 정책으로 확정.
- 즉 LoRA↔base 차이는 '날조율'이 아니라 skill 필드 포맷 규율(reports/39 결론)이라는 점이, **모델 의존적이지 않음**이
  확인됨.

## 산출물
- 메인 repo: 이 문서(요약).
- CareerTunerAI `results/2026-06-23-golden-set-002-review/`: `verdicts_codex.jsonl`, 갱신된 `consensus.jsonl`·`semantic_metrics.json`.
- 인프라: Codex 호출은 4090 SSH(`codex exec --sandbox danger-full-access`), reports/40 채널.

## 다음
- needs_policy/acceptable_gray 라벨 정책 사람 확정 → 하니스 skill 매칭 정밀화 여부 결정(reports/39 §6).
- (선택) 3번째 모델 추가 시 동일 파이프라인에 verdict 파일만 더하면 됨.

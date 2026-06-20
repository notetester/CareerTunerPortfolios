# 3B vs 7B 비교 계획 (결론 미확정 — 비교 후 결정)

## 목적
Phase 1 은 `Qwen2.5-3B-Instruct` 로 파이프라인을 관통한다. 그러나 **최종 서빙 모델은 지금 확정하지 않는다.**
C/D가 메인 시연 기능일 수 있으므로, **같은 데이터셋으로 7B(`Qwen2.5-7B-Instruct`)를 학습**해 정량 비교한 뒤
최종 모델(또는 alias 연결 대상)을 결정한다.

## 비교 조건 (변인 통제)
* 동일 학습 데이터셋(`data/train.jsonl`, `data/val.jsonl`)
* 동일 LoRA 설정(r=16, α32, 3 epoch) — 베이스 모델만 교체
* 동일 서빙(Ollama, Q4_K_M, Modelfile temperature 0.2 / stop)
* 동일 평가 입력(C 골든셋 30~50건; 학습셋과 분리, 사람 검수)

## 비교 항목
| # | 지표 | 측정 방법 |
| --- | --- | --- |
| 1 | JSON 파싱 성공률 | 골든셋 응답을 `json.loads` 시도, 성공 비율 |
| 2 | 필수 키 누락률 | fitSummary/strengths/risks/strategyActions/learningTaskReasons 존재 검사 |
| 3 | 환각률(입력 외 사실 추가) | 응답의 회사/기술/자격증이 입력에 있는지 대조 |
| 4 | 점수·판단 변경 시도 | 응답에 fitScore/applyDecision 을 새로 쓰거나 바꾸는 시도 탐지(있으면 감점) |
| 5 | 한국어 자연스러움 | LLM-as-judge pairwise(3B vs 7B) + 사람 표본 |
| 6 | 전략 추천 설득력 | LLM-as-judge + 사람 표본 |
| 7 | 응답 지연(latency) | 동일 입력 N회 평균 ms |
| 8 | VRAM 점유 | 서빙 중 `nvidia-smi` 실측 |
| 9 | base vs LoRA 승률 | 각 모델별 튜닝 전/후 pairwise 승률 |

## 결정 규칙(잠정)
* 결과가 **비슷하면 3B 유지**(가볍고 공유 PC 친화).
* 7B가 **눈에 띄게 좋고**(특히 JSON 안정성·설명 품질) 공유 PC 운영상(6모델 VRAM 합산) 문제가 없으면 **7B 메인 허용**.
* 공유 PC VRAM 합산 우려가 크면 3B 메인 + 7B 는 보고서 증빙용.

> 결론 문구는 비교 실측 전까지 확정하지 않는다. 측정값을 이 문서에 채운 뒤 결정한다.

## 3B 학습 실측 (2026-06-21, 학습 단계)

mixed 데이터(train 375 / val 41), QLoRA 4bit r=16/α=32, epochs 3, 공유 RTX 4090.

| 지표 | 3B 값 |
| --- | --- |
| 학습 시간 | 약 12분 (01:14:05~01:26:25) |
| 최종 train_loss | 0.6267 (마지막 logged 0.4601) |
| 최종 eval_loss | 0.5155 (token acc 0.864) |
| eval_loss 추이(epoch 1→2→3) | 0.589 → 0.529 → 0.516 (매 epoch 개선) |
| grad_norm | 0.35~0.44 안정(발산 없음) |
| LoRA adapter / merged | 59.9MB / 6.17GB |

> 아래 "결과 기록" 표의 JSON 파싱·환각·승률·지연·VRAM 등은 **생성/Ollama 테스트 이후** 채운다(loss는 출력 품질을 보장하지 않음). 7B는 같은 데이터로 학습 후 비교.

## 3B 서빙 실측 (2026-06-21)

| 지표 | 3B 값 |
| --- | --- |
| GGUF f16 / Q4_K_M 크기 | 5.75 GiB / **1.80 GiB**(서빙 footprint — 공유 PC 경량 테넌트 목표 달성) |
| Ollama 모델 | `careertuner-c-career-strategy-3b:latest` (~1.9 GB) |
| test_infer JSON parse | **4/4 성공**(IT 2 + 비IT 2) |
| 금지키(fitScore/score/applyDecision/decision) | **0건** |
| 중국어/일본어 토큰 누출 | **0건** |
| 비IT 샘플 IT 표현 누출 | **0건** |
| 출력 truncation 임계 | max_tokens 512 실패 → **1024 통과**(서빙/백엔드 ≥1024 필수) |
| 백엔드 연동 | C_FIT_EXPLAIN OSS provider + 폴백, 단위테스트 18/18 통과 |

> 7B 비교는 같은 mixed 데이터로 학습 후 위 지표 + 응답지연 + base vs LoRA 승률을 채운다.

## 결과 기록 (채울 자리)
| 지표 | 3B | 7B | 비고 |
| --- | --- | --- | --- |
| JSON 파싱 성공률 | | | |
| 필수 키 누락률 | | | |
| 환각률 | | | |
| 점수 변경 시도 | | | |
| 한국어 자연스러움(승률) | | | |
| 전략 설득력(승률) | | | |
| 응답 지연 | | | |
| VRAM 점유 | | | |
| base→LoRA 승률 | | | |
| **결정** | | | |

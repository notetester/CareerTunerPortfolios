# 평가 신뢰도 결과 (2026-06-22)

> 신뢰도 라운드 실측: **격리 latency** + **blind 재판정**. 1차 결과의 두 큰 약점(케이스 1·2 누락, warm latency 7×)이 해소됐다. 원본은 artifact repo(미커밋), 여기엔 요약만.

## 1. ★ 격리 latency — 7× 격차의 정체 = VRAM 경합 (해소)
| 항목 | C LoRA 단독 | base 단독 |
| --- | ---: | ---: |
| warm_avg | **2,926ms** | 2,112ms |
| warm_p95 | 3,389ms | 2,838ms |
| tok/s | **159.0** | 154.8 |
| success | **36/36** | 35/36 |
| cold_start | 3,866ms | 4,205ms |
| timeout | 0 | 0 |
| VRAM(nvidia-smi) | 3,322MiB | 3,323MiB |

- **단독으로 로드하면 LoRA 2.9s, tok/s는 base와 거의 동일**(159 vs 155). → 이전 34s/180s 타임아웃은 **C+D(interview-3b 6.4GB)+base 동시 상주에 의한 VRAM 경합/스왑**이었다. **모델 자체 문제 아님.**
- LoRA가 base보다 ~1.4× 느린 건 **출력 토큰 1.4배**(460 vs 319) 때문 — per-token 속도는 같다.
- ★ **데모 리스크 해소:** 시연 때 단일 모델만 로드(reports/22)하면 LoRA는 **2.9s·36/36·timeout 0**으로 안정. (1차의 '7× 느림·timeout'은 공유 GPU 경합 artifact였음 — 1차 reports/17의 latency 해석을 이 결과로 확정 보정.)

## 2. blind 재판정 — LoRA 12/12 (케이스 1·2 복원)
- `selected-run` 으로 case 1·2(이전 run0 타임아웃) 복원 → **12/12 비교**.
- **A/B 블라인드**(라벨 은닉) 내용 기반 판정 후 key reveal → **전 12케이스 LoRA 우세**. 1차 라벨 판정(10/0+2보류)과 **일치**(편향 통제 후에도 동일).
- 우세 이유: base의 **보유/부족 사실 역전** 다수(clear-gap=필수4개 전부 보유 서술 등 5건) + LoRA의 **구체성**(실제 액션).
- LoRA 약점도 유지 기록: data-hold 자기모순(Spark/TensorFlow), 'CRM465' 날조 → reports/24 backlog.
- caveat: Claude 1차 판정 + 스타일로 모델 추정 가능 → **사람 검토 권장**, 표본 12 작음.

## 3. 4개 약점 처리 현황
| 약점(요청) | 상태 |
| --- | --- |
| pairwise run0 누락 | ✅ selected-run 복원(12/12) |
| warm latency 7× | ✅ VRAM 경합 확인 — 단독 2.9s, 모델 문제 아님 |
| Claude 단독 판정 편향 | ◑ blind 재판정으로 보강(사람 검토는 여전히 권장) |
| 실행조건 통제(quant/template) | ✅ 단일 모델 격리 + 동일 프롬프트/하니스 |

## 4. 종합 결론
- **LoRA 가치 = grounded specificity (입증).** 계약 포맷은 base가 약간 낫지만, **사실 충실도·구체성은 LoRA 우위**(블라인드에서도 12/12). 격리하면 **속도·안정성도 충분**(2.9s·36/36).
- **결정: LoRA 유지.** 다음: LoRA 약점(자기모순·날조) validator/prompt 차단(reports/24), 골든셋 40~60 확대 + 사람검토. (7B·RAG 보류)

## 5. 산출물 (원본 미커밋, artifact repo CareerTunerAI)
```text
c-fit-3b-isolated.json · base-isolated.json            # 격리 latency
c-fit-3b-pairwise-blind.json · *-blind-key.json        # 블라인드 입력·매핑
c-fit-3b-pairwise-blind-judgment.json                  # 블라인드 6축 판정
```

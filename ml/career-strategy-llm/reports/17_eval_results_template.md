# C_FIT_EXPLAIN 평가 결과 (템플릿 — 측정 후 채움)

> 4090에서 `scripts/eval_fit_model.py` 실행 결과 요약을 옮긴다(원본 JSON 은 미커밋).
> 측정일: `____-__-__` · 골든셋: `eval/golden_fit_cases.jsonl` (___건)

## 1. 헤드라인 — Baseline → Target → Actual
| 지표 | Baseline(base 3B) | Target | Actual(LoRA 3B) |
| --- | --- | --- | --- |
| JSON 파싱율 | _ | ≥0.95 | _ |
| 필수키 충족율 | _ | ≥0.95 | _ |
| 금지키 발생율 | _ | 0.00 | _ |
| CJK 누출율 | _ | 0.00 | _ |
| 환각 플래그율 | _ | ≤0.05 | _ |
| 계약 성공율(success) | _ | ≥0.90 | _ |
| p95 지연(ms) | _ | — | _ |

## 2. base vs LoRA 델타
| 지표 | base | LoRA | Δ(LoRA-base) |
| --- | --- | --- | --- |
| JSON 파싱율 | _ | _ | _ |
| 금지키 발생율 | _ | _ | _ |
| CJK 누출율 | _ | _ | _ |
| 환각 플래그율 | _ | _ | _ |
| mustMention 충족 | _ | _ | _ |

> 해석: LoRA 가 base 대비 ___. (개선이 작으면 정직히 적고 가치를 뉴로-심볼릭 경계·셀프호스팅·안정성으로 재프레임.)

## 3. OSS 성공률 / 폴백 (백엔드 경유, ai_usage_log)
| 모델 | 건수 | 비율 |
| --- | --- | --- |
| careertuner-c-career-strategy-3b (자체) | _ | _ |
| mock (폴백) | _ | _ |
| openai (폴백) | _ | _ |

폴백 사유 분해: `요청 실패(5xx) _ / 응답 처리 실패(JSON) _ / 타임아웃 _`

## 4. 재시도 전/후 (#105 효과)
| max-retries | OSS 성공율 | 폴백율 |
| --- | --- | --- |
| 0 | _ | _ |
| 2(기본) | _ | _ |
| **Δ** | _ | _ |

## 5. 실패 사유 분포(failure_reasons)
```text
(예: PARSE_FAIL: _, MISSING_MUST_MENTION: _, CJK_LEAK: _, ...)
```

## 6. 자원/지연
- avg/p95 latency: _ / _ ms · VRAM(ollama ps): _ GB · context: _

## 7. 해석 / 다음
- (강점) _
- (약점/위험) _
- (다음 액션) 골든셋 40~60 확대·사람검증 / 약점 케이스 보강 / (보류) 7B·RAG

# interview-3b:q4 vs F16 라이브 A/B 결과

- 실행일: 2026-07-07
- 엔드포인트: 공유 4090 Ollama(`http://127.0.0.1:11435/v1`, OpenAI 호환)
- 골든셋: `eval/interview_golden_cases.jsonl` (20케이스, temp=0, warmup 1)
- 후보: `interview-3b:q4`(Q4_K_M, 1.93GB) · 기준선: `interview-3b:latest`(F16, 6.18GB, **3.2배 큼**)
- 하니스: `scripts/eval_interview_model.py` → `scripts/compare_interview_quant.py`

## 판정: PASS (제안 임계값 5개 전부 충족)

| 기준 | 값 | 임계 | 판정 |
| --- | --- | --- | --- |
| c1 평균\|q4−f16\| | 3.8 | ≤ 5.0 | PASS |
| c2 agreement@10 | 0.90 | ≥ 0.90 | PASS(경계) |
| c3 q4 파싱률 ≥ f16 | 1.0 ≥ 1.0 | — | PASS |
| c4 q4 골든대비 MAE ≤ f16+3 | 9.65 vs 7.65 | slack 3 | PASS |
| c5 신규 CJK 누출 | 없음 | 0 | PASS |

- 두 모델 모두 JSON 파싱률 1.0, CJK 누출 0. 평균 점수 F16 55.05 / Q4 55.25.

## 주의 — LOW 밴드 꼬리 2건 (agreement 정확히 0.90)

18/20 케이스는 |Δ|≤10. 벗어난 2건은 모두 저품질 답변 LOW 밴드:

| caseId | Δ(q4−f16) | 성격 |
| --- | --- | --- |
| case-tech-transaction-low-005 | −20 | 약한 답변 채점 분기 |
| case-tech-empty-low-020 | +25 | 빈/무관 답변 채점 분기 |

저품질 답변에서 Q4가 F16보다 채점 분산이 크다(한쪽은 낮게, 한쪽은 높게). 합격/불합격을 가르는 중상위 밴드는 정합.

## 권고 (D 오너 비준 대상)

- Q4는 **OSS 서빙 후보로 손실 허용 범위**(3.2배 축소, 중상위 밴드 정합). `provider=oss` 서빙 시
  `INTERVIEW_EVAL_MODEL=interview-3b:q4` 로 전환 가능.
- 단 **임계값은 제안**이며, LOW 밴드 꼬리 2건과 agreement 경계(0.90)를 D가 확인 후 확정한다.
  꼬리가 문제되면 F16 유지 또는 골든셋 LOW 밴드 확장 후 재측정.

## 산출물 경로

- raw 결과/verdict JSON: 본체 `out/`(gitignore) → 영구 보관은 `CareerTunerAI` submodule.
  (`live_f16.json`, `live_q4.json`, `live_verdict.json`)
- 재현: 위 두 스크립트를 동일 인자로 재실행(§상단).

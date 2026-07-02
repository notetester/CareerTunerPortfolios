# A-only baseline repeat2 + judge 판정 summary

> 상세: [AIDocs report 80](../../../docs/ai-reports/areas/c-career-strategy/reports/80_a_only_baseline_repeat2_judge.md)
> (AIDocs commit `635f18a`). artifact: CareerTunerAI `benchmarks/evidence-attribution-baseline/` commit `7b5693a`.

- run2(60콜) 추가 실측: PASS 53 · 후보 4/5문장 · PARSE_FAIL 3. 라벨 변동 11/60 — 케이스 단위 재현성 낮음(단일 run 단정 금지 재확인).
- rubric v2 judge(후보 11문장, Claude 단일 judge — human 확정 대기): **TRUE_UNSUPPORTED_OWNERSHIP 3건**
  (EA-A-013 Redis · EA-A-003 Spring Boot critical · EA-A-004 PowerShell), 경고문/요구설명/조건문 6건, detector FP 1건, UNCLEAR 1건.
- **판단: "A true ≈ 0" 기각 — 그러나 3건 전부 검출기 포착 + E1/R3 검출 유형이라 계층 안전장치(A+E1+R3) 필요성의 정량 입증.**
  B 재론 근거 없음. 120케이스 확장 보류 — 다음 레버는 운영 gate 데이터 구조.
- 확정 다음 순서: ①관리자 gate review workflow ②gate reason 로그 FP triage ③(사용자) human gold label 확정.
- 한계: benchmark 프롬프트는 production 프롬프트 아님 · judge 단일 판정 · E1/R3 포착은 휴리스틱 매치 분석(E2E 실측 별도).

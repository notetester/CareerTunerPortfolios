# 설명 품질 pairwise 결과 — LoRA vs base (2026-06-22)

> 심판: 노트북 Claude(blind 아님, 사실 기준 채점). 입력: `out/eval/c-fit-3b-pairwise-input.json`(하니스 v2, --save-raw). 6축 1~5점.
> **결론을 한 줄로:** 계약(JSON 포맷)은 base가 낫지만, **설명 품질·사실 충실도는 LoRA가 압도** — 파인튜닝의 가치가 측정으로 확인됐다.

## 0. ★헤드라인 — 계약 평가의 반전
- 1차/2차 **계약 지표**(파싱·키·CJK)는 base ≥ LoRA 였다(reports/17).
- 그러나 **pairwise 품질**에서 비교 가능한 10케이스 중 **LoRA 10승 0패**(2케이스는 LoRA run0 타임아웃이라 보류).
- 결정적 이유: **base가 규칙엔진의 matched/missing 사실을 자주 역전**한다(부족 역량을 "보유"로 서술). 계약 지표는 prose 안의 이 오류를 못 잡았다 → **계약 ≠ grounding**.

## 1. base 의 grounding 역전 (가장 위험한 오류)
| case | base 오류 | 실제(규칙엔진) |
| --- | --- | --- |
| clear-gap(HOLD) | "Go·gRPC·Kubernetes·분산시스템 **필수 스킬 보유**" | 4개 전부 **부족** (치명적: 미달자에게 적격감) |
| design(HOLD) | "UI디자인·디자인시스템 **보유**" | 둘 다 **부족** |
| boundary(HOLD) | "Kafka **보유**" | **부족**(우대) |
| ambiguous(HOLD) | "Coroutines 지원자 역량" | **부족**(우대) |
| hold-tone(HOLD) | "C++ 강조하라" | **부족**(우대) |
→ **5/10 케이스에서 사실 역전.** 이 제품에서 가장 위험(부적격자에게 합격감을 줌). LoRA 는 같은 케이스에서 사실을 지켰다.

## 2. 6축 평균 (비교 가능 10케이스, 1=나쁨 5=우수)
| 축 | LoRA | base |
| --- | ---: | ---: |
| job_fit_relevance | ~4.6 | ~3.1 |
| specificity | **~4.5** | ~2.2 |
| evidence_grounding | ~3.8 | ~2.9 |
| risk_awareness | ~4.1 | ~2.8 |
| tone | ~4.0 | ~2.8 |
| non_it_domain_fit | ~3.3 | ~3.4 |
- LoRA 우위: **구체성**(실제 액션: "JPA 공식문서+Spring Boot 사례 구현", "ERP 교육 SAP/QuickBooks 수강"), 사실 충실도, 리스크 솔직함.
- base: 간결하지만 **일반적**(strengths=스킬 나열, action="교육 프로그램 참여"). 비IT IT용어 누출은 양쪽 0(NI 무승부).

## 3. 케이스별 승자
```text
boundary·clear-gap·ambiguous·hold-tone·design(HOLD) : LoRA  (base 사실 역전)
sales·finance(COMPLEMENT) : LoRA (구체성)
bait(COMPLEMENT) : LoRA (환각 없이 구체)
data-hold·marketing-apply : LoRA(근소) — 단 LoRA 자체 약점 동반(아래)
backend-apply·frontend-complement : 보류 (LoRA run0 타임아웃 → 출력 없음)
```

## 4. LoRA 의 약점 (정직)
- **case data-hold 자기모순**: strengths "Spark·TensorFlow 보유" ↔ risks "Spark·TensorFlow 부재"(둘 다 실제 부족). LoRA 의 가장 큰 grounding 슬립.
- **주변 사실 날조**: "CRM465"(가짜 제품명), "공공기관" 맥락 삽입.
- **stochastic 톤 슬립**: hold-tone 케이스 run2 에서 "즉시 지원" 1회(HOLD 부적합 표현) — 3회 중 1회.
- **속도**: warm 34s vs base 4.7s(7×) — 단 LoRA 13.5 tok/s(과생성 1.4배 + per-token ~5× 느림)는 **VRAM 경합/부분 오프로드 정황**(D·base 공존). 격리(단일 모델) 재측정 필요.

## 5. 결론 / 결정
- ★ **파인튜닝의 가치는 실재한다 — 측정으로 확인됨.** 계약 포맷은 base 가 잘하지만, **규칙엔진 사실에 충실한 구체적 한국어 설명**은 LoRA 가 분명히 우위(base 는 사실을 역전하는 위험 오류 빈발).
- **결정: LoRA 유지.** 이제 근거 있음(품질 pairwise).
- 발표 메시지: *"계약과 품질을 모두 측정했다. 계약은 base 가 낫지만, pairwise 품질에서 base 는 5/12 케이스에서 보유/부족 사실을 역전했고 LoRA 는 사실을 지키며 더 구체적이었다 — 파인튜닝은 JSON 포맷이 아니라 **grounded specificity** 로 값을 한다."*

## 6. 다음 액션
1. **하니스 fix**: pairwise 가 run0 대신 **성공한 run** 을 고르게(케이스 1·2 비교 복원).
2. **격리 latency 재측정**: D 언로드·단일 모델·warmup → LoRA 진짜 속도.
3. **LoRA 개선(재학습 아님, 데이터/검증)**: 자기모순·주변 날조(CRM465)·HOLD 톤 슬립을 골든셋/검증으로 잡고, 다음 학습 라운드 입력 정제 항목으로.
4. 골든셋 40~60 확대 + 사람검증.
- (보류 유지) 7B·RAG.
> 판정 원본: `out/eval-sync/c-fit-3b-pairwise-judgment.json`(artifact repo, 미커밋). 메인 repo 엔 이 요약만.

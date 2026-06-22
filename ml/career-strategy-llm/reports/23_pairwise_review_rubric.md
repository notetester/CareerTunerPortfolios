# pairwise 설명 품질 — 블라인드 리뷰 루브릭

> reports/21의 1차 pairwise는 유용하나 **Claude 단독·라벨 노출** 판정이라 편향 공격을 받을 수 있다.
> 이 라운드는 **A/B 블라인드** + 명시 루브릭으로 보강한다. ★ Claude 리뷰는 **1차 판정**이며, **사람 검토로 보강 가능**(최종 증거는 사람 동의 권장).

## 1. 6축 (각 1~5; 1=나쁨 5=우수)
```text
1. job_fit_relevance   공고/프로필 매칭 설명이 정확한가
2. specificity         추상적 조언이 아니라 구체적 액션을 주는가
3. evidence_grounding  입력의 matched/missing/부족역량에 근거하는가(입력 밖 사실·고유명사 추가 X)
4. risk_awareness      부족역량/지원 리스크를 솔직히 말하는가
5. tone                APPLY/COMPLEMENT/HOLD 판단에 맞는 톤인가(HOLD인데 즉시지원 권유 X)
6. non_it_domain_fit   비IT 직군에서 IT 용어를 과하게 섞지 않는가(IT 케이스는 3 중립)
```

## 2. 블라인드 방식
- 입력 `out/eval/c-fit-3b-pairwise-blind.json`: 케이스별 `candidateA`/`candidateB` 만(어느 쪽이 LoRA/base인지 숨김).
- 매핑 `out/eval/c-fit-3b-pairwise-blind-key.json`: `{caseId: {A: lora|base, B: ...}}` — **판정이 끝난 뒤에만** 연다(reveal).
- A/B 배정은 caseId 해시로 결정론(난수 X) → 재현 가능.

## 3. 판정 출력(케이스별) — `out/eval/c-fit-3b-pairwise-blind-judgment.json`(미커밋)
```json
{ "caseId":"...", "winner":"A|B|tie|both_bad",
  "scores":{"A":{"job_fit_relevance":1,"specificity":1,"evidence_grounding":1,"risk_awareness":1,"tone":1,"non_it_domain_fit":1},
            "B":{ }},
  "reason":"근거 1~2문장" }
```
- winner 는 6축 합으로(차이 ≤2 = tie, 둘 다 평균 ≤2 = both_bad). 판정 후 key로 A/B→모델 reveal해 승률 집계.

## 4. caveat (정직)
```text
- 본 판정은 Claude 1차 평가다. LLM judge 는 장황함·표면적 유창함에 끌릴 수 있어, 사람 검토로 보강한다.
- 1차 pairwise(reports/21)는 라벨 노출이었고, 본 라운드는 블라인드로 그 편향을 통제한다.
- 표본 12케이스는 작다 → 골든셋 40~60 확대 후 재판정.
- (참고) 1차에서 'data-hold 자기모순'·'CRM465'는 실제 LoRA 약점, "즉시 지원"은 하니스 오탐(부정문맥)으로 reports/24에서 분리.
```

## 5. 절차
```text
1) 4090: --pairwise --blind 로 selected-run blind 입력 생성(reports/25) → artifact repo push
2) 노트북: pull → Claude 가 blind 입력만 보고 6축 판정 → key로 reveal → 승률/축별 집계
3) 메인 repo 엔 요약만(reports/21 갱신 또는 신규)
```

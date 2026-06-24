# 설명 품질 pairwise 평가 계획 — LoRA vs base

> 1차 평가는 **계약(contract) 준수**만 쟀다 → base ≥ LoRA(reports/17). 하지만 LoRA를 파인튜닝한 목적은
> **한국어 설명의 품질·도메인 적합성**이고, 그건 아직 미측정이다. 이 라운드에서 **동일 케이스의 두 모델 설명을 pairwise로 비교**해 LoRA의 실제 가치(또는 무가치)를 판정한다.

## 1. 입력
하니스 v2 가 만든 `out/eval/c-fit-3b-pairwise-input.json`:
```json
{ "lora_model": "...", "base_model": "...",
  "axes": ["job_fit_relevance","specificity","evidence_grounding","risk_awareness","tone","non_it_domain_fit"],
  "pairs": [ { "caseId": "...", "domainGroup": "...", "expectedDecision": "...",
              "user_prompt": "...(입력 그대로)", "lora_output": "...", "base_output": "..." } ] }
```
- `--save-raw` 로 평가해야 `lora_output`/`base_output`/`user_prompt` 가 채워진다.

## 2. 심판(judge)
- **1순위: 노트북 Claude Code 세션이 직접 pairwise 판정**(API 키 불필요, 입력을 그대로 읽고 채점). blind 를 위해 어느 쪽이 LoRA/base 인지 모르게 라벨을 A/B 로 섞어 판정 후 매핑해도 좋다.
- (대안) OpenAI/Anthropic API judge 스크립트 — 키 필요. 이번엔 1순위로 진행.

## 3. 6축 채점 (각 1~5; 1=나쁨, 5=우수)
```text
1. job_fit_relevance   공고/프로필 매칭 설명이 정확한가
2. specificity         추상적 조언이 아니라 구체적 액션을 주는가
3. evidence_grounding  입력의 matched/missing/부족역량에 근거하는가(입력 밖 사실 추가 X)
4. risk_awareness      부족역량/지원 리스크를 솔직히 말하는가
5. tone                APPLY/COMPLEMENT/HOLD 판단에 맞는 톤인가
6. non_it_domain_fit   비IT 직군에서 IT 표현을 과하게 섞지 않는가(IT 케이스는 N/A=3 중립)
```

## 4. 판정 출력(케이스별) — `out/eval/c-fit-3b-pairwise-judgment.json`(미커밋)
```json
{
  "caseId": "case-it-backend-apply-001",
  "winner": "lora|base|tie|both_bad",
  "scores": {
    "lora": {"job_fit_relevance":1,"specificity":1,"evidence_grounding":1,"risk_awareness":1,"tone":1,"non_it_domain_fit":1},
    "base": {"job_fit_relevance":1,"specificity":1,"evidence_grounding":1,"risk_awareness":1,"tone":1,"non_it_domain_fit":1}
  },
  "reason": "근거 한두 문장"
}
```
- winner 는 6축 합으로 도출(동률±1 이내 = tie, 둘 다 ≤2 평균 = both_bad).

## 5. 요약 → 메인 repo 리포트만
판정 원본 JSON 은 `out/eval/`(미커밋). **메인 repo 에는 요약만**(예: `reports/21_pairwise_results.md`):
```text
- 승률: LoRA _ / base _ / tie _ / both_bad _
- 축별 평균(LoRA vs base) 6개
- 도메인별(IT/비IT) 승률
- 대표 사례 2~3개(왜 이겼/졌나)
- 결론: LoRA 유지/개선/대체 권고
```

## 6. 절차
```text
1) 4090: 하니스 v2 로 LoRA·base 평가(--save-raw) → pairwise-input 생성 (reports/19)
2) 4090: 결과를 artifact repo(out/eval-sync)로 push (reports/20)
3) 노트북: artifact repo pull → Claude 가 pairwise-input 읽고 6축 판정
4) 노트북: 요약만 메인 repo reports/ 에 커밋 → PR
```
- 재학습/7B/추가데이터/RAG 는 이번 단계에서 하지 않는다. 판정 후 개선 방향만 도출.

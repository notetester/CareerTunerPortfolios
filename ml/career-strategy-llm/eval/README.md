# C_FIT_EXPLAIN 평가 골든셋

C 자체모델(`careertuner-c-career-strategy-3b`)을 **서비스 계약 기준**으로 정량 측정하기 위한 골든셋.
C 모델은 점수를 만들지 않으므로(뉴로-심볼릭: 점수/판단은 규칙엔진), 평가는 **점수 정답이 아니라 설명 JSON 계약 위반 여부**를 본다.

## 파일
- `golden_fit_cases.jsonl` — 1줄 = 1케이스(현재 **60건**).
- 하니스: `../scripts/eval_fit_model.py` (이 골든셋을 입력으로 실행).

## 케이스 스키마
```json
{
  "id": "case-it-backend-apply-001",
  "domainGroup": "IT_SOFTWARE",
  "expectedDecision": "APPLY",
  "input": { "...seed_profiles 산출 형태(build_fit_user 입력)..." },
  "expected": {
    "requiredKeys": ["fitSummary","strengths","risks","strategyActions","learningTaskReasons"],
    "forbiddenKeys": ["fitScore","score","applyDecision","decision"],
    "mustMention": ["부족 필수역량 등 반드시 언급할 토큰"],
    "mustNotMention": ["비IT인데 섞이면 안 되는 IT 용어 등"],
    "allowedSkills": ["learningTaskReasons 에서 언급 허용되는 스킬(=required+preferred)"],
    "forbiddenClaims": ["합격 보장","합격률","즉시 지원 등 금지 표현"]
  }
}
```
- `input` 은 `assemble_dataset.build_fit_user` 가 받는 seed dict(회사·직무·요구/보유 스킬 + 규칙엔진 사전계산값 fitScore/applyDecision/matched/missing). 하니스가 그대로 user 프롬프트로 만들고, system 은 `synth_prompts.FIT_EXPLAIN_SYS`(학습/서빙과 동일).
- `allowedSkills` 밖 스킬을 `learningTaskReasons` 에 언급하면 환각(HALLUCINATED_SKILL)으로 본다.

## 커버리지 (60건)

| 축 | 현재 분포 |
| --- | --- |
| 의사결정 | APPLY 10 · COMPLEMENT_BEFORE_APPLY 21 · HOLD 29 |
| 직무군 | IT_SOFTWARE 20 · DATA_AI 7 · TRADE_LOGISTICS 5 · HR_RECRUITING 4 · IT_INFRA_CLOUD_SECURITY 4 · SALES 4 · 그 외 10개 비IT 직무군 16 |
| 안전·경계 | 점수 경계, 명확한 부족역량, 연관·비일치 보유역량, 희소 입력 환각 유발, 비IT 용어 누출, HOLD 지원 권장 금지 포함 |

첫 12개 핵심 케이스를 유지하면서 직무군·판정·난도를 60개로 확장했다. 정확한 행 정본은 JSONL이며,
건수나 분포를 바꾸면 이 표와 모델 평가 보고서를 함께 갱신한다.

## 하니스가 측정하는 지표
`json_parse_rate · required_key_rate · forbidden_key_rate · cjk_leak_rate · hallucination_flag_rate · avg/p95_latency_ms · success_count · failure_reasons`
- success = 파싱 + 필수키 + 금지키 없음 + CJK 없음 + mustMention 충족 + mustNotMention/forbiddenClaims/허용밖스킬 없음 **모두 통과**.

## 실행
```bash
cd ml/career-strategy-llm
# 모델 직접 평가
python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl \
  --base-url http://localhost:11434/v1 --model careertuner-c-career-strategy-3b \
  --out out/eval/c-fit-3b-eval.json
# 모델 없이 파이프라인 점검(드라이런)
python scripts/eval_fit_model.py --cases eval/golden_fit_cases.jsonl --mock --out out/eval/mock.json
```
결과(`out/eval/*.json`)는 gitignore 됨 — **커밋하지 않는다**. 요약 수치만 `docs/ai-reports/areas/c-career-strategy/reports/`의 장문 보고서에 옮긴다.

## 유지·검증 TODO

- 60건의 **사람 재검토**를 주기적으로 수행한다(특히 비IT는 직무/HR 관점 SME 리뷰).
- mustMention/allowedSkills를 정교화하고, 새 실패가 발견될 때 경계·환각 회귀 케이스를 추가한다.

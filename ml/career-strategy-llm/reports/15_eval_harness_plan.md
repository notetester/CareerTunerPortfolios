# 평가 하니스 계획 — C_FIT_EXPLAIN 정량 측정

> 목표: "잘 된다"를 **숫자**로 보이기. C 자체모델을 서비스 계약 기준으로 측정하고, base 대비·재시도 전후·폴백 사유까지 정량화한다.
> 7B/RAG 는 아직 보류. 이번은 3B 측정 체계 구축.

## 1. 두 경로로 측정
| 경로 | 무엇 | 도구 |
| --- | --- | --- |
| (A) **모델 직접** | Ollama `/v1/chat/completions` 에 골든셋 투입 → 계약 위반율·지연 | `scripts/eval_fit_model.py` + `eval/golden_fit_cases.jsonl` |
| (B) **백엔드 OSS 경유** | 실제 `POST /api/fit-analyses/...` → 뉴로-심볼릭 조립·폴백 포함 end-to-end | 백엔드(provider=oss) + `ai_usage_log` 집계 |

(A)는 모델 자체 품질, (B)는 제품 경로의 실제 성공률/폴백을 본다.

## 2. (A) 모델 직접 — 측정 지표
`json_parse_rate · required_key_rate · forbidden_key_rate · cjk_leak_rate · hallucination_flag_rate · avg/p95_latency_ms · failure_reasons`
- 실패 사유 분류: `HTTP_* / EMPTY / PARSE_FAIL / MISSING_REQUIRED_KEY / FORBIDDEN_KEY / CJK_LEAK / FORBIDDEN_CLAIM / MISSING_MUST_MENTION / FORBIDDEN_MENTION / HALLUCINATED_SKILL`.

## 3. (B) OSS 성공률/폴백률 — ai_usage_log 기반
실제 제품 경로 성공률 = OSS 호출 중 `model=careertuner-c-career-strategy-3b`(자체모델) 비율, 폴백 = `model=mock`/openai.
```sql
-- 최근 기간 C 적합도 OSS 성공/폴백 집계
SELECT model, COUNT(*) AS n
FROM ai_usage_log
WHERE feature_type = 'FIT_ANALYSIS'
GROUP BY model;
```
- 폴백 사유: 백엔드 로그 `C 자체모델 ... 폴백` WARN 의 메시지(요청 실패 5xx / 응답 처리 실패 JSON / 타임아웃) 집계.

## 4. 재시도 전후 비교 (#105 효과 정량화)
`oss.max-retries` 를 0(재시도 없음) vs 2(기본)로 두고 동일 골든셋·동일 부하에서 OSS 성공률을 비교 → **재시도가 폴백률을 얼마나 줄였는지** 수치로.
```text
max-retries=0 : 성공률 ?,  폴백 ?
max-retries=2 : 성공률 ?,  폴백 ?  (Δ)
```

## 5. base vs LoRA 비교
| 대상 | 비고 |
| --- | --- |
| Qwen2.5-3B-Instruct (base) | Ollama `qwen2.5:3b-instruct` 로 받아 동일 프롬프트로 평가(별도 GGUF 변환 불필요) |
| `careertuner-c-career-strategy-3b` (LoRA) | 현재 자체모델 |
| Mock | 규칙엔진 하한(설명은 규칙 기반) |
| (가능 시) OpenAI | 상한 참고 |
- 핵심 그래프: **JSON 준수율·금지키율·CJK·환각·mustMention** 의 base vs LoRA **델타**.
- LoRA 가 base 대비 개선이 작으면(<5%p) 정직하게 적고, 가치를 **뉴로-심볼릭 경계 + 셀프호스팅 + 안정성**으로 재프레임.

## 6. 발표용 표/그래프 항목
```text
- Baseline(base 3B) → Target → Actual(LoRA 3B) 한 장 표
- JSON 준수율 / 금지키율 / CJK / 환각 : base vs LoRA 막대
- OSS 성공률 + 폴백 사유 분해(파이/스택)  ← '2/3 실패' 일화를 실제 분모로 대체
- 재시도 전/후 성공률 Δ
- 3B 지연(avg/p95) · VRAM(ollama ps)
```

## 7. 실행/산출물 규칙
- 하니스 실행 결과 `out/eval/*.json`, 백엔드 로그 → **커밋 금지**(gitignore). 요약 수치만 `reports/17_eval_results_template.md` 에 옮긴다.
- 4090 실행 명령: `reports/16_4090_eval_commands.md`.
- 발표 슬라이드/그래프 원본은 `docs/storyboard` 서브모듈(**팀 공용** — 담당자별 `A/`~`F/`)의 `C/` 하위에 둔다(메인 repo 용량 지양). 스토리보드 자체는 C 전용이 아니라 팀 전체 공유 공간이다.

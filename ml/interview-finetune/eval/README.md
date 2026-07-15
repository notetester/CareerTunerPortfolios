# 면접 채점 모델 양자화(Q4) 골든셋 · 검증 하니스

`interview-3b`(F16 baseline) 대비 `interview-3b:q4`(Q4_K_M 양자화)의 **채점 품질 저하 여부**를
오프라인 골든셋으로 정량 비교하기 위한 fixture + 러너 + 비교기 세트다.

초기 20건 제안 단계는 종료됐다. 현재 fixture는 독립 판정단이 라벨링한 **60건**이며, 2026-07-07
4090 Ollama 라이브 A/B 결과 Q4를 운영 후보에서 제외하고 F16을 유지했다. 최종 수치와 판단은
[LIVE_AB_RESULT](LIVE_AB_RESULT.md)를 정본으로 본다.

D 도메인 면접 채점 모델은 **점수를 만드는** 모델이다(C 모델과 다름 — C는 점수를 규칙엔진이 만든다).
따라서 이 골든셋은 **점수 정답(expectedScore)** 을 가지며, 양자화가 (1) 점수를 얼마나 흔드는지,
(2) JSON 계약을 얼마나 깨는지, (3) 한국어 외 CJK를 새로 누출하는지를 본다.

## 파일
- `interview_golden_cases.jsonl` — 1줄 = 1케이스(현재 **60건**). `*.jsonl` 전역 무시에서
  `.gitignore` negation(`!eval/interview_golden_cases.jsonl`)으로 **추적한다**(fixture이므로).
- `panel_scores.jsonl` — 두 독립 판정자의 점수와 평균 panel score. 현재 골든 `expectedScore`의 근거다.
- 러너: `../scripts/eval_interview_model.py` (골든셋 → per-case 결과 JSON).
- 비교기: `../scripts/compare_interview_quant.py` (q4 결과 vs f16 결과 → verdict).

## 케이스 스키마 (1 JSON / line)
```json
{
  "id": "case-tech-di-high-001",
  "questionType": "TECH",                // TECH | PERSONALITY | SITUATION
  "companyName": "토스",
  "jobTitle": "백엔드 개발자",
  "question": "Spring에서 의존성 주입(DI)이 무엇이고 ...",
  "referenceModelAnswer": "의존성 주입은 ...",  // 만점 기준 모범답안(= OssAnswerEvaluator referenceModelAnswer)
  "answer": "의존성 주입은 객체가 ...",          // 채점 대상 지원자 답변
  "expectedScore": 94,                    // 두 독립 판정자 평균을 반올림한 0~100 골든 점수
  "expectedBand": "HIGH",                 // HIGH>=85 | MID_HIGH 70~84 | MID 40~69 | LOW<40
  "panelClaude": 92,
  "panelCodex": 95,
  "rubricNotes": "핵심(외부 주입·결합도·컨테이너)을 ..."  // 채점 근거 메모(사람용)
}
```
- 러너는 `question / answer / referenceModelAnswer / companyName / jobTitle` 을
  **`OssAnswerEvaluator.evaluateAnswer` 와 완전히 동일한 user 프롬프트 모양**으로 조립하고,
  system 은 `InterviewPromptCatalog.EVALUATION_SYSTEM_PROMPT` 를 쓴다(train/serve/eval skew 방지).
- `expectedScore` 는 F16/Q4 **양쪽의 골든 대비 오차(MAE)** 를 재는 기준이다. 절대 정답이라기보다
  "양자화로 오차가 F16보다 크게 나빠지지 않는가"를 보는 앵커다.

## 커버리지 (60건)

| 밴드 \ 유형 | TECH | PERSONALITY | SITUATION | 합계 |
| --- | ---: | ---: | ---: | ---: |
| HIGH(85+) | 11 | 6 | 6 | 23 |
| MID_HIGH(70~84) | 2 | 1 | 3 | 6 |
| MID(40~69) | 9 | 5 | 3 | 17 |
| LOW(<40) | 8 | 3 | 3 | 14 |
| 합계 | 30 | 15 | 15 | 60 |

- 모든 case 는 한국어(제품이 한국어). 회사·직무를 실제 채용 맥락으로 다양화.
- 빈 답변과 "잘 모르겠습니다" 같은 LOW 극단으로 최하점 clamp/파싱을 검증한다.

## 하니스가 측정하는 per-case 지표
러너(`eval_interview_model.py`)가 케이스마다 남기는 값:
`{ id, model, parsed_score(0~100), json_ok, cjk_leak, latency_ms, raw }`
- `parsed_score` = 모델 JSON 의 `score` 를 `extractJsonSpan`(백엔드 미러) 후 파싱·`clampScore(0..100)`.
- `json_ok` = span 추출·JSON 파싱·`score` 정수화가 모두 성공했는가.
- `cjk_leak` = 출력에 일본어 가나 / CJK 한자(한글 제외)가 섞였는가.

## Q4-vs-F16 비교 지표 (compare_interview_quant.py)
두 per-case 결과 파일(q4, f16)을 케이스 id 로 묶어 계산:
- **per-case delta** = `q4_score - f16_score`
- **mean|q4-f16|** = 케이스별 절대 점수차의 평균(핵심 헤드라인)
- **agreement@10** = |q4-f16| <= 10 인 케이스 비율(밴드 경계에서의 안정성)
- **tag MAE vs golden** = 유형(TECH/PERSONALITY/SITUATION)별 |score - expectedScore| 평균, q4/f16 각각
- **json_parse_rate / cjk_leak_rate** = 유형별, q4/f16 각각

## 초기 Q4 하니스 기준과 최종 결정

아래 5개 임계값은 20건 단계에서 만든 **초기 양자화 회귀 기준**이다. 하니스 자체의 회귀 신호로는
남기되, 현재 배포 결정은 F16 자기참조가 아니라 독립 판정단 60건 비교를 우선한다.

전체를 **모두** 만족하면 `PASS`, 하나라도 어기면 `FAIL`:
1. `mean|q4-f16| <= 5` — 양자화로 인한 평균 점수 이동이 5점 이내.
2. `agreement@10 >= 0.90` — 케이스의 90% 이상에서 q4와 f16이 10점 이내로 일치.
3. `q4 json_parse_rate >= f16 json_parse_rate` — 양자화가 JSON 계약을 더 깨지 않음.
4. `q4 MAE(vs golden) <= f16 MAE + 3` — 양자화가 골든 대비 오차를 3점 넘게 악화시키지 않음.
5. **no new CJK leaks** — q4 의 cjk_leak 케이스 집합이 f16 대비 새로 늘지 않음.

이유: 면접 채점은 밴드(HIGH/MID_HIGH/MID/LOW) 경계에서의 안정성이 UX에 직결되므로, 절대 점수보다
**F16 대비 이동(1·2)** 과 **골든 대비 악화(4)** 를 함께 본다. 계약(3)·안전(5)은 회귀 게이트다.

라이브 판정단 기준 결과는 F16 MAE 8.30/10점 이내 일치 0.700, Q4 MAE 9.53/일치 0.617이었다.
따라서 Q4는 배포하지 않는다. F16도 40~84점 중간 구간의 오차가 커 단독 사용자 채점기로 승격하지 않고,
현재 백엔드 provider fallback을 유지한다.

## 실행
```bash
cd ml/interview-finetune

# 1) 골든셋 -> q4 결과 (라이브: Ollama q4)
python scripts/eval_interview_model.py --cases eval/interview_golden_cases.jsonl \
  --base-url http://localhost:11434/v1 --model interview-3b:q4 \
  --out out/eval/interview-q4.json --save-raw

# 2) 골든셋 -> f16 결과 (라이브: Ollama f16)
python scripts/eval_interview_model.py --cases eval/interview_golden_cases.jsonl \
  --base-url http://localhost:11434/v1 --model interview-3b \
  --out out/eval/interview-f16.json --save-raw

# 3) 비교 -> verdict
python scripts/compare_interview_quant.py \
  --q4 out/eval/interview-q4.json --f16 out/eval/interview-f16.json \
  --cases eval/interview_golden_cases.jsonl --out out/eval/interview-quant-verdict.json

# 오프라인 점검(모델 없이 — GPU 게이트 다운 시):
python scripts/eval_interview_model.py --cases eval/interview_golden_cases.jsonl --mock \
  --model interview-3b:q4 --out out/eval/mock-q4.json --save-raw
```
결과(`out/eval/*.json`)는 gitignore 됨 — **커밋하지 않는다**. 요약 수치만 D 영역 보고서로 옮긴다.

## 다음 검증 조건

- 40~84점 부분 정답 사례를 보강해 새 후보를 학습한다.
- 새 후보는 같은 60건 판정단 골든셋과 JSON/CJK 계약을 모두 재검증한다.
- critique/report 프롬프트 계약은 채점 계약과 분리한 별도 골든셋으로 확장한다.

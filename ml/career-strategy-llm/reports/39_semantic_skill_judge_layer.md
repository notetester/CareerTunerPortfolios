# Semantic Skill Judge Layer — 의미 기반 스킬 판정 레이어 (2026-06-23 golden-set-002)

> 자동 하니스의 문자열 기반 `HALLUCINATED_SKILL` 판정을 **대체하지 않고**, AI judge ensemble 로
> 보완하는 병렬 레이어. 사람 대량검토 대신 **AI 로 대규모 정밀 판정**한다(요약만 메인 repo,
> 상세 패킷·verdict 는 CareerTunerAI `results/2026-06-23-golden-set-002-review/`).

## 1. 문제 (reports/38 후속)

`eval_fit_model.py` 의 HALLUCINATED_SKILL 은 `learningTaskReasons[].skill` 을 allowedSkills 와
**공백제거+소문자 정확매칭**(#124)으로만 본다. 그래서 모델이 in-scope 스킬을 **콤마 나열·접미구·
괄호**로 풀어쓰면 over-count 된다. golden60 에서 base 의 HALLUC 30건은 대부분 이 gray-zone 이었고
(reports/38), "느슨한 표현을 어디까지 허용?"은 정책 판단이라 **의미 판정**이 필요했다.

## 2. 3단계 레이어 (코드: `scripts/`)

| 단계 | 모듈 | 역할 |
| --- | --- | --- |
| 1. 결정론 정규화 | `skill_normalizer.py` | NFKC·공백·대소문자·괄호 제거, 콤마/슬래시/중점/`및` 분할, 접미 wrapper(사용법/활용/이해/방법론/시스템 등) 반복 제거, allowedSkills 대비 정확/부분 매칭. **명백한 문자열 오탐만** 내리고 valid_error 는 절대 자동 단정 안 함 |
| 1.5 패킷 빌더 | `judge_packet_builder.py` | 결과+골든셋에서 후보 수집→정규화→**잔여만** `judge_packet.jsonl`(풀 스키마) + `judge_chatgpt_packet.md`(사람/ChatGPT용) + `normalization_stats.json` |
| 2. AI judge ensemble | `semantic_skill_judge.py` | 같은 packet 을 **3 lens 가 독립(blind) 판정**: grounding(엄격 근거) / semantic(의미 동치) / mechanics(문자열 아티팩트). 외부 AI verdict 수집 + mock(파이프라인 점검) |
| 3. consensus | `judge_consensus.py` | 3 verdict **다수결 합의** + **병렬 지표**. 동률·needs_policy·과반미달·저신뢰 → `needsHumanReview` |

### judge 스키마
- 입력(`judge_packet.jsonl`): caseId, model, run, flagType, flaggedText, field, allowedSkills, matchedSkills, missingSkills, jobRequirements(required/preferred/duties), profileSkills, profileCertificates, rawExcerpt, normalizer(status/method/softHits/unmatched), expectedDecision, occurrences
- 출력(verdict): `{candidateId, judge, decision, confidence, rationale, needsHumanReview}`
- decision ∈ `valid_error` / `acceptable_gray` / `harness_false_positive` / `needs_policy`

### consensus 규칙 (보수적)
다수결 라벨이 final. 동률→`needs_policy`. 아래 중 하나면 `needsHumanReview=true`: 어떤 judge 든
needs_policy 투표 · 어떤 judge 든 needsHumanReview · 과반 미달 · 합의 confidence < 0.6.

## 3. golden60 실측 결과 (base=qwen2.5:3b-instruct, LoRA 는 HALLUC 0)

stage 1 이 raw 30건 중 **15건을 LLM 없이 오탐 처리**, 잔여 13 unique 후보를 ensemble 이 판정:

| 병렬 지표 | 값 |
| --- | --- |
| raw_hallucination_flag_items (원시, **유지**) | 30 |
| stage1_resolved_false_positive | 15 |
| normalized_hallucination_count (정규화 후 잔여) | 15 |
| **semantic_hallucination_count (valid_error)** | **0** |
| harness_false_positive_count (stage1+합의) | 27 |
| acceptable_gray_count | 3 |
| needs_policy / needsHumanReview | 0 / 0 |
| judge_confidence | 0.906 |

ensemble verdict 39건(13후보×3 lens): harness_false_positive 24 + acceptable_gray 15 + **valid_error 0**.
3 lens 합의가 강해(needsHumanReview 0) 결론이 견고하다.

**해석:** base 의 raw HALLUC 30건은 **진짜 범위밖 날조 0** — 전부 문자열 아티팩트(27)이거나
in-scope/요구사항을 느슨히 쓴 gray-zone(3). 예) `WMS(창고관리시스템) 사용법`↔allowed `WMS 운영`,
`헬프데스크 솔루션 이해`(duties 가 "도구 추천" 명시 요청), `SV 경험이 있는 경우`(요구조건 구절 누출).
⇒ reports/38 가설("base 12× 날조는 과장")을 AI judge 로 정량 확증. LoRA↔base 차이는 '날조율'이
아니라 **skill 필드 포맷 규율**(LoRA 단일 스킬 vs base 풀어쓰기)이었다.

## 4. 산출물 위치
- **메인 repo(요약·도구)**: `scripts/skill_normalizer.py`·`judge_packet_builder.py`·`semantic_skill_judge.py`·`judge_consensus.py` + 단위테스트 4종, 이 문서.
- **CareerTunerAI(상세 패킷·verdict)**: `results/2026-06-23-golden-set-002-review/` (judge_packet·chatgpt_packet·rubric·verdicts·consensus·semantic_metrics·README).

## 5. 원칙·안전 (엄수)
- semantic 지표는 **병렬**. 기존 raw 지표를 즉시 대체하지 않는다.
- AI judge = 1차 판정자, **사람 = 정책 확정자**. needsHumanReview/needs_policy 는 사람이 본다.
- 점수/판단 로직·D/F 모델·E2 validator 미변경. raw JSON/log 는 메인 repo 미커밋(out/ gitignore).
- 7B 학습/재학습/RAG 미착수.

## 6. 다음
1. 다른 AI judge 추가(ChatGPT/Gemini) — `judge_chatgpt_packet.md` 배포 → verdict 수집 → 3사 consensus 로 신뢰도 강화.
2. 사람이 acceptable_gray 허용 범위(정책) 확정.
3. 정책 확정 후에야 하니스 skill 매칭 정밀화(콤마 분할·접미 정규화·부분문자열 인정) 본체 반영 여부 결정.
4. E1 grounding(LoRA 0.217) 은 별도 관측기 소관 — 본 레이어는 HALLUCINATED_SKILL 한정.

# 4090 골든셋(60) 평가 결과 (2026-06-23, jobId `…golden-set-002`)

> 60케이스 × 3 = **180 run/모델**. dev `5279f90`(공백 정규화 fix #124 포함). raw 는 CareerTunerAI `results/2026-06-23-golden-set-002/`(push `2296074`). 여기엔 요약·해석.

## 1. 결과
| 지표 | LoRA(careertuner-c-career-strategy-3b) | base(qwen2.5:3b-instruct) |
| --- | --- | --- |
| 계약 success | **0.922** (166/180) | 0.822 (148/180) |
| json_parse_rate | 0.989 | 1.0 |
| hallucination_rate(원시) | 0.011 | 0.133 |
| **E1 grounding_violation_rate** | **0.217** | 0.067 |
| **E2 unsupported_named_entity** | **1** (`CRM465`) | 0 |
| cjk_leak_rate | 0.033 | 0.039 |
| 실패유형 | CJK_LEAK 6·MISS_MENTION 4·FORBID_MENTION 2·PARSE_FAIL 2 | HALLUC_SKILL 18·CJK 7·MISS_MENTION 4·FORBID 3 |

## 2. ★검증 — hallucination 격차는 과장(측정 아티팩트 확인)
실패 raw 를 직접 대조한 결과:
- **LoRA 의 HALLUCINATED_SKILL = 0.** LoRA hallucination 0.011(2건)은 전부 FORBIDDEN_MENTION(아래 §4). 즉 **LoRA 범위밖 스킬 날조 0**.
- **base HALLUCINATED_SKILL 18런 / bad_skills 27건 중 22건이 GRAY** — in-scope 스킬을 **긴 구절·콤마 목록·괄호 표기**로 쓴 것(`WMS 운영, 입출고 관리, 재고 정합성 관리, …`, `Figma 사용법`, `협업, 코드리뷰, 커뮤니케이션`). 명백 범위밖은 ~5건. 하니스의 skill 정확매칭이 이 느슨한 표현을 over-count.
- ⇒ **"base 가 12× 날조"는 과장.** 진짜 신호: **LoRA 는 learningTaskReasons.skill 을 깔끔한 단일 스킬로 출력, base 는 느슨하게(긴 구절/콤마)** — 포맷 규율의 차이(파인튜닝 효과의 일종)이며, 명백 범위밖 날조는 base ~5 vs LoRA 0.

## 3. E2 — CRM465 실제 날조 포착 ✓
`case-nonit-martech-crm-bait-hold-062`(CRM 도구 추천 bait)에서 LoRA 가 `CRM 시스템(CRM465, SalesForce 등)`으로 **존재하지 않는 제품코드 CRM465 를 생성** → **E2 observer high(영숫자 제품코드)가 정확히 포착**(보정 효과 실증). base 0. LoRA 의 라틴 가짜제품 날조율 ≈ 0.6%(1/180), 나오면 관측기가 잡는다. (한글 가짜명 케이스는 라틴 관측기 미포착 — 사람검토 대상, 알려진 한계.)

## 4. E1 grounding + platform leak
- **E1 grounding: LoRA 0.217 > base 0.067.** LoRA 가 부족 in-scope 역량을 strengths/fitSummary 에서 '보유'로 더 자주 서술 — **백엔드 E1 guard 가 잡는 유형**(라이브 폴백과 정합). guard 필요성 재입증.
- **platform leak(FORBIDDEN_MENTION)**: `case-it-mobile-swift-e1bait-collab-hold-202`(iOS/Swift)에서 **base 3/3런** + LoRA 1런이 Android/Kotlin/Java 누출(플랫폼 혼동). LoRA 추가 1건은 `growth-marketing`(비IT)에서 SQL 누출. base 가 더 일관되게 혼동 — 좋은 판별 케이스.

## 5. 기타
- **CJK 누출**: 양쪽 ~3~4%(`流程`·`掌握` 류 진짜). 골든36(2~3%)보다 약간↑ — 비IT 장문 케이스에서 증가. **재학습 범위 품질 backlog**.
- **PARSE_FAIL**: LoRA 2(parse_fail_prone 케이스가 의도대로 비-JSON 유발), base 0. 프로덕션은 폴백 처리.

## 6. 결론
- **파인튜닝 가치 재확인(확장셋에서 더 뚜렷)**: LoRA 계약 success↑(0.922 vs 0.822), 범위밖 날조 0, skill 필드 깔끔, E2 가짜제품은 관측기가 포착.
- **트레이드오프**: LoRA E1 grounding 위반↑(0.217) — symbolic guard 가 흡수(뉴로-심볼릭 정당).
- **정직성**: hallucination 원시격차(12×)는 skill 표현 느슨함의 over-count였음을 raw 대조로 규명 → oversell 금지.

## 7. 다음(backlog)
- **하니스 skill 매칭 정밀화 후보**(SME/레인 C 검토): 콤마 분할 + in-scope 부분문자열 인정 → gray-zone over-count 제거. 단 정책 판단이라 사람검토로.
- E2 한글 고유명사·CJK 누출 → 데이터 정제/다음 재학습 라운드.
- 레인 C(SME) 사람검토: 비IT 도메인 라벨 타당성 + gray-zone 판정 기준.

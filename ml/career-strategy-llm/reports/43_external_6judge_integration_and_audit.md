# golden60 HALLUCINATED_SKILL — 외부 6평가 통합 + 충실성 감사 (2026-06-26)

> reports/39·41 의 semantic skill judge 평가지(`judge_chatgpt_packet.md`, 13후보)를 **외부 6곳**(ChatGPT×2·
> Codex·Gemini·Claude웹·ClaudeCode)에 돌려 받은 verdict 를 합의에 통합. 내부 4 + 외부 6 = **10 verdict 세트**.
> **결론(좁고 정직하게): 130 판정(10×13) 전부 valid_error = 0.** 정규화 충실성은 독립 감사로 확증.
> 단, '독립성·벤더 수·표본·라벨 경계'에 한계가 있어 헤드라인은 아래 caveat 와 함께만 유효(적대 검증 반영).

## 1. 방법
- **수집**: `scripts/ingest_external_verdicts.py` — 외부 파일의 judge placeholder(전부 `chatgpt`)를 출처 라벨로
  재기입, candidateId 대조·스키마 검증 후 `verdicts_ext_<label>.jsonl` 생성(CareerTunerAI).
- **합의**: `judge_consensus.py` 다수결을 **외부6 단독**과 **전체10**로 각각 산출.
- **감사(다에이전트 워크플로)**: 6개 원본(.jsonl/.json + 요약 .md)을 각각 독립 에이전트가 정규화 결과와 1:1 대조
  (판정 일치·valid_error 누락·요약 산문의 날조주장·귀속) + 1개 비평 에이전트가 헤드라인 과장을 적대 검증.

## 2. 결과 — 합의 3종 비교
| 패널 | valid_error | acceptable_gray | harness_fp | needs_policy | conf |
| --- | --- | --- | --- | --- | --- |
| 내부 4 (reports/41) | **0** | 3 | 6 | 4 | 0.639 |
| 외부 6 (신규) | **0** | 7 | 4 | 2 | 0.753 |
| **전체 10** | **0** | 7 | 6 | **0** | 0.893 |

- 판정자가 늘자 reports/41·42 의 동률(needs_policy) 4건이 전부 해소 → 전체10은 gray 7 / fp 6, **needs_policy 0**.
- **판정자별 valid_error**: 10개 verdict 세트 전부 0 (전수 스캔, 130/130).

## 3. 전체 10 — 후보별 최종 라벨
| 후보 | 최종 | 합의 |
| --- | --- | --- |
| sv경험이있는경우 · SAP WMS 운영 | harness_fp | **10/10** |
| 위험물 운송 관리 | harness_fp | 9/10 |
| 무역 영어 협상 · WMS(창고관리)사용법 | harness_fp | 8/10 · 7/10 |
| WMS(창고관리)활용 | harness_fp | 6/10 |
| 헬프데스크 솔루션 이해 · LMS 솔루션 선택 | acceptable_gray | 9/10 |
| 물류 관리 및 KPI 분석 · **현장 인력 배치…** · 수요예측 기반 발주 최적화 | acceptable_gray | 8/10 |
| 사내 안전관리 시스템 운영 · 데이터 기반 수요예측 | acceptable_gray | 7/10 |

## 4. 충실성 감사 결과 (통과)
- 6/6 모두: 원본 구조화 valid_error=**0**, 정규화 ↔ 원본 **byte-identical**(judge 필드만 변경), decision 불일치 0,
  needsHumanReview/dissent 누락 0, 매핑 불가 라벨 0. `valid_error→acceptable_gray` 은닉 변경 흔적 없음.
- 요약(.md) 산문도 6/6 모두 "진짜 날조 있음"을 주장하지 않음(있는 '날조' 언급은 전부 *날조 부정* 맥락).

## 5. 정직성 caveat (적대 검증이 요구 — 헤드라인은 이와 함께만 유효)
1. **'10명 독립'은 과장.** 내부 claude 3-lens(grounding/semantic/mechanics)는 **동일 Claude 모델 3프롬프트** ensemble
   이라 독립 판정자가 아니다. 독립 *모델*은 사실상 3종(Claude·GPT/Codex·Gemini).
2. **벤더는 3개**(Anthropic·OpenAI·Google). Codex·GPT 는 모두 OpenAI 계열 → "4벤더"는 근거 없음(정정).
   추가로 **Codex 가 내부(reports/41 SSH 패널)·외부(codex-user) 양쪽에 등장**해 전체10 중 **2표가 OpenAI Codex 계열**
   (부분상관 가능). 즉 '판정자가 늘자 동률 해소'(§2)의 독립성은 Codex 중복기여만큼 약화된다 — OpenAI 가중이 사실상
   2배. valid_error=0 결론엔 영향 없으나(전 판정자 0), gray↔fp 경계의 '판정자 수' 신뢰도는 Codex 1표로 정규화해 보면
   민감도가 더 크다(헤드라인은 이 caveat 와 함께만 유효).
3. **출처는 폴더 라벨 신뢰.** 외부 6파일 모두 내부 `judge` 메타가 `chatgpt`(평가지 응답 템플릿 placeholder 를
   채운 AI 가 그대로 둠). claude-web·claude-code-ext 는 요약 제목까지 `judge: chatgpt`. 즉 "어느 AI 가 판정했나"는
   **사용자가 폴더로 부여한 라벨**에 의존하며 파일 내용만으로 교차검증되지 않는다(판정 *내용* 충실성과는 무관).
   → **개선**: 다음 라운드부터 평가지 템플릿이 judge 필드를 각 판정자 이름으로 받도록 수정.
4. **표본 한정.** golden-set-002 단일 셋의 stage1 정규화 후 잔여 13후보(물류/안전 등 non-IT 편중, 큐레이션)에
   국한. `valid_error=0`은 **이 표본 범위의 부재 증거**이지 "base 가 구조적으로 날조하지 않는다"는 일반 증명이 아니다.
5. **gray↔fp 경계는 모델민감.** 외부 패널이 내 패널보다 gray 로 더 기욺(외부6 gray7/fp4 vs 내부4 gray3/fp6).
   valid_error=0 은 견고하나, 비오류 *내부 라벨링*의 정확한 값은 사람 정책 확정 대상.
6. **reports/42 규칙 B 번복.** reports/42 가 '현장 인력 배치…'를 `harness_false_positive` 로 권고했으나,
   외부6 **6:0**·전체10 **8:2** 로 `acceptable_gray` 로 뒤집힘. "allowedSkill 에 매핑되면 fp" 규칙이 과잉기계화였음
   (다개념 결합 + '능력' 추상접미는 매핑돼도 gray 로 보는 게 합의). → reports/42 §3 항목 B 정정(아래).

## 6. reports/42 규칙 정정안
- 기존(reports/42 §2): "미매칭 토큰이 *전부* discrete allowedSkill 에 매핑 → harness_fp".
- **정정**: 단일 allowedSkill 의 접미/괄호/연결어 변형만 → harness_fp. **두 개 이상 개념을 '와/콤마/및'로 결합하거나
  '능력·역량' 등 추상 접미로 풀어쓴 다개념 구**는 각 부분이 allowedSkill 에 매핑돼도 → **acceptable_gray**
  (다모델 합의 8:2). valid_error 판정에는 영향 없음(여전히 0).

## 7. 산출물
- 메인 repo: 이 문서, reports/42 정정.
- CareerTunerAI `results/2026-06-23-golden-set-002-review/`: `verdicts_ext_{gpt-1,gpt-2,codex-user,gemini,
  claude-web,claude-code-ext}.jsonl`, `consensus_external6.jsonl`·`consensus_all10.jsonl`,
  `semantic_metrics_{external6,all10}.json`. 수집기 `scripts/ingest_external_verdicts.py`(메인 repo).
- 원본 6평가: 사용자 제공 `D:\code\AI평가지`(메인 repo 미커밋).

## 8. 다음
- 평가지 템플릿 judge 필드 자기기입화(caveat 3 개선) → 출처 자기검증 가능.
- reports/42 정정 규칙 비준 시 normalizer SPLIT_RE/SUFFIX_NOUNS 확장(측정 하니스 한정, 점수/판단/E2/D·F 불변).
- (선택) 표본 확대: golden 다른 셋/IT 직무로 valid_error=0 일반화 여부 검정.

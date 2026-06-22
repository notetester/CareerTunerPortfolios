# 면접 브리핑 Contract (QGEN 학습 입력 = 런타임 조립기 출력)

> **단일 스키마.** 백엔드 런타임 조립기(`InterviewBriefingAssembler`)와 Python 합성기(`generate_synthetic.py`)가
> **똑같은 브리핑 형식**을 만든다. 그래야 합성으로 학습한 모델이 런타임 입력을 그대로 이해한다.
>
> **핵심 원칙(RAG 논리):** 면접 회사는 무한대(외국 포함) → 모델이 회사를 외우면 안 된다.
> 회사 정보는 **입력으로 매번 주입**, 모델은 **"브리핑 → 질문" 변환 능력만** 학습한다.

---

## 1. 입력 소스 (team1_db 실측 2026-06-19)

| 브리핑 섹션 | 소스 | 채움률 | 없을 때 |
| --- | --- | --- | --- |
| 회사명 / 직무명 | `application_case.company_name` / `job_title` | 100% | 필수 |
| 공고 원문 | `job_posting.extracted_text` (없으면 `original_text`) | - | **앞 1500자 트렁케이트** |
| 회사 정보 | `company_analysis` | **35%** | 섹션 통째 생략 |
| 직무 정보 | `job_analysis` | **78%** | 섹션 통째 생략 |
| 자소서 | `user_profile.self_intro` | 3건 | 자소서 모드만, 없으면 모드 게이트 |

> ⚠️ 채움률이 들쭉날쭉(회사분석 1/3) → **"있으면 쓰고 없으면 생략"** 폴백이 전제. 항상 있다 가정하면 깨진다.

---

## 2. company_analysis → 브리핑 매핑

| DB 필드 (JSON) | 브리핑 | 정제 규칙 |
| --- | --- | --- |
| `industry` | 업종 | 1줄 |
| `company_summary` | 개요 | 200자 트렁케이트 |
| `recent_issues` | 최근 이슈 | 트렁케이트 + 분석 메타 서술 컷 |
| `interview_points` | 면접 포인트 | 200자 트렁케이트 |
| `competitors[]` | 경쟁사 | 비었으면(`[]`) 생략 |
| `verified_facts[{fact,source}]` | `[확인된 사실]` | **상위 5개**, `fact (출처: source)` |
| `ai_inferences[{basis,inference}]` | `[AI 추론]` | **상위 3개**, `inference`만 (basis 생략) |

## 3. job_analysis → 브리핑 매핑

| DB 필드 | 브리핑 | 정제 규칙 |
| --- | --- | --- |
| `required_skills[]` | 필수 스킬 | 전체 (보통 짧음) |
| `preferred_skills[]` | 우대 스킬 | **상위 8개** |
| `duties` | 주요 업무 | 300자 트렁케이트 |
| `difficulty` | 난이도 | EASY\|NORMAL\|HARD |
| `ambiguous_conditions[{condition,assumption}]` | 모호 조건 | **상위 2개**, `condition → assumption` |
| (summary / qualifications / evidence) | — | 생략 (duties·skills와 중복) |

---

## 4. 정제·압축 규칙 (조립기 = 코드, LLM 아님)

1. **선별**: facts 5 / inferences 3 / preferred_skills 8 / ambiguous 2 (앞에서 자름)
2. **정제**: `"외부 검색을 하지 않았으므로"`, `"입력 공고 기준"` 류 분석 메타 서술 정규식 컷
3. **트렁케이트**: 긴 text 필드는 N자 컷 + `…`
4. **사실/추론 라벨 유지**: `[확인된 사실]`(출처 있음) vs `[AI 추론]`(추측) 구분 — QGEN이 다르게 활용
5. **옵셔널 폴백**: 빈 배열/NULL 필드·섹션은 통째 생략

---

## 5. 브리핑 형식 (자연어 텍스트 · 예시: GC녹십자)

```
# 면접 브리핑
회사명: GC녹십자
직무명: 해외영업/마케팅
면접 모드: 직무 — 필수 스킬별 기술 질문 위주, 국비 주니어 수준
질문 수: 6

## 회사 정보
- 업종: 제약·바이오 (백신/혈액분획/희귀질환치료제)
- 개요: 생명공학·제약 기업, 백신·희귀질환·항암제·혈액분획 사업 …
- 면접 포인트: 글로벌 상업화, 품질관리(GMP), 영업망 이해
- [확인된 사실] 세계 최초 유행성출혈열 백신 개발 (출처: 채용공고)
- [확인된 사실] 세계 5위 Plasma 생산시설 (출처: 채용공고)
- [AI 추론] 연구·생산·품질·글로벌영업이 연결된 사업구조

## 직무 정보
- 필수 스킬: 해외영업, 시장조사, 계약협상, 커머셜 전략
- 주요 업무: 희귀질환치료제·항암제 해외영업, 거래처 발굴, 매출 계획 …
- 난이도: HARD

## 채용공고 (요지, 1500자 제한)
…
```

> 빈 섹션은 통째 빠진다. 회사분석 없는 케이스(65%)는 `## 회사 정보` 섹션 자체가 없다.

---

## 6. 모드별 재료 (6모드 · todo 레시피 기준)

| 모드 | 끌어올 재료 | 질문 성격 |
| --- | --- | --- |
| **기본(BASIC)** | 회사명/직무명만 | 자기소개·지원동기·장단점 |
| **직무(JOB)** | `required_skills` + `duties` + 공고 | TECH 위주, 스킬별 기술 질문 (국비 초급) |
| **인성(PERSONALITY)** | `ai_inferences`(있으면) | PERSONALITY+SITUATION, 컬처핏 1~2개 |
| **압박(PRESSURE)** | 공고 + `required_skills` | 본질문3 + 자동 반박1 (PROBE 분기) |
| **자소서(RESUME)** | `user_profile.self_intro` (★게이트) | 자소서 문장 기반 질문 |
| **기업맞춤(COMPANY)** | `company_analysis` 전체 | 회사 이해도·지원동기 |

---

## 7. 출력 형식 (학습 JSONL — `prepare_data.py` 호환)

각 줄 = OpenAI chat 포맷. `{"messages":[{system},{user},{assistant}]}`

```json
{"messages":[
  {"role":"system","content":"<QUESTION_SYSTEM_PROMPT + 모드별 활용 규칙>"},
  {"role":"user","content":"<5장 브리핑 텍스트>"},
  {"role":"assistant","content":"<JSON: [{\"question\":\"…\",\"question_type\":\"TECH\"}, …] ×6>"}
]}
```

- `question_type` ∈ `TECH` / `EXPECTED` / `PERSONALITY` / `SITUATION` (FOLLOW_UP은 PROBE task)

---

## 8. 자체 LLM 6 task (멀티태스크 · 같은 JSONL에 task 혼합)

| task | 입력(user) | 출력(assistant) | 데이터 |
| --- | --- | --- | --- |
| **QGEN** | 브리핑 | 질문 6개 `[{question,question_type}]` | 합성 |
| **MODEL_ANSWER** | 회사/직무/질문 | 모범답안 3~5문장 | 합성 |
| **EVAL** | 질문+답변(+모범답안 기준) | `{score, feedback}` | **실데이터 59건** + 합성 |
| **PROBE** | (압박) 질문+답변 | 반박 꼬리질문 | 합성 |
| **REPORT** | 전체 Q&A | 총평·항목 점수 | 합성 |
| **PLAN** | 면접 상태 | 다음 액션 JSON | 합성 |

> EVAL은 `interview_training_sample`(실사용 {질문/답변/점수/피드백})을 백엔드 export로 뽑아 합성과 합친다.
> "잘함/망함/애매함" 점수 구간을 합성에서 의도적으로 섞어 채점 모델이 구간을 학습하게 한다.

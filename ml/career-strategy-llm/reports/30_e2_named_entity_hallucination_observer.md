# E2 — 입력 밖 고유명사/제품명 날조 관측형 observer (2026-06-22)

> E1(grounding guard)과 달리 **reject/fallback 하지 않고 측정만** 한다. 평가 하니스(`eval_fit_model.py`)에 지표를 넣어 "얼마나 자주, 어떤 고유명사를 날조하는지"를 관측하고, 결과를 다음 데이터 정제/재학습 backlog로 연결한다.

## 1. 왜 reject 가 아니라 관측형인가
| | E1 (grounding guard) | **E2 (named-entity observer)** |
| --- | --- | --- |
| 대상 | 부족 역량을 "보유"로 서술 | 입력에 없는 고유명사/제품명/도구명 생성(예: `CRM465`) |
| 위험도 | **높음** — 사용자가 자기 부족 역량을 잘못 이해 → 잘못된 지원/학습 결정 | 중간 — 설명 신뢰도는 떨어지나 점수/판단(규칙엔진)·자격 판단을 직접 오도하진 않음 |
| 처리 | runtime 에서 retry→fallback(차단) | **차단 안 함. 측정·로그·리포트만** |
| 차단 시 부작용 | 적음(부족 역량 과장은 막는 게 맞음) | **큼** — 잘못 차단하면 false-positive↑, 자체모델 노출률↓(가드 누적) |

E2 를 reject 로 만들면 가드가 쌓여 폴백률이 더 오르고(E1 회귀에서 이미 확인), 자체모델이 화면에 덜 보인다. 게다가 "입력에 없는 고유명사"의 일반 판별은 false-positive 가 많다(실재하는 도구명·약어를 가짜로 오인). 따라서 **1차는 관측**으로 시작한다.

## 2. CRM465 사례
모델이 학습 제안/강점 서술에서 입력(공고·프로필)에 전혀 없던 구체 제품명을 만들어내는 경우:
```
"strengths": ["CRM465 운영 경험으로 즉시 투입 가능"]   ← 입력 어디에도 CRM465 없음
```
점수/판단은 규칙엔진이라 안전하지만, 설명이 **없는 경력을 지어내** 신뢰도를 떨어뜨린다. 이런 빈도를 수치로 본다.

## 3. 관측 지표 (하니스에 추가)
`summary` 에 다음을 추가했다(측정 전용 — **success/실패율에 영향 없음**):
```
unsupported_named_entity_count       # high(제품코드) 총 건수 — 헤드라인
unsupported_named_entity_rate        # high 가 1건 이상인 run 비율
unsupported_named_entities_by_case   # 케이스별 {high:[...], review:[...], runs_flagged:N}
```
각 run 결과에도 `named_entities: {high, review}` 가 붙는다.

### 2-tier (신뢰도 + 필드 스코프 분리) — ★1차 관측 후 보정(reports/33)
- **high — 날조된 '제품 식별자'(전 필드 스캔)**: 가짜 제품은 학습추천에 있어도 날조라 모든 필드에서 본다.
  - (a) **영숫자 제품코드**: `[A-Za-z]{2,}\d{2,}…`(CRM465/ERP900/ToolX12).
  - (b) **엔터프라이즈 약어 coinage**: 글자만으로 된 토큰이 약어 `{crm}` 으로 시작 + 글자 2+(예: `CRMONE`='crm'+'one'). **헤드라인 지표.**
- **review — 입력 밖 고유명사 '보유' 주장(보유 문맥만)**: `fitSummary`+`strengths` 만 스캔(E1 grounding 과 동일). 학습추천(`strategyActions`/`learningTaskReasons`)의 실제 도구 추천은 정상이라 제외. **낮은 신뢰도**, 사람 검토용.

## 4. false-positive 방지
관측에서 다음은 **flag 하지 않는다**:
- **일반 기술명**: `GENERIC_TECH`(Java/React/SQL/Spring/AWS/Docker/Kubernetes … + SAP/QuickBooks + 1차 관측 보정 추가: HubSpot/MLflow/PyTorch/Pandas/EC2/Controller/CRUD … + coinage 충돌 흡수용 crmnext/erpnext).
- **입력에 이미 있는 명칭**: 공고·프로필·부족역량·duties·`expected.allowedSkills/mustMention` 의 `supported` 집합.
- **범주/약어**: `CATEGORY_TERMS`(CRM/ERP/API/UI/CI/CD …) — 단독으로는 날조 아님.
- **버전 표기(양 티어 가드)**: 토큰의 숫자/기호 접미(`[0-9.+#_-]+$`)를 떼어 베이스가 일반 기술명이면 제외 — `Java21`/`Python3`/`C++17`. (★`Python3` 가 기존 review 로 새던 버그를 이 가드로 수정.)
- **coinage 약어 제한**: `{crm}` 만. `erp/ai/ml/db/api/bi/ocr` 는 실제 단일토큰 제품(ERPNext/Airflow/MLflow/DBeaver/Apigee/Bitbucket/OCRmyPDF)과 충돌해 **제외**(적대적 검증).
- **한글 일반 설명**: 라틴 토큰만 보므로 정상 한국어 설명은 0건.

단위테스트 `scripts/test_entity_observer.py`(**17 케이스**)로 CRM465/ERP900/ToolX12 + CRMONE coinage 포착, ERPNext/MLflow/Java21/Python3/C++17 오탐 미발생, review 가 학습추천이 아닌 보유 문맥만, "관측이 success 를 안 바꿈"을 검증한다.

## 5. 한계 (1차 관측이라 의도적으로 제한 — 적대적 검증으로 명시)
- **한글 날조 미포착**: 한글 회사/제품/자격명(예: "메가솔루션", 가짜 자격 "정보보안전문가1급")은 casing 신호가 없어 구조적으로 못 잡는다. high 는 라틴 식별자 중심.
- **allowlist 도구의 보유 주장은 통과**: `strengths` 에 "Salesforce 운영 경험 보유"(입력 밖)라 해도 Salesforce 가 GENERIC_TECH 라 안 잡힌다 → '입력 대조 보유 검증'은 named-entity 스캔이 아니라 별도 로직(향후).
- **제외 약어의 coinage 날조 미포착**: 가짜 `ERPxxxx`/`MLxxxx` 는 FP 안전을 위해 high(coinage)에서 빠진다(보유 문맥이면 review 로는 잡힐 수 있음). FP 안전 > 재현율 트레이드오프(수용).
- **완전한 자연어 의미검사가 아님.** 명백한 입력 밖 식별자 1차 탐지로 제한.

## 6. backend 처리 방침
- **runtime backend 에는 reject/fallback 을 걸지 않는다.** (E2 는 관측 단계)
- backend WARN-only observer 는 **검토만**: 서빙 프롬프트를 바꾸면 학습-서빙 skew 위험(현재 모델은 기존 `FIT_EXPLAIN_SYS` 로 학습, 재학습 범위 밖)이라 이번 PR 에선 구현하지 않는다. 필요해지면 "로그 WARN 만, 응답 변경 없음"으로 별도 검토.

## 7. 향후 backlog 연결
관측 결과 → 데이터/모델 개선 루프:
1. 4090 에서 골든셋으로 E2 지표 측정(자체모델 vs mock) → artifact repo 저장.
2. high/review 로 잡힌 고유명사를 모아 **학습 데이터 정제 항목**으로 등록(해당 패턴이 나온 학습 샘플 점검·수정).
3. 다음 재학습 라운드에서 "입력 밖 고유명사 금지"를 데이터·프롬프트(학습+서빙 동시)로 반영 → skew 없이 근본 개선.
4. 재측정으로 `unsupported_named_entity_rate` 감소 확인.

> 즉 E2 는 "지금 차단"이 아니라 **"측정해서 다음 학습으로 고치는"** 관측 지표다. 실행 명령은 reports/31.

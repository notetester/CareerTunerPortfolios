# E2 observer 1차 관측 결과 + 보정 (2026-06-22)

> 첫 4090 **작업 큐 자동화**(reports/32)로 E2 observer 를 돌린 결과 + 그 데이터로 티어링을 보정한 기록.
> raw 결과는 artifact repo(`CareerTunerAI` `results/2026-06-22-e2-observer-001/`), 여기엔 요약·결정.

## 1. 1차 실측 (golden 12케이스 × repeat 3 = 36 run, dev `c5f427c`)
| 지표 | 값 |
| --- | --- |
| `unsupported_named_entity_count`(high) | **0** |
| `unsupported_named_entity_rate` | 0.0 |
| review distinct | **29** |
| API/계약 | 정상(관측은 success 에 영향 없음) |

**★필드 귀속 분석(핵심): review 29/29 가 전부 `strategyActions`/`learningTaskReasons`/`risks`(학습 추천·위험 서술) 문맥, `strengths`/`fitSummary`(보유 주장)엔 0건.**
→ 모델은 **후보의 경력/보유를 날조하지 않았다.** review 대부분은:
- 실제 도구를 **"배우라"고 추천**: HubSpot·PyTorch·MLflow·Minikube·Pandas·Actix (정상)
- 표준 기술 어휘: EC2/EKS/VPC·Controller·CRUD·GET/SET/LRU·C++17·ANOVA (노이즈)

**진짜 날조 = `CRMONE` 1건** — "CRM 도구(CRMONE, HubSpot 등)"에 끼워넣은 **존재하지 않는 CRM 제품명**. digit 이 없어 high(제품코드)를 못 잡고 review 로 샜다.

## 2. 보정 (3관점 제안 → 합성 → 적대적 검증)
관측 데이터를 근거로 티어링을 보정. **적대적 검증이 실제 scanner 를 실행**해 규칙을 확정:
1. **review 를 보유 문맥(`fitSummary`+`strengths`)으로 한정** — E1 grounding 과 동일 스코프. 학습추천의 실제 도구는 정상이라 제외 → review 노이즈 29→0.
2. **high 에 coinage 규칙 추가** — 가짜 제품 식별자를 전 필드에서: 영숫자 코드(CRM465) + 약어 coinage(`CRMONE`='crm'+'one'). → CRMONE 가 high 로.
3. **coinage 약어 = `{crm}` 만** — `erp/ai/ml/db/api/bi/ocr` 는 실제 단일토큰 제품(ERPNext/Airflow/MLflow/DBeaver/Apigee/Bitbucket/OCRmyPDF)과 충돌 → 제외.
4. **양 티어 버전 가드** — 토큰의 숫자/기호 접미를 떼어 베이스가 일반 기술명이면 제외(Java21/Python3/C++17). ★`Python3` 가 기존 review 로 새던 **프로덕션 버그를 적대적 검증이 발견·수정**.
5. **allowlist 확장** — 관측에서 학습추천으로 나온 실제 도구·표준 어휘 + coinage 충돌 흡수(crmnext/erpnext).

## 3. 보정 검증
- **1차 실측 36 run 에 보정 observer 재적용 → `high=['CRMONE']`, `review=[]`** (적대적 검증 예측과 정확히 일치).
- 단위테스트 `scripts/test_entity_observer.py` **17/17 통과**: CRM465/ERP900/ToolX12 + CRMONE 포착, ERPNext/MLflow/Java21/Python3/C++17 오탐 미발생, review 가 보유 문맥만, success 불변.

## 4. 자동화 결과(첫 큐 실행)
- `jobs/open/2026-06-22-e2-observer-001` → 4090 이 `run_latest_job.ps1` 으로 실행 → `results/<jobId>/` 저장 → `jobs/done` 이동 → `STATUS_4090.md` 자동 갱신 → push(`6272928`). **복붙 0회로 동작 확인.**

## 5. v2 재측정 결과 (보정 observer, 2026-06-22, jobId `…-002`, CareerTunerAI `dd64e69`)
보정(#120)이 dev 에 반영된 뒤 의존성 게이트 통과 → 60 run(12×5) 재측정.
| 지표 | 1차(우보정 전) | **v2(보정 후)** |
| --- | --- | --- |
| `unsupported_named_entity_count`(high) | 0 | **0** |
| review distinct | 29(전부 노이즈) | **0** |
| `unsupported_named_entity_rate` | 0.0 | **0.0** |
| 계약 success | — | 59/60(`json_parse_rate` 0.983) · cjk 0 · hallucination 0 · timeout 0 |

**무결성 검증(조용히 깨진 게 아님):** 관측기는 60/60 row 에 `named_entities` 기록, 59개 응답에 라틴 토큰(스캔 대상) 존재. v2 raw 에 보정 observer 재적용 → high=[], review=[] 로 **기록값과 일치**. 즉 review 0 은 **필드 스코프가 1차 노이즈 29건을 정확히 제거**한 결과.

**해석:**
- review 노이즈(학습추천의 실제 도구·표준 어휘) 29 → 0 = 보정 성공.
- high 0: 이번 60 run 에 CRMONE 류 미출현. CRMONE 는 **확률적**(1차 36 run 1건, v2 60 run 0건) → **라이브 가짜제품 날조율 매우 낮음**(≈1~2% 희소). 나오면 high 가 잡는다(보정으로 확인).
- ⇒ **모델은 E2(고유명사 grounding)에 유의미한 문제가 없다.** 1차의 "30 review"는 측정 노이즈였음이 v2 로 확정.
- 잔여: 1/60 `PARSE_FAIL`(`case-nonit-sales-complement-001` run4) — **E2 무관한 계약 견고성** 이슈(자체모델이 드물게 비-JSON 출력). 프로덕션은 OSS 재시도+폴백으로 처리. json_parse 0.983 → 별도 backlog.
- 자동화: `…-001`(1차)·`…-002`(v2) 모두 **"작업 진행" 트리거 + 의존성 게이트**로 무복붙 실행 검증.

## 6. 다음
- E2 관측은 **완료**. 관측된 유일 가짜제품(CRMONE)은 빈도가 낮아 우선순위 낮음 — 학습 데이터 정제 backlog 에 기록만(다음 재학습 시 점검).
- 잔여 계약 견고성(`PARSE_FAIL` ~1.7%)은 E2 와 별개 backlog.
- 포트폴리오 내러티브: **"측정 → 발견(모델은 대체로 견고, 가짜 제품명 1종 희소) → 관측기 보정(적대적 검증으로 기존 Python3 버그까지 수정) → 자동화 파이프라인으로 무복붙 재측정"** — oversell 없이 측정으로 뒷받침.

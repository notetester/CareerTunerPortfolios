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

## 5. 다음
- 이 보정 PR merge 후 **v2 재측정**(`2026-06-22-e2-observer-002`, repeat↑)으로 **라이브 날조율**(high=가짜제품/run)을 본다 — CRMONE 류가 얼마나 자주 나오는지.
- 관측된 가짜 제품명은 **학습 데이터 정제 backlog**로 등록 → 다음 재학습에서 데이터·프롬프트(학습/서빙 동시)로 근본 개선(skew 회피).
- 포트폴리오 내러티브: **"측정 → 발견(모델은 대체로 견고, 가짜 제품명 1종) → 관측기 보정(적대적 검증으로 기존 버그까지 수정) → 자동화 파이프라인"** — oversell 없이 측정으로 뒷받침.

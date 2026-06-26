# C 자체모델 v2 — RAG 설계 계획 (2026-06-26)

> reports/49(7B smoke) 결론에 따라 C v2 방향을 **3B LoRA 유지 + RAG 우선**으로 확정하고, RAG를 붙이기 전 설계 범위를 명확히 한다.
> **이번 PR은 설계 문서(reports/50)만 추가한다. LangChain/RAG 코드·Vector DB·embedding·backend 변경은 포함하지 않는다.**
> 불변 원칙: 점수·applyDecision은 rule engine/server 소유(LLM은 설명만), E1 grounding guard·E2 named-entity observer 유지, 3B LoRA가 기본 모델.

## 1. 배경 — 왜 RAG 우선인가
reports/49 7B smoke(golden60, GPU, repeat 2) 결과 요약:
- **7B base가 3B LoRA를 못 이김.** success **LoRA 0.892 > 3B base 0.875 > 7B base 0.867**.
- **semantic valid_error는 3B LoRA·7B base 모두 0** — 모델 크기를 키워도 "진짜 날조"는 줄지 않는다.
- 비용: 7B latency **~3403ms** vs 3B LoRA **~2263ms**, VRAM **~4.7GB** vs 3B 계열 **~2.2GB**.
- 7B 전환·7B LoRA 재학습 **보류**.

**핵심 판단:** 남은 gap은 *모델 파라미터 수*가 아니라 **grounding/근거 제공**이다. 즉 모델이 더 똑똑해질 문제가 아니라, *정확한 입력 근거(공고·프로필·회사조사·자격증 사실)*를 모델에 충분히 주는 문제다. 이는 RAG가 직접 겨냥하는 영역이며, 동일 3B LoRA를 유지하면서 latency/VRAM 증가 없이 품질을 올릴 수 있는 경로다. (예: reports/49의 유일한 valid_error `MSSQL`은 모델 크기 문제가 아니라 "허용 스킬 catalog 근거 부재" 문제 — RAG로 해결 가능.)

## 2. RAG 적용 대상 데이터 소스
| # | 데이터명 | 소유자/스코프 | 업데이트 주기 | RAG 우선순위 | 개인정보 위험도 | PoC 포함 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 채용공고 원문 | application-private (지원 건에 귀속) | 지원 건 생성 시 | **높음** | 낮음(공개정보) | ✅ |
| 2 | 회사/직무 조사 결과(B 산출) | application-private 또는 global(회사 단위 캐시) | 분석 시 | **높음** | 낮음 | ✅(요약) |
| 3 | 사용자 프로필/이력서/자격증/경력 | **user-private** | 사용자 편집 시 | **높음** | **높음** | ✅(요약) |
| 4 | 기존 지원 건 분석 결과 | user-private(본인 지원 건) | 분석마다 | 중간 | 높음 | ❌(PoC 제외) |
| 5 | 기술·자격증 학습 자료 | **global** | 드묾 | 중간 | 낮음 | ▵(일부) |
| 6 | 면접 질문/답변 기록 | user-private/application-private | 면접 연습 시 | 낮음 | 높음 | ❌ |
| 7 | NCS/직무역량/자격증 catalog | **global** | 드묾 | **높음** | 낮음 | ✅(일부) |

분류 원칙: **global**(누구에게나 동일 근거: NCS·자격증·학습 catalog) / **user-private**(프로필·이력서) / **application-private**(특정 지원 건의 공고·조사). PoC는 *공개·저위험·고우선* 소스 중심으로 좁힌다.

## 3. PoC 범위 (지원 건 1개 기준, C 한정)
**포함:** 채용공고 원문 1건 · 사용자 프로필 요약 · 회사/직무 조사 요약 · 자격증/기술 catalog 일부(global).
**제외:** 전체 커뮤니티 데이터(F) · 면접 음성/영상 기록 · 장기 개인 히스토리 전체 · 복잡한 권한 공유 기능.
**목표:** "지원 건 1개에 대해, 공고+프로필+조사+catalog를 근거로 3B LoRA 설명이 더 grounded해지는가"를 오프라인으로 검증. 서비스 통합·다중 사용자 스케일은 PoC 이후.

## 4. 아키텍처 (제안)
```text
Spring Boot backend (기존)
  └─ RAG service layer (신규, C 전용)         ← 점수/판단 미관여(설명 근거 주입만)
       ├─ embedding/indexing pipeline          ← 오프라인/배치 + 신규 문서 시 증분
       ├─ vector store (+ metadata 필터)        ← tenant/scope 격리 필수
       ├─ retriever (top-k + scope 필터 + rerank?)
       └─ prompt context builder               ← retrievedContext[] 조립
  └─ 기존 OSS client (CareerAnalysisOssClient) → 3B LoRA(Ollama) 호출 (불변)
```
점수·applyDecision은 **rule engine/server**가 그대로 산출하고, RAG는 **learningTaskReasons/strengths/risks 등 설명 텍스트의 근거**로만 쓴다.

### 4.1 프레임워크 후보 (고정 아님 — 비교)
| 후보 | Spring 통합 난이도 | 팀 프로젝트 적합성 | 구현량 | 운영 복잡도 | 발표/포트폴리오 설명력 |
| --- | --- | --- | --- | --- | --- |
| **Spring AI** | 낮음(네이티브) | 높음(백엔드 Java 단일 스택) | 중 | 낮음 | 높음("Spring AI로 RAG") |
| **LangChain4j** | 낮음(JVM) | 높음 | 중 | 낮음 | 높음 |
| **Python LangChain microservice** | 중(별도 서비스·HTTP) | 중(임베딩 생태계 풍부하나 운영 2-스택) | 중~높 | 중~높 | 중(아키텍처 복잡) |
| **custom lightweight retriever** | 낮음 | 중(직접 구현 학습가치↑) | 낮~중 | 낮음 | 높음(원리 설명력) |
- **잠정 권장(PoC):** embedding 생태계가 Python에 강하므로 **PoC는 Python 경량 retriever(또는 LangChain) 마이크로서비스로 오프라인 검증** → 서비스 통합 단계에서 **Spring AI/LangChain4j로 백엔드 단일 스택 흡수** 검토. 최종 고정은 R2~R3에서.

## 5. Vector DB / 검색 방식 후보
| 후보 | 의미검색 | 운영 복잡도 | 기존 스택 적합 | PoC 적합 |
| --- | --- | --- | --- | --- |
| **FAISS** | ✅ | 낮음(in-process 파일) | △(Python) | ✅ PoC 최적 |
| **Chroma** | ✅ | 낮음(임베디드) | △ | ✅ |
| **pgvector** | ✅ | 중(Postgres 신규 인프라) | △(프로젝트는 MySQL) | △ |
| **Elasticsearch/OpenSearch** | ✅(+BM25 hybrid) | 높음 | △ | ❌ |
| **MySQL FULLTEXT** | ❌(키워드만) | **매우 낮음**(기존 MySQL) | ✅ | ✅(키워드 fallback/hybrid) |
- **프로젝트는 MySQL 8 + MyBatis가 이미 운영 중** → 초기 PoC는 **운영 복잡도 최소화**: 의미검색은 **FAISS/Chroma(임베디드)**로, 키워드 보조는 **MySQL FULLTEXT**로 hybrid. **새 DB 서버(pgvector/ES) 도입은 PoC 범위 밖**(R3 이후 스케일 필요 시 재검토). production 후보로 pgvector를 1순위 비교 대상으로 남긴다.

## 6. Embedding 모델 후보
| 후보 | 한국어 품질 | 로컬 실행 | 비용 | 속도 | 개인정보 | 시연 적합 |
| --- | --- | --- | --- | --- | --- | --- |
| **bge-m3** | 높음(다국어) | ✅(로컬) | 무료 | 중 | **우수(로컬)** | 높음 |
| **multilingual-e5** | 높음 | ✅ | 무료 | 중~빠름 | 우수 | 높음 |
| **OpenAI embedding API** | 높음 | ❌(외부 전송) | 유료 | 빠름 | **위험(개인정보 외부 전송)** | 중 |
| **local sentence-transformers(ko-sroberta 등)** | 중~높 | ✅ | 무료 | 빠름 | 우수 | 중 |
- **잠정 권장:** **로컬 bge-m3 또는 multilingual-e5** — 한국어 품질 + **개인정보(이력서/프로필)를 외부 API로 보내지 않음** + 4090 로컬 실행 가능. OpenAI embedding은 user-private 데이터에 **부적합**(외부 전송), global catalog에 한해서만 고려 가능.

## 7. Chunking 설계
| 문서 유형 | chunk 단위 |
| --- | --- |
| 채용공고 | 업무 / 자격(필수) / 우대 / 복지 / 회사소개 단위 |
| 이력서/프로필 | 기술 / 경력 / 프로젝트 / 자격증 단위 |
| 회사조사 | 사업 / 기술스택 / 최근이슈 / 인재상 단위 |
| 자격증 catalog | 자격증 1개 = 1 chunk |
| 학습자료 | 주제 / 개념 단위 |

각 chunk는 **metadata 필수**(스코프 격리·만료·도메인 필터의 근거):
```json
{
  "tenantId": "...",
  "userId": "...",
  "applicationId": "...",
  "sourceType": "job_posting",
  "sourceId": "...",
  "visibility": "user_private",
  "createdAt": "...",
  "expiresAt": "...",
  "domainGroup": "IT_SOFTWARE"
}
```
- `visibility`(global/user_private/application_private)와 `userId`/`applicationId`는 **retriever 필터의 1차 키**다(§8).

## 8. 개인정보 / 보안 설계 (필수·강함)
- **스코프 격리(하드 필터):** user-private/application-private chunk 검색은 **반드시 `userId`(및 `applicationId`)로 범위를 먼저 제한**한 뒤 의미검색. 스코프 필터는 retriever의 **사전 조건**이지 사후 정렬이 아니다.
- **인덱스 분리:** **global catalog 인덱스**와 **user-private 인덱스**를 물리적으로 분리(또는 강제 파티션). global 검색이 user 데이터를 절대 끌어오지 않게.
- **교차 사용자 금지:** 다른 사용자의 이력서/지원 건 chunk는 **어떤 경로로도 검색 불가**(필터 누락 시 결과 0건이 되도록 fail-closed 설계).
- **로그 제한:** retrieved context에 개인정보가 포함될 수 있으므로 **raw retrieved chunk를 평문 로그/공개 로그에 남기지 않는다.** 디버그 시 chunkId·sourceType 등 메타만 로깅.
- **repo 격리:** **raw retrieved chunk·embedding·user 데이터를 main repo나 CareerTunerAI 공개 artifact에 커밋하지 않는다.** 평가 산출물은 지표 요약만(개인정보 마스킹).
- **embedding 보존/삭제 정책:** embedding에도 개인정보가 복원 가능 형태로 들어갈 수 있으므로, **user 데이터 삭제 시 해당 embedding·chunk도 삭제**(`expiresAt` + 사용자 탈퇴/지원건 삭제 연동). 보존 기간·삭제 트리거를 정의.
- **외부 전송 금지(기본):** user-private 데이터의 embedding은 **로컬 모델로** 생성(OpenAI embedding API 등 외부 전송 금지). global catalog에 한해 외부 API 허용 검토 가능.

## 9. Prompt 조립 방식
```text
[System]
너는 CareerTuner의 커리어 전략 설명 모델이다.
점수와 applyDecision은 서버가 제공한 값을 절대 변경하지 않는다.
retrievedContext는 근거로만 사용하고, 없는 사실은 만들지 않는다.

[User]
profileSnapshot
jobPostingSummary
fitScore / applyDecision           ← 서버/rule engine 산출값(불변)
matchedSkills / missingSkills
retrievedContext[]                 ← {sourceType, sourceTitle(내부), text}
```
RAG context 규칙:
- retrieved context에 **없는 회사 사실을 만들지 않는다**(grounding).
- 출처 `sourceType`/`sourceTitle`은 **내부적으로 유지**(추적·평가용)하되, 답변엔 과도한 출처 표기보다 **근거 기반 표현**.
- **점수/판단은 여전히 rule engine/server 소유** — RAG는 설명 근거만. (E1 guard가 "부족역량 보유 서술"을 계속 차단; RAG가 이를 우회하지 못하게.)
- prompt 길이 관리: retrievedContext는 top-k 제한 + 요약 chunk 우선(§11 길이초과 완화).

## 10. 평가 계획
기존 지표 유지 + RAG 전용 지표 추가:
- 계약: contract success · json_parse_rate · CJK leak · PARSE_FAIL
- 안전: E1 grounding violation · E2 named-entity high/review · **raw/normalized/semantic hallucination**
- 성능: latency
- **RAG 전용: retrieval hit rate · retrieved context precision · 개인정보 누출 여부(스코프 격리 위반 0 검증)**

비교군:
| 군 | 구성 | 목적 |
| --- | --- | --- |
| **A** | 3B LoRA only(현행) | 베이스라인(reports/49 = success 0.892) |
| **B** | 3B LoRA + RAG | 본 가설(grounding↑, valid_error/E1↓ 기대) |
| C | 7B base only | 참조(reports/49, 이미 열세) |
- **7B + RAG는 이번 설계에서 후순위.** 동일 golden60 + RAG 확장 케이스로 A vs B 비교가 핵심.
- 성공 기준(예): B가 A 대비 **valid_error·E1 grounding violation 감소**(또는 동률 유지)하면서 **success ≥ A**, latency 허용 범위, **개인정보 누출 0**.

## 11. 위험요소와 완화책
| 위험 | 완화책 |
| --- | --- |
| 잘못 검색된 context로 오답 | top-k 작게 + 유사도 임계 + (선택) rerank, retrieval precision 평가 게이트 |
| 개인정보 leakage | §8 하드 스코프 필터(fail-closed)·인덱스 분리·로그/repo 격리 |
| 오래된 회사정보/공고정보 | `expiresAt`/`createdAt` 메타로 신선도 필터, 만료 chunk 제외 |
| retrieval latency 증가 | 임베디드 store·top-k 제한·캐시(회사 단위 global), 비동기 사전 인덱싱 |
| chunk 중복/오염 | dedup(sourceId+hash), 인덱싱 시 정규화 |
| prompt 길이 초과 | retrievedContext 요약·토큰 예산 배정·우선순위 절단 |
| RAG가 점수/판단 침범 | context는 설명 근거로만, 점수/applyDecision은 server 소유 유지, E1 guard 불변 |

## 12. 단계별 로드맵
| Phase | 내용 | 완료 기준 |
| --- | --- | --- |
| **R0** | 설계 문서(이 reports/50) | 설계·범위·보안 원칙 합의 |
| **R1** | offline retrieval PoC | 지원 건 1개로 공고/프로필/조사/catalog 인덱싱·검색 동작, scope 필터 검증(교차 사용자 0건) |
| **R2** | 3B LoRA + retrievedContext prompt 실험 | A vs B 오프라인 비교에서 grounding 지표 개선 신호 + valid_error 미증가 |
| **R3** | backend service layer 통합 | Spring(Spring AI/LangChain4j) RAG service, 점수/판단 미관여·E1/E2 불변 확인 |
| **R4** | golden60 + RAG 확장 평가 | A/B/C 정식 비교(§10 지표), 개인정보 누출 0 |
| **R5** | 제한적 서비스 적용 | 일부 지원 건/사용자 대상 플래그 적용, 모니터링(품질·latency·PII) |
- 각 phase는 **점수/판단 로직·E1 guard·E2 observer·기본 모델 불변**을 완료 조건에 포함.

## 13. 이번 PR 범위
**포함:** `ml/career-strategy-llm/reports/50_rag_design_plan.md` (이 문서)뿐.
**미포함:** LangChain/RAG 코드 · Vector DB 설치 · embedding 생성 · backend API 변경 · prompt runtime 변경 · raw data/artifact.

## 자체 검증
- ✅ #146 결과 수치 정확 반영(success 0.892/0.875/0.867, valid_error 0, latency 3403 vs 2263, VRAM 4.7 vs 2.2).
- ✅ 7B 전환 보류 + RAG 우선이 "gap=grounding(모델크기 아님)" 근거와 연결.
- ✅ RAG는 설명 근거만 — 점수/applyDecision은 rule engine/server 소유, E1 guard 불변(§9).
- ✅ 개인정보/사용자별 검색 격리(§8) — 하드 스코프 필터·인덱스 분리·fail-closed·로그/repo/embedding 정책.
- ✅ 구현 없음 — 설계 문서만 추가(§13).

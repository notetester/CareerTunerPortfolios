# CareerTuner — Backend

Spring Boot **4.1.0** / Java **21** / **MyBatis** / **MySQL 8** REST API 서버.
인증은 **JWT(Access/Refresh) + Spring Security(stateless)**, 비밀번호는 **BCrypt**.

이 문서는 백엔드의 **현재 구현·실행 상태**를 설명한다. 목표 기능 범위와 출시 우선순위는
[`../docs/planning/기획.md`](../docs/planning/기획.md), 표준 도메인 구조와 소유권은
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)와
[`../docs/FEATURE_MODULE_STRUCTURE.md`](../docs/FEATURE_MODULE_STRUCTURE.md)를 따른다.

> 영속성 계층은 **MyBatis만** 사용한다(JPA 미사용). 매퍼는 `@Mapper` 인터페이스 +
> `src/main/resources/mapper/**/*.xml` 조합으로 작성하고, `map-underscore-to-camel-case`가 켜져 있다.

## 사전 준비

- JDK 21
- MySQL 8 — 공유 개발·AWS 환경은 할당받은 **`team1_db`**를 사용하며 기존 DB에는 필요한 patch만 적용한다.
- 외부에 연결되지 않은 로컬/demo DB를 처음 만들 때만
  `src/main/resources/db/schema.sql` → `src/main/resources/db/data.sql` 순서로 실행한다(IntelliJ Database 콘솔 권장).
  `data.sql`에는 공통 비밀번호의 개발용 `SUPER_ADMIN`이 있으므로 `team1_db`와 운영 DB에는 적용하지 않는다.
  운영 관리자는 별도 one-time bootstrap 절차와 운영 비밀값으로 생성·승격한다.
- 기존 DB를 갱신할 때는 배포 절차에 명시된 `src/main/resources/db/patches/*.sql`을 적용한다. Sites 반환
  주소 연동에는 `20260711_auth_frontend_return_client.sql`이 필요하며, 기존 이메일 토큰은 `primary`로 호환된다.
  동일 사용자의 소셜 provider 중복 연결 방지는 `20260711_user_social_provider_unique.sql`을 적용한다.
- 관리자 경계 정합 보정은 애플리케이션 쓰기를 멈춘 뒤
  `20260711_admin_active_assignment_unique.sql` → `20260711_admin_soft_delete_columns.sql` →
  `20260711_admin_permission_crud_catalog.sql` 순서로 준비한다.
  개발자별 관리자 발급·회수와 quorum 검증은 [`db/maintenance/README.md`](src/main/resources/db/maintenance/README.md)를 따른다.
  안전한 비seed ACTIVE `SUPER_ADMIN` 3명 이상을 확인한 뒤 필요하면 `20260711_admin_seed_role_reconciliation.sql`을 적용한다.
  후자는 고정 id+email 검증을 통과한 공유 DB seed 5개를 모두 `USER`로 복구한다.
  검증된 seed 외 ACTIVE `SUPER_ADMIN`이 최소 3명 없으면 실패하며 seed 외 관리자 계정은 수정하지 않는다.

## 실행

```bash
.\gradlew.bat bootRun      # Windows  (macOS/Linux: ./gradlew bootRun)
```

- 서버 `http://localhost:8080` · 헬스 `GET /api/health` · Swagger `http://localhost:8080/swagger-ui.html`
- HikariCP `initialization-fail-timeout: -1` 로 **MySQL 없이도 부팅**된다(헬스 체크 가능).

### 환경 프로파일 (local / tailscale / aws / domain)

호스트 기본값 묶음(DB·Ollama·API 주소)을 이름으로 전환한다 — `application-<이름>.yaml` 이 호스트 관련 키만 오버라이드한다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun                        # 전부 내 PC (DB/Ollama localhost)
.\gradlew.bat bootRun --args='--spring.profiles.active=tailscale'     # 팀 DB + 공유 4090 Ollama
```

- 프로파일 없이 실행하면 기존 기본값 그대로다. 환경변수 override(`DB_HOST` 등)는 프로파일보다 항상 우선한다.
- 공유 4090 Ollama 가 꺼져 있으면 부팅 시 자동으로 폴백 후보(`AI_OLLAMA_FALLBACK_BASE_URLS`,
  기본 `http://localhost:11434`)로 전환된다. 끄기: `AI_OLLAMA_FALLBACK_ENABLED=false`.
- 프로파일 정의·컴포넌트별 전환 방법: [`../docs/ENVIRONMENTS.md`](../docs/ENVIRONMENTS.md), [`../config/environments.json`](../config/environments.json)

### 시연(데모) 구성

데모는 두 모드로 나뉜다. 채널별 빌드·배포 절차는 [`../docs/RELEASE.md`](../docs/RELEASE.md) 참고.

- **웹 mock 데모(백엔드 불필요)** — GitHub Pages(<https://notetester.github.io/CareerTunerDemo/>),
  `VITE_USE_MOCK=true` 빌드. mock 레지스트리에 등록된 **인증 + C 영역(홈/대시보드/취업 분석/적합도)** 만
  보이고, 미등록 엔드포인트는 "데모 미제공" 안내가 뜬다.
- **풀스택 시연(이 백엔드 실행)** — 전체 API 가 실 데이터로 동작한다. 라이브 기능 스위치(모두 env, 키 값 커밋 금지):

| 기능 | 켜는 방법 |
| --- | --- |
| 자격증 근거 라이브 조회 | `CAREERTUNER_CERT_DATA_GO_KR_SERVICE_KEY` 에 data.go.kr **디코딩(raw)** 키 주입. 미설정 시 라이브 조회 없이 안전 degrade(일정·등록 근거 미생성). 국가자격 **종류 판별(T/S)은 번들 오프라인 스냅샷**(resources/cert/, 연 단위 갱신)이 기본이고, **국가기술자격 시험일정은 통합 API**(apis.data.go.kr — 구형 q-net 과 별개 호스트, https 정상)를 번들 jmCd 매핑으로 종목별 조회한다. 구형 openapi.q-net(http 전용, 장애 잦음)은 폴백으로만 남는다 — 어느 경로든 확인 실패 시 '없음'이 아니라 **확인 불가**로 표시된다 |
| C 자체 모델(OSS) | `CAREERTUNER_ANALYSIS_AI_PROVIDER=oss` + `CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL=<OpenAI 호환 엔드포인트>`. base-url 미설정 시 OSS 비활성(기존 openai 경로 유지) |

**8080 포트 점유 시(BlueStacks 등):** 백엔드를 `--args='--server.port=8081'` 로 띄우고,
프런트 dev 프록시 대상(`frontend/.env.localhost` 의 `VITE_PROXY_TARGET`)을 `http://localhost:8081` 로 함께 바꾼다.

## 설정/시크릿 (환경변수 override)

모든 민감값은 `application.yaml` 에 `${ENV:기본값}` 형태다. **지금은 커밋된 기본값으로 즉시 동작**하고,
배포(AWS 등)에서는 동일 이름의 환경변수만 주면 코드/파일 변경 없이 교체된다.

```bash
DB_PASSWORD=... JWT_SECRET=... OAUTH_KAKAO_CLIENT_SECRET=... java -jar app.jar
```

| 그룹 | 변수 | 기본값(커밋) |
| --- | --- | --- |
| DB | `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | `localhost` / `3306` / `team1_db` / `dbuser` / `YOUR_DB_PASSWORD` |
| JWT | `JWT_SECRET` `JWT_ACCESS_TTL` `JWT_REFRESH_TTL` | dev 기본키 / `1800`(30분) / `1209600`(14일) |
| OAuth | `OAUTH_{KAKAO,NAVER,GOOGLE}_CLIENT_ID` `..._CLIENT_SECRET` `..._REDIRECT_URI` | `CHANGEME`(미발급) |
| 메일 | `MAIL_HOST` `MAIL_USERNAME` `MAIL_PASSWORD` `MAIL_FROM` `MAIL_DEV_MODE` | `smtp.naver.com` / 빈값 / 빈값 / no-reply / `true` |
| 앱 | `APP_FRONTEND_URL` `APP_SITES_FRONTEND_URL` `APP_API_BASE_URL` | `http://localhost:5173` / 빈값(AWS 프로파일은 Sites 주소) / `http://localhost:8080` |
| 업로드 | `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` `JOB_POSTING_MAX_FILE_SIZE_BYTES` | `10MB` / `12MB` / `10485760` |
| 로컬 LLM | `AI_OLLAMA_BASE_URL` `AI_OLLAMA_MODEL` `AI_OLLAMA_CONNECT_TIMEOUT` `AI_OLLAMA_READ_TIMEOUT` | `http://localhost:11434` / `gemma4` / `3s` / `30s` |
| 로컬 LLM 폴백 | `AI_OLLAMA_FALLBACK_BASE_URLS`(콤마 구분) `AI_OLLAMA_FALLBACK_ENABLED` | `http://localhost:11434` / `true` — 4090 미응답 시 부팅에서 자동 전환 |
| E 첨삭 LLM | `CAREERTUNER_CORRECTION_AI_PROVIDER` `CAREERTUNER_CORRECTION_AI_SELF_BASE_URL` `CAREERTUNER_CORRECTION_AI_SELF_MODEL` `CAREERTUNER_CORRECTION_AI_SELF_MAX_TOKENS` | `openai` / 빈값 / `careertuner-e-correction-3b:delivery-s-f16-20260708` / `3072` |
| B 기업 웹검색 | `NAVER_SEARCH_CLIENT_ID` `NAVER_SEARCH_CLIENT_SECRET` `CAREERTUNER_COMPANY_WEBSEARCH_ENABLED` `CAREERTUNER_COMPANY_WEBSEARCH_MAX_SEARCH_CALLS_PER_ANALYSIS` `CAREERTUNER_COMPANY_WEBSEARCH_MAX_RESULTS_PER_ANALYSIS` | 빈값 / 빈값 / `false` / `4` / `12` |

> 메일은 `MAIL_DEV_MODE=true`(또는 SMTP username 미설정)면 **실제 발송 대신 인증 링크를 로그로 출력**한다.
> OAuth 키는 아직 미발급이라 placeholder다 — 키 수령 후 위 env(또는 yaml 기본값)만 교체하면 동작한다.

## 인증 (Auth) API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 회원가입(+인증메일 발송) | - |
| POST | `/api/auth/login` | 로그인 | - |
| POST | `/api/auth/refresh` | 액세스 토큰 재발급(refresh 회전) | - |
| POST | `/api/auth/logout` | refresh 토큰 폐기 | Bearer |
| GET | `/api/auth/me` | 내 정보 | Bearer |
| GET | `/api/auth/check/email?value=` | 이메일 중복 확인 | - |
| GET | `/api/auth/verify-email?token=` | 이메일 인증 → 프런트로 리다이렉트 | - |
| POST | `/api/auth/email/resend?email=` | 인증메일 재발송 | - |
| GET | `/api/auth/oauth/providers` | 현재 환경에서 사용 가능한 소셜 로그인 제공자 조회 | - |
| GET | `/api/auth/oauth/{provider}` | 소셜 로그인 시작(`kakao`/`naver`/`google`) | - |
| GET | `/api/auth/oauth/{provider}/callback` | 소셜 콜백 → 프런트로 토큰 전달 | - |

- **JWT**: 액세스 토큰은 `Authorization: Bearer <token>`. 리프레시 토큰은 불투명 UUID로 `refresh_token` 테이블에서 회전/폐기 관리.
- **소셜 로그인 흐름**: 프런트가 `/api/auth/oauth/{provider}` 로 전체 페이지 이동 → 제공자 인증 →
  백엔드 콜백에서 사용자 조회/생성 후 JWT 발급 → 프런트 `/auth/callback#accessToken=…&refreshToken=…` 로 리다이렉트.
  반환 대상은 요청 URL이 아니라 서명된 state의 `primary`/`sites` named client로만 선택한다. OAuth 공급자에는
  기존 AWS 백엔드 콜백 하나만 등록하며, 이메일 인증 토큰도 같은 client를 DB에 보존한다.
- Sites 요청은 결제 준비를 서버에서 거부한다. 결제·구독·환불 mutation은 운영 AWS 프런트에서만 수행한다.

## 결제·구독 사용권 API

구독 상품과 사용권 정책은 DB 기준으로 내려가며, 다른 파트의 AI 기능은 `AiBenefitUsageService.consumeByFeature(...)`를 호출해
기능 코드(`feature_type`)에 매핑된 사용권을 성공 후 1장 차감한다.

사용량 기반 크레딧 기능은 `ai_feature_benefit_policy`의 `min_credit_cost`, `max_credit_cost`,
`credit_unit_tokens`로 실행 전 최소·최대 금액을 고지하고, 성공 후 실제 `token_usage`를 범위 안에서 정산한다.
기존 DB에는 `db/patches/20260706e_ai_usage_credit_range.sql`과
`db/patches/20260706f_ai_ticket_credit_fallback.sql`을 순서대로 적용한다.

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/billing/plans` | 활성 구독 플랜과 월별 사용권 정책 조회 | - |
| GET | `/api/billing/feature-benefit-policies` | AI 기능 코드와 사용권 코드 매핑 조회 | - |
| GET | `/api/billing/benefits/me` | 내 현재 구독 기간 사용권 잔여량 조회. 잔여량이 없으면 현재 플랜 기준으로 자동 발급 | Bearer |
| GET | `/api/billing/benefit-transactions/me?limit=50` | 내 사용권 지급·차감 원장 조회 | Bearer |
| GET | `/api/billing/refund-policy/current` | 현재 시행 환불정책과 사용자 고지 상태 조회 | Bearer |
| POST | `/api/billing/refund-policy/acknowledgements` | 정책 버전별 결제·크레딧·사용권 고지 확인 기록 | Bearer |
| POST | `/api/billing/charge-preview` | AI 기능 실행 전 사용권/크레딧 예상 차감량과 적용 환불정책 조회 | Bearer |
| GET | `/api/billing/refunds` | 내 환불 신청과 처리 결과 조회 | Bearer |
| POST | `/api/billing/refunds/preview` | 결제 당시 정책·현재 게시 정책과 사용 이력으로 환불 가능 여부 사전 판정 | Bearer |
| POST | `/api/billing/refunds` | 결제 건의 사용 이력을 자동 판정하고 전액 환불 검토 신청 | Bearer |
| GET | `/api/admin/refund-policies` | 환불정책 초안·게시 버전 조회 | Bearer(ADMIN) |
| PUT | `/api/admin/refund-policies/draft` | 환불정책 초안 생성 또는 수정 | Bearer(ADMIN) |
| GET | `/api/admin/refunds` | 환불 요청과 자동 판정 근거 조회 | Bearer(ADMIN) |
| POST | `/api/admin/refunds/{id}/approve` | 가결제 건 전액 환불 승인 | Bearer(ADMIN) |
| POST | `/api/admin/refunds/{id}/reject` | 환불 불가 처리 | Bearer(ADMIN) |
| POST | `/api/admin/refund-policies/{id}/publish` | 정책 게시, 환불정책 공지 자동 생성·고정 | Bearer(ADMIN) |

현재 구독제 사용권 정책은 `subscription_plan`, `subscription_benefit_policy`,
`user_subscription`, `user_benefit_balance`, `ai_feature_benefit_policy`, `benefit_transaction` 테이블로 관리한다.
환불 고지 기준은 `refund_policy`의 게시 버전으로 관리하고, 사용자별 고지 시점은
`refund_policy_acknowledgement`에 기록한다. 결제 대기 건에는 결제 당시 환불정책이
`payment.policy_snapshot_json`에 함께 저장된다. AI 기능은 차감 미리보기에서 발급한
`actionKey`로 건별 고지를 기록하고, 실제 `AiChargeService` 차감 시 동일 키를 재검증한다.
환불 신청은 `refund_request`에 결제 당시 정책과 결제 이후 크레딧·사용권 사용 여부를 판정 근거로 남긴다.
가결제 단계에서는 관리자가 전액 환불 또는 환불 불가만 결정하며 실제 PG 취소와 부분 환불은 수행하지 않는다.
PRO 플랜은 영상분석권을 월 1장 제공하고, PREMIUM 플랜은 영상분석권과 아바타면접권을 각각 월 5장 제공한다.
실제 결제 승인과 구독 갱신 자동화는 아직 연결 전이며, 구독 기간이 없으면 기존 `users.plan` 값을 기준으로 해당 월의 사용권을 발급한다.

## 지원 건 (Application Case) API

핵심 단위인 지원 건 API는 인증된 사용자 자신의 데이터만 다룬다.

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/application-cases` | 지원 건 생성 | Bearer |
| GET | `/api/application-cases?view=ACTIVE\|ARCHIVED\|DELETED` | 내 지원 건 목록. `view` 생략 시 기존 `includeArchived` 호환 동작 유지 | Bearer |
| GET | `/api/application-cases/{id}` | 지원 건 상세 | Bearer |
| PATCH | `/api/application-cases/{id}` | 지원 건 수정 | Bearer |
| DELETE | `/api/application-cases/{id}` | 지원 건 소프트 삭제(`deleted_at` 기록) | Bearer |
| PATCH | `/api/application-cases/{id}/restore` | 삭제된 지원 건 복원(`deleted_at`, `archived_at` 초기화) | Bearer |
| POST | `/api/application-cases/{id}/job-posting` | 텍스트/URL 공고문 저장 및 revision 추가 | Bearer |
| POST | `/api/application-cases/{id}/job-posting/upload` | PDF/이미지 업로드 및 텍스트 추출 | Bearer |
| GET | `/api/application-cases/{id}/job-posting` | 현재 공고문 조회 | Bearer |
| GET | `/api/application-cases/{id}/job-posting/revisions` | 공고문 revision 이력 조회 | Bearer |
| GET | `/api/application-cases/extractions/active` | 진행 중인 내 공고문 추출 작업 조회 | Bearer |
| GET | `/api/application-cases/job-posting/extractions/latest?applicationCaseIds=...` | 지원 건별 최신 공고문 추출 상태 일괄 조회 | Bearer |
| GET | `/api/application-cases/{id}/job-posting/extraction` | 현재 지원 건의 최신 공고문 추출 상태 조회 | Bearer |
| POST | `/api/application-cases/{id}/job-posting/extraction/retry` | 실패한 공고문 추출 재시도 | Bearer |
| PATCH | `/api/application-cases/{id}/job-posting/extraction/review` | `REVIEW_REQUIRED` 추출 텍스트 검수 확정 후 자동 분석 재개 | Bearer |
| PATCH | `/api/application-cases/{id}/job-posting/extraction/confirm` | 사용자가 수정한 공고문 텍스트 확정 후 OCR 없이 자동 분석 갱신 | Bearer |
| POST | `/api/application-cases/{id}/job-analysis` | 자체 공고 분석 생성(`self-rules-v1`, 선택적 로컬 LLM) | Bearer |
| GET | `/api/application-cases/{id}/job-analysis` | 공고 분석 조회 | Bearer |
| GET | `/api/application-cases/{id}/job-analysis/history` | 공고 분석 이력 조회 | Bearer |
| PATCH | `/api/application-cases/{id}/job-analysis/{analysisId}/review` | 공고 분석 사용자 검수·확정 | Bearer |
| POST | `/api/application-cases/{id}/company-analysis` | 자체 기업 분석 생성(`self-rules-v1`, 선택적 로컬 LLM) | Bearer |
| GET | `/api/application-cases/{id}/company-analysis` | 기업 분석 조회 | Bearer |
| GET | `/api/application-cases/{id}/company-analysis/history` | 기업 분석 이력 조회 | Bearer |
| PATCH | `/api/application-cases/{id}/company-analysis/{analysisId}/review` | 기업 분석 사용자 검수·확정 | Bearer |
| GET | `/api/application-cases/{id}/analysis` | 호환용 공고/적합도 분석 조회 | Bearer |
| GET | `/api/application-cases/{id}/ai-usage/b/failures` | 현재 지원 건의 B 분석 실패 로그 조회 | Bearer |
| GET | `/api/admin/application-cases` | 관리자 지원 건 목록 조회 | Bearer(ADMIN) |
| GET | `/api/admin/application-cases/{id}` | 관리자 지원 건 상세와 B 이력 조회 | Bearer(ADMIN) |
| PATCH | `/api/admin/application-cases/{id}/status` | 관리자 지원 건 상태 변경과 처리 메모 기록 | Bearer(ADMIN) |
| GET | `/api/admin/job-analysis` | 관리자 공고 분석 조회 | Bearer(ADMIN) |
| PATCH | `/api/admin/job-analysis/{analysisId}/memo` | 관리자 공고 분석 운영 메모 수정 | Bearer(ADMIN) |
| GET | `/api/admin/company-analysis` | 관리자 기업 분석 조회 | Bearer(ADMIN) |
| PATCH | `/api/admin/company-analysis/{analysisId}/memo` | 관리자 기업 분석 운영 메모 수정 | Bearer(ADMIN) |
| PATCH | `/api/admin/company-analysis/{analysisId}/metadata` | 관리자 기업 분석 출처 메타데이터 수정. 날짜 clear 플래그로 `checked_at`, `refresh_recommended_at` 초기화 가능 | Bearer(ADMIN) |
| GET | `/api/admin/ai-usage/b` | 관리자 B AI 사용량 로그 조회 | Bearer(ADMIN) |

공고/기업 분석은 기본적으로 자체 파인튜닝 모델 `careertuner-b-jobposting-r1`(Ollama)을 사용한다(`B_ANALYSIS_LOCAL_LLM_ENABLED` 기본 `true`). 스키마·그라운딩 검증을 통과하지 못하거나 모델 호출이 실패하면 1회 재시도 후 `self-rules-v1` 규칙 경로로 폴백한다. Ollama 미서빙 환경에서는 `B_ANALYSIS_LOCAL_LLM_ENABLED=false`로 끄면 곧장 `self-rules-v1`을 사용한다. 모델·주소·타임아웃은 `B_ANALYSIS_OLLAMA_MODEL`, `B_ANALYSIS_OLLAMA_BASE_URL`, `B_ANALYSIS_OLLAMA_READ_TIMEOUT`(기본 480s)로 조정한다. 컨텍스트와 출력 예산은 `B_ANALYSIS_OLLAMA_NUM_CTX`(기본 8192), `B_ANALYSIS_OLLAMA_NUM_PREDICT`(기본 2048)로 조정하며, 서비스는 이 예산에 맞춰 긴 공고 원문을 보수적으로 절단해 컨텍스트 초과 폴백을 줄인다.
텍스트 PDF는 PDFBox로 먼저 추출하고, 텍스트가 없는 PDF와 이미지는 자체 문서 추출 워커 또는 명시적으로 allowlist 된 OCR fallback만 사용한다. OpenAI 폴백은 `OPENAI_API_KEY`가 있을 때만 동작하며 모델은 `OPENAI_MODEL`(기본 `gpt-5`)로 바꾼다.
자체 LLM은 B 공고/기업 분석(`careertuner-b-jobposting-r1`), E 첨삭(`careertuner-e-correction-3b:delivery-s-f16-20260708` → Anthropic → OpenAI), F 커뮤니티 검열의 Ollama 연동과 D 면접 파인튜닝 실험을 중심으로 붙어 있으며,
A~F 담당별 목표 운영 기준은 [`../docs/planning/담당별_자체LLM_운영안.md`](../docs/planning/담당별_자체LLM_운영안.md)를 따른다.
공통 `ai/common`, 도메인별 `A_AI_*`~`F_AI_*` 설정, 관리자 AI 상태 API는 목표 구조이므로 실제 도입 시 공통 영역 합의 후 구현한다.
공고문 파일 업로드는 기본 10MB까지 허용하며, 초과 시 `INVALID_INPUT` 응답으로 안내한다.
현재 구현은 지원 건 보관/삭제를 `archived_at`, `deleted_at`으로 분리한다. 사용자 목록 API는 `view=ACTIVE|ARCHIVED|DELETED`를 지원하며,
기존 `includeArchived=true`는 `view`가 없을 때 활성+보관 목록을 반환하는 호환 동작으로 유지한다. 복원 API는 삭제 상태와 보관 상태를 함께 비워 활성 목록으로 되돌린다.
공고문 수정은 같은 공고의
revision으로 저장한다. `job_posting`은 `(application_case_id, revision)` unique key로 중복 revision을 막고,
저장 충돌이 발생하면 revision을 다시 계산해 최대 3회 재시도한다. 기업 분석 출처 메타데이터는 관리자 API에서만
수정하며, `sourceType`은 필수이고 날짜 필드는 `clearCheckedAt`, `clearRefreshRecommendedAt` 플래그로 `NULL` 저장할 수 있다.
사용자 검수 API는 기업 분석 본문과 구조화 JSON만 수정한다. 제품 정책은 `../docs/planning/기획.md`, 데이터/API 목표 구조는
`../docs/ARCHITECTURE.md`와 `../docs/FEATURE_MODULE_STRUCTURE.md`를 따른다.

## E 첨삭 API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/corrections` | 자기소개서·면접 답변·이력서·포트폴리오 설명 첨삭 생성 | Bearer |
| GET | `/api/corrections` | 내 첨삭 이력 조회 | Bearer |
| GET | `/api/corrections/{id}` | 내 첨삭 결과 상세 조회 | Bearer |
| GET | `/api/admin/corrections` | 관리자 첨삭 성공 이력 검색·페이지 조회 | Bearer(ADMIN) |
| GET | `/api/admin/corrections/summary` | 관리자 첨삭 성공·실패·메모 현황 집계 | Bearer(ADMIN) |
| GET | `/api/admin/corrections/ai-failures` | 첨삭 AI 실패 로그 조회 | Bearer(ADMIN) |
| GET | `/api/admin/corrections/{id}` | 첨삭 원문·결과·AI 사용량 상세 조회 | Bearer(ADMIN) |
| PUT | `/api/admin/corrections/{id}/memo` | 첨삭 운영 메모 저장·삭제 | Bearer(ADMIN) |
| GET | `/api/admin/credits` | 크레딧 변동 원장 검색·페이지 조회 | Bearer(ADMIN) |
| GET | `/api/admin/credits/summary` | 크레딧 지급·차감·잔액 현황 집계 | Bearer(ADMIN) |
| POST | `/api/admin/credits/adjust` | 관리자 크레딧 증감과 원장·감사 로그 기록(`requestId` 재시도 멱등 처리) | Bearer(ADMIN) |

E 자체 모델은 버전이 고정된 3B F16 모델(`careertuner-e-correction-3b:delivery-s-f16-20260708`)과 Ollama의 JSON object 응답 모드를 사용한다. 서버가 JSON 키, 원문 분량, 문단 보존, `changes` 3개 이상 계약을 검증하며, 첫 응답이 계약을 어기면 같은 3B에 실패 사유와 이전 출력을 전달해 한 번 repair한다. 구조화 출력 편차를 줄이기 위해 자체 모델의 기본 temperature는 `0.0`이다. 첨삭 화면 진입 또는 AutoPrep에서 WRITE 사용이 예견되면 이 3B 모델만 비동기로 워밍하며, 워밍은 크레딧·사용권·AI 사용 로그를 차감하지 않는다. 자체 모델 실패 또는 시간 예산 소진 시 Anthropic을 호출하고, Anthropic도 실패하거나 미설정이면 OpenAI로 전환한다. 운영 연결 시 `CAREERTUNER_CORRECTION_AI_PROVIDER=self`, `CAREERTUNER_CORRECTION_AI_SELF_BASE_URL=http://localhost:11434/v1`을 설정한다. Anthropic에는 `ANTHROPIC_API_KEY`, OpenAI 최종 폴백에는 `OPENAI_API_KEY`가 필요하다.

기존 DB에는 `db/patches/20260705_e_correction_admin_memo.sql`을 먼저 적용해야 첨삭 운영 메모 API를 사용할 수 있다. 첨삭 실패는 성공 결과 테이블이 아니라 `ai_usage_log`에 기록되므로 관리자 화면도 성공 이력과 실패 로그를 별도 데이터 소스로 조회한다.
관리자 크레딧 조정 배포 전에는 `db/patches/20260711_e_admin_credit_idempotency.sql`을 적용해야 한다. 이 패치는 `request_key`와 재시도 방지 unique index를 추가하고 과거 DB의 `feature_type` 길이를 현재 스키마와 맞춘다.

## C 분석·대시보드 API

홈/대시보드/취업 분석/적합도 분석(C 담당) API. 모두 인증된 사용자 자신의 데이터만 다루며,
A 프로필·B 공고/지원 건·D 면접·E 첨삭 원본은 읽기 전용으로만 참조한다.

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/home/summary` | 로그인 홈 요약(포커스·다음 액션·온보딩 진행률) | Bearer |
| GET | `/api/dashboard/summary` | 대시보드 요약(준비도 게이지·상태별 건수·최근 변화·유망 지원 건·AI 요약 이력) | Bearer |
| POST | `/api/dashboard/summary/refresh` | 대시보드 AI 요약 재생성(크레딧 차감) | Bearer |
| GET | `/api/dashboard/todos` | 오늘의 할 일 목록(파생+사용자 추가) | Bearer |
| POST | `/api/dashboard/todos` | 사용자 할 일 추가 | Bearer |
| PATCH | `/api/dashboard/todos/derived` | 파생 할 일 완료 오버라이드 | Bearer |
| PATCH | `/api/dashboard/todos/{todoId}` | 사용자 할 일 완료 토글 | Bearer |
| DELETE | `/api/dashboard/todos/{todoId}` | 사용자 할 일 삭제 | Bearer |
| GET | `/api/analysis/summary` | 장기 취업 분석 요약(반복 강·약점, 직무/기업 유형/기술스택별 적합도, 지원 분류·우선순위, 리스크, 3줄 요약) | Bearer |
| POST | `/api/analysis/summary/refresh` | 장기 경향 AI 요약 재생성(크레딧 차감) | Bearer |
| GET | `/api/analysis/history` | 장기 분석 AI 실행 이력 | Bearer |
| GET | `/api/analysis/plan` | 커리어 목표 + 학습 계획 조회 | Bearer |
| PUT | `/api/analysis/plan/goal` | 분석용 커리어 목표 설정(목표 직무·기간·우선 역량·선호 기업 유형) | Bearer |
| POST | `/api/analysis/plan/learning-plans` | 학습 계획 생성 | Bearer |
| POST | `/api/analysis/plan/learning-plans/{planId}/tasks` | 학습 계획 과제 추가 | Bearer |
| PATCH | `/api/analysis/plan/learning-plans/{planId}/tasks/{taskId}` | 학습 과제 완료 토글 | Bearer |
| GET | `/api/fit-analyses` | 지원 건별 최신 적합도 분석 목록 | Bearer |
| GET | `/api/fit-analyses/application-cases/{id}` | 지원 건의 최신 적합도 분석(비교 매트릭스·점수 산정 상세·신뢰도·지원 판단·액션 보드·톤별 전략 포함) | Bearer |
| POST | `/api/fit-analyses/application-cases/{id}` | 적합도 분석 생성/재생성(C 담당 AI, 크레딧 차감) | Bearer |
| GET | `/api/fit-analyses/application-cases/{id}/history` | 재분석 히스토리(점수 변화·매칭/부족 역량 변화) | Bearer |
| PATCH | `/api/fit-analyses/{fitAnalysisId}/learning-tasks/{taskId}` | 학습 로드맵 체크리스트 완료 토글 | Bearer |
| GET | `/api/admin/home/summary` | 관리자 홈 처리 필요 작업(실패·강등 노출·재분석 요청 등) | Bearer(ADMIN) |
| GET | `/api/admin/dashboard/overview` | 관리자 운영 종합 현황 | Bearer(ADMIN) |
| GET | `/api/admin/analytics/summary` | 분석 통계(점수 분포·반복 부족 역량·프롬프트 버전별 성능 등) | Bearer(ADMIN) |
| GET | `/api/admin/analytics/failures` | 분석 실패 큐(FAILED/FALLBACK 결과 통합) | Bearer(ADMIN) |
| GET | `/api/admin/analytics/quality-flags` | 분석 품질 검수 큐 | Bearer(ADMIN) |
| PATCH | `/api/admin/analytics/quality-flags/{fitAnalysisId}/{flagType}/resolve` | 품질 플래그 해결 처리 | Bearer(ADMIN) |
| GET | `/api/admin/analytics/runs?userId=` | 장기/대시보드 AI 실행 이력 | Bearer(ADMIN) |
| GET | `/api/admin/analytics/users/{userId}/timeline` | 사용자별 분석 이력 타임라인 | Bearer(ADMIN) |
| GET/POST | `/api/admin/analytics/runs/{runId}/memos` | 실행 이력 운영 메모 조회/작성 | Bearer(ADMIN) |
| PATCH/DELETE | `/api/admin/analytics/runs/{runId}/memos/{memoId}` | 실행 이력 운영 메모 수정/삭제 | Bearer(ADMIN) |
| GET | `/api/admin/fit-analyses` | 관리자 적합도 분석 목록(재분석 요청 플래그 포함) | Bearer(ADMIN) |
| GET | `/api/admin/fit-analyses/{id}` | 관리자 적합도 분석 상세(스냅샷·매트릭스·판단 JSON 포함) | Bearer(ADMIN) |
| GET/POST | `/api/admin/fit-analyses/{id}/memos` | 적합도 운영 메모 조회/작성 | Bearer(ADMIN) |
| PATCH/DELETE | `/api/admin/fit-analyses/{id}/memos/{memoId}` | 적합도 운영 메모 수정/삭제 | Bearer(ADMIN) |
| GET | `/api/admin/prompts/fit-analysis` | 적합도 분석 프롬프트 운영 조회 | Bearer(ADMIN) |
| GET | `/api/admin/prompts/analytics` | 장기/대시보드 분석 프롬프트 운영 조회 | Bearer(ADMIN) |

C 분석 AI는 `OPENAI_API_KEY`가 없으면 결정적 mock으로 동작하고, 키 주입 시 동일 엔드포인트로 실제 구조화 분석이 실행된다.
비용이 드는 AI 요약은 `career_analysis_run`의 입력 fingerprint로 캐시하며, 명시적 재생성 시에만 크레딧을 차감한다.
C 소유 테이블: `fit_analysis`, `fit_analysis_learning_task`, `fit_analysis_history`, `fit_analysis_condition_match`,
`career_analysis_run`, `career_goal`, `learning_plan`, `learning_plan_task`, `dashboard_insight`, `dashboard_todo`,
`analysis_quality_flag`, `admin_fit_analysis_memo`, `admin_career_run_memo`.

## JSON 컬럼 매핑 방침

MySQL `JSON` 컬럼은 초기에는 Java `String`으로 매핑한다. 예를 들어 `required_skills`, `matched_skills`,
`recommended_study`는 `["React","AWS"]` 형태의 JSON 문자열로 API에 내려간다.

이 방식은 MyBatis `TypeHandler` 없이 MVP를 빠르게 붙이기 위한 결정이다. 프런트/백엔드에서 구조화된 조작이
많아지는 시점에 Jackson 기반 `JsonTypeHandler`를 추가하고 `List<String>` 또는 전용 DTO로 전환한다.

### 개발/demo 시드 계정 (공통 비밀번호 `Career1234!`)

> 아래 계정은 로컬·팀 개발 DB 전용이다. 알려진 비밀번호의 `SUPER_ADMIN`이 포함되므로
> 운영 DB에 `data.sql`을 적용하거나 운영 bootstrap 계정으로 재사용하지 않는다.

| 이메일 | 비고 |
| --- | --- |
| `admin@careertuner.dev` | SUPER_ADMIN — 개발/demo bootstrap 전용 |
| `jiwon.kim@careertuner.dev` · `seoyeon.lee@careertuner.dev` | USER, 이메일 인증됨 |
| `pending@careertuner.dev` | 이메일 미인증 상태 |
| `minsu.park@careertuner.dev` | **소셜(카카오) 전용** — 비밀번호 로그인 불가 |

공개 AWS와 함께 쓰는 공유 DB의 seed role이 드리프트했다면, 관리자 role/status를 매 요청 재검증하는 백엔드가 배포된 뒤
`20260711_admin_seed_role_reconciliation.sql`을 적용한다. 패치가 반환하는 5행이 모두
`USER`이고 활성 권한·그룹이 모두 0인지 확인한다. 로컬/demo의 `data.sql`은 이와 달리
독립 시연을 위해 admin을 `SUPER_ADMIN`으로 최초 생성하며 공유·운영 DB에는 적용하지 않는다. 역할 보정 패치는 활성 refresh token도
최초 1회 회수하므로 적용 후 다시 로그인해야 한다.

공유·운영용 `SUPER_ADMIN`은 알려진 seed가 아니라 강한 개별 비밀번호의 A~F 전용 계정을 생성하거나
이메일 인증을 마친 기존 개인 개발자 계정에 발급한다. 발급·회수는
[`db/maintenance/README.md`](src/main/resources/db/maintenance/README.md)의 멱등 SQL을 사용하고,
안전한 ACTIVE `SUPER_ADMIN`을 항상 3명 이상 유지한다. USER/SECURITY/BILLING/CONTENT/AI/POLICY/ADMIN_PERMISSION 각각
READ/CREATE/UPDATE/DELETE와 `AUDIT_READ`를 정본 코드로 사용한다. 일반 ADMIN 템플릿은 담당 영역의
READ/CREATE/UPDATE만 기본 포함한다. DELETE는 기본 템플릿에서 제외하지만 슈퍼 관리자가 일반 ADMIN에게
개별 부여할 수 있다. 정책·관리자 권한 CRUD 템플릿은 슈퍼 관리자 범위다.

관리자 삭제 대상은 `20260711_admin_soft_delete_columns.sql`의 `deleted_at`으로 보존한다. 공지, FAQ,
커뮤니티 가이드, 레벨 정책, 법적 문서·조항, 분석 메모, 챗봇 대화, 광고, 면접 지식,
협업 ban, 권한 그룹 항목을 물리 삭제하지 않는다. 법적 문서와 조항은 감사·법적 이력을 유지하고,
조항 FK도 `ON DELETE RESTRICT`로 보강한다. 협업 ban과 권한 그룹 항목은 재등록 시 기존 UNIQUE 행의
`deleted_at`을 `NULL`로 복원한다.

## 패키지 구조

```text
com.careertuner
 ├─ CareerTunerApplication
 ├─ common
 │   ├─ config     SecurityConfig(JWT/stateless), OpenApiConfig, CareerTunerProperties
 │   ├─ security   JwtTokenProvider, JwtAuthenticationFilter, AuthUser
 │   ├─ web        ApiResponse, HealthController
 │   └─ exception  ErrorCode, BusinessException, GlobalExceptionHandler
 ├─ user           domain(User), mapper(UserMapper)
 ├─ auth           controller, service(Auth/Email/SocialOAuth), domain, dto, mapper
 └─ <그 외 도메인>  기능 구현 또는 package-info 스텁
```

전체 표준 도메인 목록과 담당 경로는
[`../docs/FEATURE_MODULE_STRUCTURE.md`](../docs/FEATURE_MODULE_STRUCTURE.md)를 기준으로 한다.

## 응답 규약

모든 API는 `ApiResponse<T>`로 감싼다.

```json
{ "success": true,  "code": "OK",           "data": { } }
{ "success": false, "code": "UNAUTHORIZED",  "message": "이메일 또는 비밀번호가 올바르지 않습니다." }
```

REST 경로는 프런트 프록시 규약에 맞춰 모두 `/api/**` 하위.

## Jackson 사용 규칙

Spring Boot 4의 기본 JSON 매퍼는 Jackson 3이다. 애플리케이션 코드에서는 `tools.jackson.*` 타입을 사용하고,
Spring이 구성한 `tools.jackson.databind.ObjectMapper` Bean을 생성자 주입받는다. 서비스에서
`new ObjectMapper()`를 직접 호출하거나 Jackson 2의 `com.fasterxml.jackson.core.*`,
`com.fasterxml.jackson.databind.*` 타입을 사용하지 않는다.

Jackson 3도 어노테이션 호환성을 위해 `com.fasterxml.jackson.annotation.*` 패키지를 사용하므로,
`JsonInclude` 같은 어노테이션 import는 예외적으로 그대로 둔다.

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
- MySQL 8 — 개발은 할당받은 **`team1_db`** 사용. 스키마/시드 적용(최초 1회):
  `src/main/resources/db/schema.sql` → `src/main/resources/db/data.sql` 순서로 실행(IntelliJ Database 콘솔 권장).

## 실행

```bash
.\gradlew.bat bootRun      # Windows  (macOS/Linux: ./gradlew bootRun)
```

- 서버 `http://localhost:8080` · 헬스 `GET /api/health` · Swagger `http://localhost:8080/swagger-ui.html`
- HikariCP `initialization-fail-timeout: -1` 로 **MySQL 없이도 부팅**된다(헬스 체크 가능).

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
| 앱 | `APP_FRONTEND_URL` `APP_API_BASE_URL` | `http://localhost:5173` / `http://localhost:8080` |
| 업로드 | `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` `JOB_POSTING_MAX_FILE_SIZE_BYTES` | `10MB` / `12MB` / `10485760` |
| 로컬 LLM | `AI_OLLAMA_BASE_URL` `AI_OLLAMA_MODEL` `AI_OLLAMA_CONNECT_TIMEOUT` `AI_OLLAMA_READ_TIMEOUT` | `http://localhost:11434` / `gemma4` / `3s` / `30s` |

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
| GET | `/api/auth/oauth/{provider}` | 소셜 로그인 시작(`kakao`/`naver`/`google`) | - |
| GET | `/api/auth/oauth/{provider}/callback` | 소셜 콜백 → 프런트로 토큰 전달 | - |

- **JWT**: 액세스 토큰은 `Authorization: Bearer <token>`. 리프레시 토큰은 불투명 UUID로 `refresh_token` 테이블에서 회전/폐기 관리.
- **소셜 로그인 흐름**: 프런트가 `/api/auth/oauth/{provider}` 로 전체 페이지 이동 → 제공자 인증 →
  백엔드 콜백에서 사용자 조회/생성 후 JWT 발급 → 프런트 `/auth/callback#accessToken=…&refreshToken=…` 로 리다이렉트.
  (서명된 state 토큰으로 CSRF 방지, 세션/쿠키 불필요)

## 결제·구독 사용권 API

구독 상품과 사용권 정책은 DB 기준으로 내려가며, 다른 파트의 AI 기능은 `AiBenefitUsageService.consumeByFeature(...)`를 호출해
기능 코드(`feature_type`)에 매핑된 사용권을 성공 후 1장 차감한다.

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/billing/plans` | 활성 구독 플랜과 월별 사용권 정책 조회 | - |
| GET | `/api/billing/feature-benefit-policies` | AI 기능 코드와 사용권 코드 매핑 조회 | - |
| GET | `/api/billing/benefits/me` | 내 현재 구독 기간 사용권 잔여량 조회. 잔여량이 없으면 현재 플랜 기준으로 자동 발급 | Bearer |
| GET | `/api/billing/benefit-transactions/me?limit=50` | 내 사용권 지급·차감 원장 조회 | Bearer |

현재 구독제 사용권 정책은 `subscription_plan`, `subscription_benefit_policy`,
`user_subscription`, `user_benefit_balance`, `ai_feature_benefit_policy`, `benefit_transaction` 테이블로 관리한다.
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
자체 LLM은 B 공고/기업 분석(`careertuner-b-jobposting-r1`)과 F 커뮤니티 검열의 Ollama 연동, D 면접 파인튜닝 실험을 중심으로 붙어 있으며,
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

### 시드 계정 (공통 비밀번호 `Career1234!`)

| 이메일 | 비고 |
| --- | --- |
| `admin@careertuner.dev` | ADMIN |
| `jiwon.kim@careertuner.dev` · `seoyeon.lee@careertuner.dev` | USER, 이메일 인증됨 |
| `pending@careertuner.dev` | 이메일 미인증 상태 |
| `minsu.park@careertuner.dev` | **소셜(카카오) 전용** — 비밀번호 로그인 불가 |

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

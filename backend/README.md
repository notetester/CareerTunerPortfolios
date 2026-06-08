# CareerTuner — Backend

Spring Boot **4.0.6** / Java **21** / **MyBatis** / **MySQL 8** REST API 서버.
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

## 지원 건 (Application Case) API

핵심 단위인 지원 건 API는 인증된 사용자 자신의 데이터만 다룬다.

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/application-cases` | 지원 건 생성 | Bearer |
| GET | `/api/application-cases` | 내 지원 건 목록 | Bearer |
| GET | `/api/application-cases/{id}` | 지원 건 상세 | Bearer |
| PATCH | `/api/application-cases/{id}` | 지원 건 수정 | Bearer |
| DELETE | `/api/application-cases/{id}` | 지원 건 삭제(현재 구현은 물리 삭제, 목표는 소프트 삭제) | Bearer |
| POST | `/api/application-cases/{id}/job-posting` | 텍스트/URL 공고문 저장 및 URL 본문 추출(현재 구현은 1건 교체, 목표는 revision 추가) | Bearer |
| POST | `/api/application-cases/{id}/job-posting/upload` | PDF/이미지 업로드 및 텍스트 추출 | Bearer |
| GET | `/api/application-cases/{id}/job-posting` | 현재 공고문 조회 | Bearer |
| POST | `/api/application-cases/{id}/job-analysis` | 실제 OpenAI 공고 분석 생성 | Bearer |
| GET | `/api/application-cases/{id}/job-analysis` | 공고 분석 조회 | Bearer |
| POST | `/api/application-cases/{id}/job-analysis/mock` | 개발용 mock 공고 분석 생성 | Bearer |
| POST | `/api/application-cases/{id}/company-analysis` | 실제 OpenAI 기업 분석 생성 | Bearer |
| GET | `/api/application-cases/{id}/company-analysis` | 기업 분석 조회 | Bearer |
| POST | `/api/application-cases/{id}/company-analysis/mock` | 개발용 mock 기업 분석 생성 | Bearer |
| POST | `/api/application-cases/{id}/analysis/mock` | 호환용 mock 공고/적합도 분석 생성 | Bearer |
| GET | `/api/application-cases/{id}/analysis` | 호환용 공고/적합도 분석 조회 | Bearer |
| GET | `/api/admin/job-analysis` | 관리자 공고 분석 조회 | Bearer(ADMIN) |
| GET | `/api/admin/company-analysis` | 관리자 기업 분석 조회 | Bearer(ADMIN) |
| GET | `/api/admin/ai-usage/b` | 관리자 B AI 사용량 로그 조회 | Bearer(ADMIN) |

실제 AI 분석과 이미지/스캔 PDF OCR은 `OPENAI_API_KEY`가 필요하다. 모델은 `OPENAI_MODEL`로 변경할 수 있으며 기본값은 `gpt-5`다.
텍스트 PDF는 PDFBox로 먼저 추출하고, 텍스트가 없는 PDF와 이미지는 OpenAI OCR을 사용한다.
공고문 파일 업로드는 기본 10MB까지 허용하며, 초과 시 `INVALID_INPUT` 응답으로 안내한다.
`/analysis/mock`은 화면과 데이터 흐름 검증 및 `fit_analysis`를 포함하는 호환 API용이며, B 프론트에서는 직접 사용하지 않는다.
목표 데이터 모델은 지원 건 보관/삭제를 `archived_at`, `deleted_at`으로 분리하고, 공고문 수정은 같은 공고의
revision으로 저장한다. 제품 정책은 `../docs/planning/기획.md`, 데이터/API 목표 구조는
`../docs/ARCHITECTURE.md`와 `../docs/FEATURE_MODULE_STRUCTURE.md`를 따른다.

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

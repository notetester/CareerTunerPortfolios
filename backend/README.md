# CareerTuner — Backend

Spring Boot **4.0.6** / Java **21** / **MyBatis** / **MySQL 8** REST API 서버.
인증은 **JWT(Access/Refresh) + Spring Security(stateless)**, 비밀번호는 **BCrypt**.

> 영속성 계층은 **MyBatis만** 사용한다(JPA 미사용). 매퍼는 `@Mapper` 인터페이스 +
> `src/main/resources/mapper/**/*.xml` 조합으로 작성하고, `map-underscore-to-camel-case`가 켜져 있다.

## 사전 준비

- JDK 21
- MySQL 8 — 개발은 할당받은 **`team1_db`** 사용. 스키마/시드 적용(최초 1회):
  `src/main/resources/db/schema.sql` → `db/data.sql` 순서로 실행(IntelliJ Database 콘솔 권장).

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
 └─ <그 외 도메인>  profile, applicationcase, jobposting, jobanalysis, companyanalysis,
                   fitanalysis, interview, community, payment, ai, admin (package-info 스텁)
```

## 응답 규약

모든 API는 `ApiResponse<T>`로 감싼다.

```json
{ "success": true,  "code": "OK",           "data": { } }
{ "success": false, "code": "UNAUTHORIZED",  "message": "이메일 또는 비밀번호가 올바르지 않습니다." }
```

REST 경로는 프런트 프록시 규약에 맞춰 모두 `/api/**` 하위.

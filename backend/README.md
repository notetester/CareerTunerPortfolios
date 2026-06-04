# CareerTuner — Backend

Spring Boot **4.0.6** / Java **21** / **MyBatis** / **MySQL 8** REST API 서버.

> 영속성 계층은 **MyBatis만** 사용한다(JPA 미사용). 매퍼는 `@Mapper` 인터페이스 +
> `src/main/resources/mapper/**/*.xml` 조합으로 작성하고, `map-underscore-to-camel-case`가 켜져 있다.

## 사전 준비

- JDK 21
- MySQL 8 (DB 생성: `src/main/resources/db/schema.sql` 실행)

## 실행

```bash
# Windows
.\gradlew.bat bootRun
# macOS/Linux
./gradlew bootRun
```

- 서버: `http://localhost:8080`
- 헬스 체크: `GET http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

스켈레톤 단계에서는 HikariCP `initialization-fail-timeout: -1` 설정으로 **MySQL이 없어도 부팅**된다(헬스 체크 가능). 실제 DB 기능은 MySQL 기동 후 동작한다.

## DB 접속 설정 (환경변수)

`application.yaml`은 아래 환경변수를 읽고, 없으면 로컬 기본값을 쓴다.

| 변수 | 기본값 |
| --- | --- |
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `careertuner` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | (빈 값) |

## 패키지 구조

```text
com.careertuner
 ├─ CareerTunerApplication        진입점
 ├─ common
 │   ├─ config   SecurityConfig(CORS/permitAll), OpenApiConfig
 │   ├─ web      ApiResponse(응답 표준), HealthController
 │   └─ exception ErrorCode, BusinessException, GlobalExceptionHandler
 │
 └─ <도메인>   auth, user, profile, applicationcase, jobposting,
              jobanalysis, companyanalysis, fitanalysis, interview,
              community, payment, ai, admin
```

각 도메인은 `controller / service / mapper(MyBatis 인터페이스) / domain(dto·model)` 계층으로 채워 나간다.
현재는 `package-info.java`로 도메인 책임만 정의되어 있다.

## 응답 규약

모든 API는 `ApiResponse<T>`로 감싼다.

```json
// 성공
{ "success": true, "code": "OK", "data": { ... } }
// 실패
{ "success": false, "code": "NOT_FOUND", "message": "대상을 찾을 수 없습니다." }
```

REST 경로는 프런트엔드 프록시 규약에 맞춰 모두 `/api/**` 하위에 둔다.

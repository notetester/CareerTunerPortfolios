# CareerTuner — 아키텍처 개요

> **CareerTuner** — 채용공고에 맞춰 내 스펙과 면접 답변을 조정하는 AI 취업 전략 플랫폼.
> 공고를 올리면 AI가 직무 요구사항·우대조건·기업 현황을 분석하고, 지원자 역량과 비교해
> 맞춤 지원 전략·학습 방향·자격증 추천·예상 질문을 제공하며, AI 가상 면접으로 모의면접·답변 첨삭까지 한다.

이 서비스의 핵심 단위는 **공고가 아니라 "지원 건(Application Case)"** 이다. 기업/공고/직무별로
독립된 AI 작업 공간(ChatGPT 세션과 유사)을 두고, 그 안에 공고 분석·기업 분석·스펙 비교·면접·리포트가 모인다.

제품 목표와 출시 우선순위는 [`planning/기획.md`](planning/기획.md), 사용자 메뉴는
[`PRODUCT_STRUCTURE.md`](PRODUCT_STRUCTURE.md), 표준 경로는
[`FEATURE_MODULE_STRUCTURE.md`](FEATURE_MODULE_STRUCTURE.md), 담당자·컴포넌트 단위 소유권은
[`TEAM_WORK_DISTRIBUTION.md`](TEAM_WORK_DISTRIBUTION.md)를 기준으로 한다.
UX와 모바일 세부 원칙은 [`디자인 분석`](planning/디자인%20분석.md)과
[`모바일 고려`](planning/모바일%20고려.md), 개발 환경 설명은 [`추천 구조`](planning/추천%20구조.md)를 참고한다.
문서 충돌 처리 규칙은 루트 [`AGENTS.md`](../AGENTS.md)를 따른다.

---

## 1. 모노레포 구조

```text
CareerTuner/                IntelliJ로 이 루트를 연다
 ├─ backend/                Spring Boot 4 + MyBatis + MySQL  (REST API, :8080)
 ├─ frontend/               React 18 + Vite + TS + Tailwind  (사용자/관리자 반응형 웹/PWA-ready, :5173)
 ├─ ml/                     자체 LLM 파인튜닝·평가 실험 산출물
 ├─ docs/                   기획·아키텍처 문서
 └─ (추후) frontend/android, frontend/ios   Capacitor 패키징
```

기능별 업무분담 구조는 [`FEATURE_MODULE_STRUCTURE.md`](FEATURE_MODULE_STRUCTURE.md)와
[`TEAM_WORK_DISTRIBUTION.md`](TEAM_WORK_DISTRIBUTION.md)를 기준으로 한다.
[`FEATURE_OWNERSHIP.md`](FEATURE_OWNERSHIP.md)는 빠른 참조용이다. 각 기능 담당자는
해당 기능의 사용자 프런트, 사용자 백엔드, 어드민 프런트, 어드민 백엔드 구조를 함께 관리한다.

관리자 프런트는 현 단계에서 별도 앱으로 분리하지 않는다. 검토 결과 기존 `admin-frontend`는
독립 `package.json`, Vite 설정, 라우트, 배포 설정 없이 빈 스켈레톤만 있었고, 현재 제품은 같은 인증/권한,
같은 API, 같은 디자인 시스템을 공유한다. 따라서 관리자 화면은 `frontend/src/admin/` 아래에 두고
`/admin/**` 라우트로 연결한다. 별도 배포 도메인, 강한 보안 경계, 독립 릴리즈 주기 같은 요구가 생길 때만
팀 결정으로 별도 앱 분리를 재검토한다.

## 2. 기술 스택

| 영역 | 선택 |
| --- | --- |
| 백엔드 | Spring Boot 4.1.0, Java 21, Spring Security, springdoc-openapi |
| 영속성 | **MyBatis** (JPA 미사용), MySQL 8 |
| 프런트엔드 | React 18, Vite 6, TypeScript, Tailwind CSS v4, shadcn/ui, react-router 7 |
| AI/ML | OpenAI Responses API, Ollama 로컬 LLM, LoRA/QLoRA 자체 모델 실험, JSON schema 검증/fallback |
| 모바일 | 반응형 웹 → PWA → Capacitor (Android/iOS) |

## 3. 개발 실행 흐름 / 포트

```text
React (Vite)   http://localhost:5173   ──/api/*──▶  Spring Boot  http://localhost:8080
```

브라우저는 `localhost:5173/api/...`로 호출하고, Vite dev 프록시(`frontend/vite.config.ts`)가
`localhost:8080/api/...`로 전달한다. 따라서 백엔드 컨트롤러는 모두 `/api/**` 하위에 둔다.

- 백엔드 실행/설정: [`../backend/README.md`](../backend/README.md)
- 프런트 실행/설정: [`../frontend/README.md`](../frontend/README.md)

## 4. 백엔드 아키텍처

도메인 패키지(`com.careertuner.<도메인>`)는 제품 기획서의 기능 요구사항과
[`FEATURE_MODULE_STRUCTURE.md`](FEATURE_MODULE_STRUCTURE.md)의 기능 모듈 기준을 따른다.

```text
auth · user · home · profile · applicationcase · jobposting · jobanalysis ·
companyanalysis · fitanalysis · analysis · dashboard · interview · correction ·
community · payment · billing · credit · settings · consent · notification · file ·
serviceinfo · support · company · legal · ai · admin
```

각 도메인은 `controller → service → mapper(MyBatis) → domain(dto/model)` 4계층으로 채운다.
공통 인프라는 `common/`에 둔다.

- `common/web/ApiResponse` — 표준 응답 envelope
- `common/exception` — `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`
- `common/config` — `SecurityConfig`(JWT/stateless·공개 엔드포인트 외 인증 필수·CORS), `OpenApiConfig`

**AI 모듈**은 공통 인프라(`ai/common`)와 프롬프트 템플릿(`ai/prompt`)만 공통에 두고,
도메인별 AI 서비스는 각 도메인 안에 둔다. 이렇게 해야 여러 담당자가 같은 `ai` 파일을 동시에 수정하는 충돌을 줄일 수 있다.
프롬프트 템플릿 기반으로 호출하며 토큰·크레딧 사용량을 기록한다
([제품 기획서의 AI 기능 설계](planning/기획.md#9-ai-기능-설계)).
팀 자체 LLM은 외부 API를 즉시 대체하는 단일 엔진이 아니라, 담당별 모델 산출물과 비용 절감, 포트폴리오 증거 확보를 위한 보조 provider다.
현재 런타임은 OpenAI 기반 분석과 F 커뮤니티 검열의 Ollama 연동이 중심이며, 담당별 모델 서버·공통 `AiClient`·상태 API는
[`담당별 자체 LLM 운영안`](planning/담당별_자체LLM_운영안.md)의 목표 구조를 따른다. 공통 `ai/common`, 공통 로그 구조,
`application.yaml`, DB 스키마를 바꾸는 작업은 팀장 승인 또는 팀 합의 후 진행한다.
기업 분석은 웹 검색, 외부 API, 수동 출처를 사용할 수 있으므로 출처 URL, 확인 시점, 재조회 시점,
확인된 사실과 AI 추론의 구분을 저장할 수 있어야 한다.

기업 분석의 웹 근거 수집은 **외부 검색 API(네이버 검색)** 를 새 시스템 경계로 둔다. 이 경로는
`careertuner.company-websearch.enabled` 플래그로 게이트하며 **기본 비활성(OFF)** 이다. OFF 상태에서는
검색 호출·검색 캐시 사용·WEB evidence 생성을 하지 않고 기존 공고 기반 분석 경로를 유지한다. ON 상태에서는
회사 식별 → 검색 → evidence gate **2소스 대조(공고 원문 + 웹 스니펫)** → 저장 순서로 동작하고,
검색 캐시(`company_search_cache`, TTL 7일)와 분석 1건당 검색·결과 상한으로 호출을 통제한다.
검색 키(`NAVER_SEARCH_CLIENT_ID`/`NAVER_SEARCH_CLIENT_SECRET`)는 env 로만 주입하고 커밋하지 않는다.

### 4.1 Spring Bean / AI Provider 계약

A/B/C/D/E/F 어느 영역이든 같은 인터페이스에 구현체가 여러 개 있으면 **선택 지점은 한 곳**이어야 한다.
특히 AI provider, fallback dispatcher, parser, sender처럼 `ProfileAiService`, `FitAnalysisAiService`,
`InterviewLlmGateway`, `PushSender` 류의 전략 인터페이스는 다음 규칙을 따른다.

- 하나의 전략 인터페이스에는 `@Primary` 구현체를 최대 하나만 둔다.
- 새 자체모델·Claude·OpenAI provider를 추가할 때 호출부가 주입받는 인터페이스 구현체를 여러 `@Primary`로 만들지 않는다.
- provider 우선순위는 primary dispatcher 한 곳에서 결정한다. 예: `자체모델 → Claude → OpenAI → 규칙/Mock`.
- 새 outer wrapper를 primary로 만들면 기존 dispatcher/provider의 `@Primary`는 제거하고, wrapper가 기존 dispatcher를 구체 타입으로 주입받아 감싼다.
- 조건부 provider가 필요해도 `@ConditionalOnProperty`만으로 모호성을 숨기지 않는다. 기본 설정과 CI 설정에서 단일 primary가 보장되어야 한다.
- Spring Boot 4/Jackson 3 환경에서는 애플리케이션 코드가 Spring 관리 `tools.jackson.databind.ObjectMapper` Bean을 주입받아 쓴다. `com.fasterxml.jackson.*` import 또는 `new ObjectMapper()` 직접 생성은 금지한다.

이 계약은 테스트로도 고정한다.

- `backend/src/test/java/com/careertuner/SpringBeanConventionTests.java` — `src/main/java` 전체에서 같은 인터페이스에 `@Primary` 구현체가 2개 이상 생기면 실패한다.
- `backend/src/test/java/com/careertuner/JacksonUsageConventionTests.java` — 애플리케이션 코드의 Jackson 2 import와 직접 `ObjectMapper` 생성을 차단한다.

**AI 오케스트레이터(자동 준비 파이프라인 — ✅ 구현 완료 2026-06-23)**: 사용자의 한 줄 요청을 받아 인테이크 챗봇이
부족 정보를 **멀티턴 대화**로 수집하고(지원 건→모드), 두뇌(Planner)가 **동적 실행계획(필요 파트 선택)**을 만든 뒤, 오케스트레이터가
**의존 그래프대로 병렬** 호출해 6개 도메인(프로필·공고·적합도·자소서·면접·커뮤니티) 지원 준비 전체를 자동화한다. 각 단계는
`자체 모델 → Claude(Haiku) → OpenAI` 폴백을 거쳐 일부 도메인이 미완이어도 mock/skip 으로 완주하며, 진행 상황은 SSE 로 스트리밍한다.
구현 위치는 백엔드 `ai/autoprep` 패키지(`ai/common` 아닌 독립 모듈) + 프론트 `features/autoprep`이며, 공통 영역 변경은 `SecurityConfig` 의 ASYNC/ERROR 디스패치 허용(SSE 401 방지)뿐이다. 시안은
[`planning/prototypes/`](planning/prototypes/)(orchestrator-screen/chat)와 면접 도메인 로드맵([`planning/면접 자율 에이전트 로드맵.md`](planning/면접%20자율%20에이전트%20로드맵.md))을 참조한다.

## 5. 데이터 모델 (요약)

DDL 원본: [`../backend/src/main/resources/db/schema.sql`](../backend/src/main/resources/db/schema.sql)

```text
users ──1:1── user_profile ─1:N─ user_profile_version
  │
  └─1:N─ application_case
              ├─1:N─ job_posting      같은 공고의 revision
              ├─1:N─ analysis_run
              ├─1:N─ job_analysis
              ├─1:N─ company_analysis
              ├─1:N─ fit_analysis
              ├─1:N─ strategy_task
              └─1:N─ interview_session ─1:N─ interview_question ─1:N─ interview_answer
              └─1:N─ correction_request

users ─1:N─ community_post
users ─1:N─ payment
users ─1:N─ credit_transaction
users ─1:N─ ai_usage_log ─N:1─ application_case(nullable)
```

배열/구조 데이터(스킬, 자격증, 추천 등)는 MySQL `JSON` 컬럼으로 저장한다.
현재 스키마는 분석 이력을 허용하도록 분석·공고 테이블을 `1:N`으로 두지만, 이 `1:N`은 서로 다른 공고 여러 개를
한 지원 건에 묶는 의미가 아니다. 하나의 `application_case`는 특정 기업·직무·공고 조합이며,
여러 `job_posting` row는 같은 공고의 수정 이력(revision)으로 해석한다.

지원 건의 `status`는 진행 상태만 표현한다.

```text
DRAFT / ANALYZING / READY / APPLIED / CLOSED
```

보관과 삭제는 상태값이 아니라 별도 시각 컬럼으로 관리한다.

```text
archived_at  보관 여부와 보관 시점
deleted_at   소프트 삭제 여부와 삭제 시점
```

기본 목록은 `archived_at IS NULL AND deleted_at IS NULL`, 보관함은
`archived_at IS NOT NULL AND deleted_at IS NULL`, 삭제된 지원 건은 `deleted_at IS NOT NULL`로 조회한다.
삭제는 DB row 삭제가 아니라 소프트 삭제다.

분석 재현성을 위해 프로필 버전과 분석 실행 단위는 별도 엔터티로 둔다. 분석 항목별 세부 결과와 근거는
구조화 JSON으로 저장하고, 사용자 확정 여부·사용자 수정 결과·확정 시점을 함께 저장한다.
각 분석 결과는 사용한 `user_profile_version`, `job_posting` revision, `analysis_run`에 연결한다.
사용자 업로드 파일, 공고 원문, 분석 결과, 첨삭 결과, 면접 답변, 음성·영상 원본은 현재 정책 초안상
기본 영구 보관으로 둔다. 사용자가 원본을 삭제하면 해당 원본을 필요로 하는 추가 분석과 재분석은 제공하지 않는다.

## 6. API 규약

- 모든 엔드포인트는 `/api/**`.
- 응답은 `ApiResponse<T>` (`success`, `code`, `message`, `data`).
- 아래 경로는 목표 API 계약 예시다. 현재 구현된 엔드포인트와 mock 여부는
  [`../backend/README.md`](../backend/README.md)를 기준으로 확인한다.

```text
POST /api/application-cases
POST /api/application-cases/{id}/job-posting
POST /api/application-cases/{id}/analysis
PATCH /api/application-cases/{id}/archive
PATCH /api/application-cases/{id}/restore
DELETE /api/application-cases/{id}          soft delete
POST /api/application-cases/{id}/interview-sessions
POST /api/interview-sessions/{id}/answers
GET  /api/interview-sessions/{id}/report
```

지원 건 삭제 API는 물리 삭제가 아니라 `deleted_at`을 기록하는 소프트 삭제로 구현한다.
공고문 저장 API는 같은 공고의 최신 revision을 추가하거나 갱신하는 의미이며, 서로 다른 공고를 한 지원 건에
묶는 용도로 사용하지 않는다.

## 7. 프런트엔드 아키텍처

현재는 Figma Make 디자인 초안 기반의 반응형 SPA이며, 일부 인증/API 연동이 연결되어 있다.
프로토타입 페이지의 내부 구현은 점진적으로 [`FEATURE_MODULE_STRUCTURE.md`](FEATURE_MODULE_STRUCTURE.md)의
`features/`와 `admin/features/` 표준 구조로 이동한다. 앱 전역 라우트·레이아웃·API 기반은 `app/`에 두고,
기능 전용 API·hook·type은 각 기능 폴더 안에 둔다.

사용자 기능과 관리자 기능은 각각 다음 하위 구조를 표준으로 한다.

```text
frontend/src/features/<feature>/{pages,components,api,hooks,types}
frontend/src/admin/features/<feature>/{pages,components,api,hooks,types}
```

3열 레이아웃(지원 건 목록 / 상세 / 준비도 패널)은 모바일에서 1열 + Drawer + 접이식 카드로 접는다
([모바일 반응형 화면 설계](planning/모바일%20고려.md#6-반응형-화면-설계)).

## 8. 모바일 / PWA 로드맵

1. 반응형 웹 + `manifest.webmanifest` (현재)
2. `vite-plugin-pwa`로 서비스워커·오프라인 캐시·아이콘
3. Capacitor로 Android/iOS 패키징, 푸시 알림

PWA 캐시는 정적 리소스와 공개 정보 중심으로 제한한다. 지원 건, 프로필, AI 분석, 첨삭, 면접 답변,
결제 정보 같은 개인·민감 데이터는 장기 캐시하지 않고 서버 우선 조회한다. 로그아웃 시 사용자 관련 캐시를 삭제하고,
오프라인 상태에서는 민감 기능에 "네트워크 연결 필요" 안내를 표시한다.

### 동의 정책 경계

- `TERMS`, `PRIVACY`는 회원 서비스의 필수 동의다. 철회 후에는 인증·동의·법률 문서·고객센터 등 재동의와 지원에 필요한 API만 허용한다.
- `AI_DATA`는 AI 생성·분석 API, `RESUME_ANALYSIS`는 이력서 문서 수집·분석 API의 기능 동의다. 컨트롤러의 `@RequiresConsent`와 서비스 검증을 함께 사용한다.
- `MARKETING`은 선택 동의이며 철회해도 핵심 기능을 제한하지 않는다. 모든 변경은 `user_consent`에 동의 여부·문서 버전·시각·출처를 이력으로 남긴다.
- 공개 법률 문서는 관리자 게시본을 우선하고, 게시본이 없을 때 코드의 기본 문서를 제공해 링크가 빈 화면이나 404가 되지 않게 한다.

## 9. MVP 단계

단계별 범위와 완료 기준은 [제품 기획서의 단계별 출시 범위](planning/기획.md#12-단계별-출시-범위)를 따른다.

- **1차** 회원가입/로그인 · 최소 약관/동의 · 프로필 · 지원 건 목록 · 공고 텍스트 업로드 · 공고 AI 분석 · 스펙 비교 · 지원 전략 · 예상 질문 · 텍스트 모의면접 · 답변 평가/기본 첨삭 · 기본 대시보드 · 관련 관리자 화면/API
- **2차** PDF/이미지 업로드 · 기업 현황 조사 · 음성 면접 · 면접 리포트 저장 · 장기 취업 경향 분석 · 커뮤니티 · 결제/크레딧
- **3차** AI 아바타 면접관 · 표정·시선·자세 분석 · 자소서/포트폴리오 고급 첨삭 · 직무별 학습 로드맵 · 관리자 프롬프트 관리

> ⚠️ 표정·자세 분석은 합격/불합격 판단 근거가 아니라 **면접 태도 개선 참고자료**로만 쓴다
> ([제품 기획서의 신뢰·안전·개인정보 원칙](planning/기획.md#10-신뢰안전개인정보-원칙)).

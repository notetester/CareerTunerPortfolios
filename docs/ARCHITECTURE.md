# CareerTuner — 아키텍처 개요

> **CareerTuner** — 채용공고에 맞춰 내 스펙과 면접 답변을 조정하는 AI 취업 전략 플랫폼.
> 공고를 올리면 AI가 직무 요구사항·우대조건·기업 현황을 분석하고, 지원자 역량과 비교해
> 맞춤 지원 전략·학습 방향·자격증 추천·예상 질문을 제공하며, AI 가상 면접으로 모의면접·답변 첨삭까지 한다.

이 서비스의 핵심 단위는 **공고가 아니라 "지원 건(Application Case)"** 이다. 기업/공고/직무별로
독립된 AI 작업 공간(ChatGPT 세션과 유사)을 두고, 그 안에 공고 분석·기업 분석·스펙 비교·면접·리포트가 모인다.

자세한 기획은 [`planning/기획.txt`](planning/기획.txt)가 원본이다(가장 중요). 그 외 [`디자인 분석`](planning/디자인%20분석.txt),
[`추천 구조`](planning/추천%20구조.txt), [`모바일 고려`](planning/모바일%20고려.txt) 참고.
실제 개발 공통 인식은 [`PRODUCT_STRUCTURE.md`](PRODUCT_STRUCTURE.md)와
[`FEATURE_MODULE_STRUCTURE.md`](FEATURE_MODULE_STRUCTURE.md)를 함께 본다.

---

## 1. 모노레포 구조

```text
CareerTuner/                IntelliJ로 이 루트를 연다
 ├─ backend/                Spring Boot 4 + MyBatis + MySQL  (REST API, :8080)
 ├─ frontend/               React 18 + Vite + TS + Tailwind  (사용자/관리자 반응형 웹/PWA-ready, :5173)
 ├─ docs/                   기획·아키텍처 문서
 └─ (추후) frontend/android, frontend/ios   Capacitor 패키징
```

기능별 업무분담 구조는 [`FEATURE_OWNERSHIP.md`](FEATURE_OWNERSHIP.md)를 기준으로 한다. 각 기능 담당자는
해당 기능의 사용자 프런트, 사용자 백엔드, 어드민 프런트, 어드민 백엔드 구조를 함께 관리한다.

관리자 프런트는 현 단계에서 별도 앱으로 분리하지 않는다. 검토 결과 기존 `admin-frontend`는
독립 `package.json`, Vite 설정, 라우트, 배포 설정 없이 빈 스켈레톤만 있었고, 현재 제품은 같은 인증/권한,
같은 API, 같은 디자인 시스템을 공유한다. 따라서 관리자 화면은 `frontend/src/admin/` 아래에 두고
`/admin/**` 라우트로 연결한다. 별도 배포 도메인, 강한 보안 경계, 독립 릴리즈 주기 같은 요구가 생길 때만
팀 결정으로 별도 앱 분리를 재검토한다.

## 2. 기술 스택

| 영역 | 선택 |
| --- | --- |
| 백엔드 | Spring Boot 4.0.6, Java 21, Spring Security, springdoc-openapi |
| 영속성 | **MyBatis** (JPA 미사용), MySQL 8 |
| 프런트엔드 | React 18, Vite 6, TypeScript, Tailwind CSS v4, shadcn/ui, react-router 7 |
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

도메인 패키지(`com.careertuner.<도메인>`)는 기획 §15를 따른다.

```text
auth · user · profile · applicationcase · jobposting · jobanalysis ·
companyanalysis · fitanalysis · interview · community · payment · serviceinfo ·
support · company · legal · notification · file · credit · consent · ai · admin
```

각 도메인은 `controller → service → mapper(MyBatis) → domain(dto/model)` 4계층으로 채운다.
공통 인프라는 `common/`에 둔다.

- `common/web/ApiResponse` — 표준 응답 envelope
- `common/exception` — `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`
- `common/config` — `SecurityConfig`(현재 permitAll·CORS), `OpenApiConfig`

**AI 모듈**은 공통 인프라(`ai/common`)와 프롬프트 템플릿(`ai/prompt`)만 공통에 두고,
도메인별 AI 서비스는 각 도메인 안에 둔다. 이렇게 해야 여러 담당자가 같은 `ai` 파일을 동시에 수정하는 충돌을 줄일 수 있다.
프롬프트 템플릿 기반으로 호출하며 토큰·크레딧 사용량을 기록한다(기획 §16).

## 5. 데이터 모델 (요약)

DDL 원본: [`../backend/src/main/resources/db/schema.sql`](../backend/src/main/resources/db/schema.sql)

```text
users ──1:1── user_profile
  │
  └─1:N─ application_case ──1:N─ job_posting
              │                 ├─ job_analysis
              │                 ├─ company_analysis
              │                 └─ fit_analysis
              └─1:N─ interview_session ─1:N─ interview_question ─1:N─ interview_answer

users ─1:N─ community_post
users ─1:N─ payment
users ─1:N─ ai_usage_log ─N:1─ application_case(nullable)
```

배열/구조 데이터(스킬, 자격증, 추천 등)는 MySQL `JSON` 컬럼으로 저장한다.

## 6. API 규약

- 모든 엔드포인트는 `/api/**`.
- 응답은 `ApiResponse<T>` (`success`, `code`, `message`, `data`).
- 핵심 리소스 경로(예시, 모바일 고려 문서 §5 기반):

```text
POST /api/application-cases
POST /api/application-cases/{id}/job-posting
POST /api/application-cases/{id}/analysis
POST /api/application-cases/{id}/interview-sessions
POST /api/interview-sessions/{id}/answers
GET  /api/interview-sessions/{id}/report
```

## 7. 프런트엔드 아키텍처

현재는 Figma Make 디자인 초안(10개 페이지) 기반의 반응형 SPA. 이후 기획 §14 구조로 확장:
`features/`(사용자 기능별 모듈), `admin/`(관리자 라우트와 관리자 기능), `services/`(API 클라이언트),
`store/`(상태관리), `hooks/`, `types/`.

사용자 기능과 관리자 기능은 각각 다음 하위 구조를 표준으로 한다.

```text
frontend/src/features/<feature>/{pages,components,api,hooks,types}
frontend/src/admin/features/<feature>/{pages,components,api,hooks,types}
```

3열 레이아웃(지원 건 목록 / 상세 / 준비도 패널)은 모바일에서 1열 + Drawer + 접이식 카드로 접는다(모바일 고려 §6·§9).

## 8. 모바일 / PWA 로드맵

1. 반응형 웹 + `manifest.webmanifest` (현재)
2. `vite-plugin-pwa`로 서비스워커·오프라인 캐시·아이콘
3. Capacitor로 Android/iOS 패키징, 푸시 알림

## 9. MVP 단계 (기획 §17)

- **1차** 회원가입/로그인 · 프로필 · 공고 텍스트 업로드 · 공고 AI 분석 · 스펙 비교 · 예상 질문 · 텍스트 모의면접 · 답변 평가/첨삭 · 지원 건 목록
- **2차** PDF/이미지 업로드 · 기업 현황 조사 · 음성 면접 · 면접 리포트 저장 · 장기 취업 경향 분석 · 커뮤니티 · 결제/크레딧
- **3차** AI 아바타 면접관 · 표정·시선·자세 분석 · 자소서/포트폴리오 고급 첨삭 · 직무별 학습 로드맵 · 관리자 프롬프트 관리

> ⚠️ 표정·자세 분석은 합격/불합격 판단 근거가 아니라 **면접 태도 개선 참고자료**로만 쓴다(기획 §8).

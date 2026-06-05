# Feature Module Structure

이 문서는 개발자가 실제 폴더와 담당 범위를 어떻게 나눌지 정리한다.
`PRODUCT_STRUCTURE.md`가 사용자 관점의 지도라면, 이 문서는 코드 소유권과 충돌 방지를 위한 지도다.
6명 담당자별 기능, AI, DB 분담의 상세 내용은 `TEAM_WORK_DISTRIBUTION.md`를 함께 본다.

## 1. 구조 원칙

- 최상위에는 `backend`, `frontend`, `docs`만 둔다.
- 관리자 프런트는 별도 앱으로 분리하지 않고 `frontend/src/admin` 아래에 둔다.
- 사용자 기능은 `frontend/src/features/<feature>`에 둔다.
- 관리자 기능은 `frontend/src/admin/features/<feature>`에 둔다.
- 백엔드는 도메인 단위로 `backend/src/main/java/com/careertuner/<domain>`에 둔다.
- MyBatis XML은 `backend/src/main/resources/mapper/<domain>`에 둔다.
- 공통 인프라는 `common`, AI 공통은 `ai/common`, 프롬프트는 `ai/prompt`에 둔다.

## 2. 기능 ID 표

| 기능 ID | 사이트 메뉴 | 사용자 프런트 | 백엔드 도메인 | 우선순위 |
| --- | --- | --- | --- | --- |
| `auth` | 로그인/회원가입 | `auth` | `auth`, `user` | 1차 |
| `home` | 홈 | `home` | `home` | 1차 |
| `dashboard` | 대시보드 | `dashboard` | `dashboard` | 1차 |
| `profile` | 내 프로필 | `profile` | `profile` | 1차 |
| `applications` | 지원 건 관리 | `applications` | `applicationcase`, `jobposting`, `jobanalysis`, `companyanalysis`, `fitanalysis` | 1차 |
| `interview` | AI 가상 면접 | `interview` | `interview` | 1차 |
| `correction` | AI 첨삭 | `correction` | `correction` | 1차~2차 |
| `analysis` | 취업 분석 | `analysis` | `analysis`, `fitanalysis` | 1차~2차 |
| `community` | 커뮤니티 | `community` | `community` | 2차 |
| `billing` | 결제/구독 | `billing` | `payment`, `billing`, `credit` | 2차 |
| `settings` | 설정 | `settings` | `settings`, `consent`, `notification` | 1차~2차 |
| `support` | 고객센터 | `support` | `support` | 2차 |
| `company` | 회사/브랜드 소개 | `company` | `company` | 2차 |
| `legal` | 약관/정책 | `legal` | `legal`, `consent` | 2차 |
| `notification` | 알림 | `notification` | `notification` | 2차 |
| `file` | 파일 업로드 | 공통 컴포넌트 | `file` | 2차 |
| `ai` | 직접 메뉴 없음 | 내부 API | `ai` | 1차~2차 |

`company`와 `companyanalysis`는 반드시 구분한다. `company`는 CareerTuner 회사/브랜드 소개이고, `companyanalysis`는 사용자가 지원하는 기업 분석이다.

## 3. 프런트엔드 표준 구조

사용자 기능:

```text
frontend/src/features/<feature>/
 ├─ pages/
 ├─ components/
 ├─ api/
 ├─ hooks/
 └─ types/
```

관리자 기능:

```text
frontend/src/admin/
 ├─ routes.ts
 ├─ pages/
 ├─ components/
 ├─ hooks/
 ├─ lib/
 └─ features/<feature>/
     ├─ pages/
     ├─ components/
     ├─ api/
     ├─ hooks/
     └─ types/
```

공통 업로드/미디어 컴포넌트:

```text
frontend/src/app/components/upload/
frontend/src/app/components/media/
```

공통 컴포넌트는 여러 담당자가 동시에 만지는 영역이므로 수정 전에 합의한다.

## 4. 백엔드 표준 구조

사용자 도메인:

```text
backend/src/main/java/com/careertuner/<domain>/
 ├─ controller/
 ├─ service/
 ├─ mapper/
 ├─ domain/
 └─ dto/
```

관리자 도메인:

```text
backend/src/main/java/com/careertuner/admin/<domain>/
 ├─ controller/
 ├─ service/
 ├─ mapper/
 ├─ domain/
 └─ dto/
```

MyBatis XML:

```text
backend/src/main/resources/mapper/<domain>/
backend/src/main/resources/mapper/admin/<domain>/
```

초기에는 모든 하위 계층을 구현하지 않아도 된다. 하지만 폴더는 기능별 담당자가 바로 확장할 수 있게 유지한다.

## 5. 담당자별 권장 분담

| 담당자 | 사용자 기능 | AI 기능 | 관리자 기능 |
| --- | --- | --- | --- |
| A | 인증, 회원, 프로필, 설정, 동의 | 이력서 요약, 기술스택 추출, 프로필 완성도 진단 | 회원 관리, 동의 이력 관리 |
| B | 지원 건, 공고문, 공고 분석, 기업 분석 | 공고 분석, 필수/우대조건 추출, 기업 요약 | 지원 건 관리, 공고/기업 분석 로그 관리 |
| C | 스펙 비교, 취업 분석, 대시보드 | 적합도 분석, 부족 역량 추천, 학습/자격증 추천 | 분석 통계, 적합도 분석 관리 |
| D | 가상 면접, 면접 리포트, 면접 파일 | 질문 생성, 꼬리 질문, 답변 평가, 리포트 생성 | 면접 세션 관리, 면접 리포트 관리 |
| E | 첨삭, 결제, 크레딧 | 답변 첨삭, 자소서 첨삭, 이력서 표현 개선 | 결제 관리, 크레딧 관리, 첨삭 로그 관리 |
| F | 커뮤니티, 고객센터, 알림, 공지, 회사/법적 문서 | 후기 요약, 질문 추출, 게시글 추천, 문의 답변 초안 | 게시판/신고, 공지/FAQ/문의, 알림 관리 |

담당자별 상세 사용자 기능, 예시 AI 출력, 주요 DB, 포트폴리오 설명 포인트는
`TEAM_WORK_DISTRIBUTION.md`에 둔다. 이 문서에서는 충돌을 줄이기 위한 실제 경로만 유지한다.

## 6. 실제 폴더 소유권

### A 담당

```text
frontend/src/features/auth
frontend/src/features/profile
frontend/src/features/settings
frontend/src/admin/features/users
frontend/src/admin/features/consents

backend/src/main/java/com/careertuner/auth
backend/src/main/java/com/careertuner/user
backend/src/main/java/com/careertuner/profile
backend/src/main/java/com/careertuner/settings
backend/src/main/java/com/careertuner/consent
backend/src/main/java/com/careertuner/admin/user
backend/src/main/java/com/careertuner/admin/consent
```

### B 담당

```text
frontend/src/features/applications
frontend/src/admin/features/application-cases
frontend/src/admin/features/job-analysis
frontend/src/admin/features/company-analysis

backend/src/main/java/com/careertuner/applicationcase
backend/src/main/java/com/careertuner/jobposting
backend/src/main/java/com/careertuner/jobanalysis
backend/src/main/java/com/careertuner/companyanalysis
backend/src/main/java/com/careertuner/admin/applicationcase
backend/src/main/java/com/careertuner/admin/jobanalysis
backend/src/main/java/com/careertuner/admin/companyanalysis
```

### C 담당

```text
frontend/src/features/analysis
frontend/src/features/dashboard
frontend/src/admin/features/analytics
frontend/src/admin/features/dashboard
frontend/src/admin/features/fit-analysis

backend/src/main/java/com/careertuner/fitanalysis
backend/src/main/java/com/careertuner/analysis
backend/src/main/java/com/careertuner/dashboard
backend/src/main/java/com/careertuner/admin/analytics
backend/src/main/java/com/careertuner/admin/dashboard
backend/src/main/java/com/careertuner/admin/fitanalysis
```

### D 담당

```text
frontend/src/features/interview
frontend/src/app/components/media
frontend/src/app/components/upload
frontend/src/admin/features/interviews
frontend/src/admin/features/interview-reports

backend/src/main/java/com/careertuner/interview
backend/src/main/java/com/careertuner/file
backend/src/main/java/com/careertuner/admin/interview
```

### E 담당

```text
frontend/src/features/correction
frontend/src/features/billing
frontend/src/admin/features/corrections
frontend/src/admin/features/payments
frontend/src/admin/features/credits
frontend/src/admin/features/plans
frontend/src/admin/features/ai-usage

backend/src/main/java/com/careertuner/correction
backend/src/main/java/com/careertuner/payment
backend/src/main/java/com/careertuner/billing
backend/src/main/java/com/careertuner/credit
backend/src/main/java/com/careertuner/admin/correction
backend/src/main/java/com/careertuner/admin/payment
backend/src/main/java/com/careertuner/admin/credit
backend/src/main/java/com/careertuner/admin/plan
backend/src/main/java/com/careertuner/admin/aiusage
```

### F 담당

```text
frontend/src/features/community
frontend/src/features/support
frontend/src/features/notification
frontend/src/features/company
frontend/src/features/legal
frontend/src/features/service
frontend/src/admin/features/community
frontend/src/admin/features/reports
frontend/src/admin/features/notices
frontend/src/admin/features/faqs
frontend/src/admin/features/support-tickets
frontend/src/admin/features/notifications

backend/src/main/java/com/careertuner/community
backend/src/main/java/com/careertuner/support
backend/src/main/java/com/careertuner/notification
backend/src/main/java/com/careertuner/company
backend/src/main/java/com/careertuner/legal
backend/src/main/java/com/careertuner/serviceinfo
backend/src/main/java/com/careertuner/admin/community
backend/src/main/java/com/careertuner/admin/report
backend/src/main/java/com/careertuner/admin/notice
backend/src/main/java/com/careertuner/admin/faq
backend/src/main/java/com/careertuner/admin/support
backend/src/main/java/com/careertuner/admin/notification
```

## 7. AI 공통 구조

AI 기능을 모든 담당자가 한 폴더에서 직접 수정하면 충돌이 잦다. 따라서 공통과 도메인별 AI 서비스를 분리한다.

```text
backend/src/main/java/com/careertuner/ai/
 ├─ common/
 │   ├─ AiClient
 │   ├─ AiRequest
 │   ├─ AiResponse
 │   └─ AiFeatureType
 │
 └─ prompt/
     ├─ PromptTemplate
     ├─ PromptTemplateMapper
     └─ PromptTemplateService
```

도메인별 AI 서비스는 각자 자기 도메인 안에 둔다.

```text
profile/service/ProfileAiService
jobanalysis/service/JobAnalysisAiService
companyanalysis/service/CompanyAnalysisAiService
fitanalysis/service/FitAnalysisAiService
interview/service/InterviewAiService
correction/service/CorrectionAiService
community/service/CommunityAiService
support/service/SupportAiService
```

## 8. 공통 파일 소유권

아래 파일은 충돌이 잦으므로 수정 전에 담당자와 합의한다.

```text
frontend/src/app/routes.ts
frontend/src/admin/routes.ts
frontend/src/app/components/layout/Header.tsx
frontend/src/app/components/layout/Footer.tsx
frontend/src/app/lib/api.ts
frontend/src/app/components/media
frontend/src/app/components/upload
frontend/src/admin/features/prompts
frontend/src/admin/features/logs

backend/src/main/resources/db/schema.sql
backend/src/main/resources/db/data.sql
backend/build.gradle
backend/src/main/resources/application.yaml
backend/src/main/java/com/careertuner/common/config/SecurityConfig.java
backend/src/main/java/com/careertuner/ai/common
backend/src/main/java/com/careertuner/ai/prompt
```

권장 소유권:

```text
라우트/헤더/공통 UI: 프런트 리더
schema.sql/data.sql: 백엔드/DB 리더
build.gradle/application.yaml: 백엔드 리더
SecurityConfig/Auth: A 담당자
AiClient/Prompt 공통: AI 공통 담당자
```

`frontend/src/features/service`는 사용자 프런트의 서비스 소개 기능이고,
`backend/src/main/java/com/careertuner/serviceinfo`는 같은 기능의 백엔드 도메인이다.
프런트 폴더명을 `serviceinfo`로 새로 만들지 않는다.

## 9. 지금 폴더만 먼저 둔 기능

아래 기능은 아직 API나 DB가 완성되지 않았지만 곧 필요해질 가능성이 높아서 골격을 먼저 둔다.

```text
notification
file
credit
consent
ai/common
ai/prompt
admin/user
admin/consent
admin/jobanalysis
admin/companyanalysis
admin/fitanalysis
admin/payment
admin/credit
admin/plan
admin/aiusage
admin/prompt
admin/report
admin/notice
admin/faq
admin/notification
```

관리자 프런트의 `frontend/src/admin/features/prompts`와 `frontend/src/admin/features/logs`도
프롬프트/운영 공통 기능 골격으로 유지한다.

골격만 있는 패키지나 폴더는 `package-info.java` 또는 `.gitkeep`만 있어도 정상이다.
실제 구현은 담당 기능 작업이 시작될 때 채운다.

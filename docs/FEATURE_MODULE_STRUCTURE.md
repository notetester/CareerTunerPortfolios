# Feature Module Structure

이 문서는 개발자가 실제 폴더와 담당 범위를 어떻게 나눌지 정리한다.
`PRODUCT_STRUCTURE.md`가 사용자 관점의 지도라면, 이 문서는 코드 소유권과 충돌 방지를 위한 지도다.
6명 담당자별 기능, AI, DB 분담의 상세 내용은 `TEAM_WORK_DISTRIBUTION.md`를 함께 본다.

## 1. 구조 원칙

- 애플리케이션 런타임 소스는 최상위 `backend`, `frontend`에서 관리하고, 기획·규칙은 `docs`와 루트 설정 문서에 둔다.
- 관리자 프런트는 별도 앱으로 분리하지 않고 `frontend/src/admin` 아래에 둔다.
- 사용자 기능은 `frontend/src/features/<feature>`에 둔다.
- 관리자 기능은 `frontend/src/admin/features/<feature>`에 둔다.
- 백엔드는 도메인 단위로 `backend/src/main/java/com/careertuner/<domain>`에 둔다.
- MyBatis XML은 `backend/src/main/resources/mapper/<domain>`에 둔다.
- 공통 인프라는 `common`, AI 공통은 `ai/common`, 프롬프트는 `ai/prompt`에 둔다.
- 사용자 기능이 릴리스 완료로 표시되려면 관련 관리자 화면과 관리자 API도 같은 릴리스 기준으로 완료해야 한다.
- 공통 영역의 Owner는 팀장이며, 라우팅·공통 컴포넌트·공통 API·DB·인증/권한·AI 프롬프트·로그 구조 변경은 팀장 승인 또는 팀 합의 후 진행한다.

## 2. 기능 ID 표

| 기능 ID | 사이트 메뉴 | 사용자 프런트 | 연관 백엔드 도메인 | 출시 범위 |
| --- | --- | --- | --- | --- |
| `auth` | 로그인/회원가입 | `auth` | `auth`, `user` | 1차 |
| `home` | 홈 | `home` | `home` | 1차 |
| `dashboard` | 대시보드 | `dashboard` | `dashboard` | 1차 |
| `profile` | 내 프로필 | `profile` | `profile` | 1차 |
| `applications` | 지원 건 관리 | `applications` | `applicationcase`, `jobposting`, `jobanalysis`, `companyanalysis`, `fitanalysis` | 1차 핵심 / 기업·파일 분석 2차 |
| `interview` | AI 가상 면접 | `interview` | `interview` | 1차 |
| `correction` | AI 첨삭 | `correction` | `correction` | 1차 답변 첨삭 / 문서 고급 첨삭 2~3차 |
| `analysis` | 취업 분석 | `analysis` | `analysis`, `fitanalysis` | 1차 지원 건 적합도 / 2차 장기 분석 |
| `community` | 커뮤니티 | `community` | `community` | 2차 |
| `billing` | 결제/구독 | `billing` | `payment`, `billing`, `credit` | 2차 |
| `settings` | 설정 | `settings` | `settings`, `consent`, `notification` | 1차 계정·동의 / 2차 알림 고도화 |
| `support` | 고객센터 | `support` | `support` | 2차 |
| `company` | 회사/브랜드 소개 | `company` | `company` | 2차 |
| `legal` | 약관/정책 | `legal` | `legal`, `consent` | 1차 최소 약관·동의 / 2차 고도화 |
| `notification` | 알림 | `notification` | `notification` | 2차 |
| `file` | 파일 업로드 | 공통 컴포넌트 | `file` | 2차 |
| `ai` | 직접 메뉴 없음 | 내부 API | `ai` | 1차~2차 |

`company`와 `companyanalysis`는 반드시 구분한다. `company`는 CareerTuner 회사/브랜드 소개이고, `companyanalysis`는 사용자가 지원하는 기업 분석이다.
연관 백엔드 도메인은 대표 호출 범위이며 전체 의존성이나 소유권 목록이 아니다. 실제 소유권은 아래 표준 폴더 소유권을 따른다.
출시 범위는 기능군의 세부 기능별 목표 시점을 요약한 것이며, 폴더 소유권이나 모든 하위 기능의 동시 완료를 의미하지 않는다.

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

공통 컴포넌트는 팀장 Owner의 공통 영역이므로 수정 전에 팀장 승인 또는 팀 합의를 거친다.

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

## 5. 담당자별 확정 분담

| 담당자 | 사용자 기능 | AI 기능 | 관리자 기능 |
| --- | --- | --- | --- |
| A | 인증, 회원, 프로필, 설정, 동의 | 이력서 요약, 기술스택 추출, 프로필 완성도 진단 | 회원 관리, 동의 이력 관리 |
| B | 지원 건, 공고문, 공고 분석, 기업 분석 | 공고 분석, 필수/우대조건 추출, 기업 요약 | 지원 건 관리, 공고/기업 분석 로그 관리 |
| C | 스펙 비교, 취업 분석, 대시보드 | 적합도 분석, 부족 역량 추천, 학습/자격증 추천, 커리어 전략 추천, 대시보드 AI 분석 출력 | 분석 통계, 적합도 분석 관리 |
| D | 가상 면접, 면접 리포트, 면접 파일 | 질문 생성, 꼬리 질문, 답변 평가, 리포트 생성 | 면접 세션 관리, 면접 리포트 관리 |
| E | 첨삭, 결제, 크레딧 | 답변 첨삭, 자소서 첨삭, 이력서 표현 개선 | 결제 관리, 크레딧 관리, 첨삭 로그 관리 |
| F | 커뮤니티, 고객센터, 알림, 공지, 회사/법적 문서 | 후기 요약, 질문 추출, 게시글 추천, 문의 답변 초안 | 게시판/신고, 공지/FAQ/문의, 알림 관리 |

담당자별 상세 사용자 기능, 예시 AI 출력, 주요 DB, 포트폴리오 설명 포인트는
`TEAM_WORK_DISTRIBUTION.md`에 둔다. 이 문서에서는 충돌을 줄이기 위한 실제 경로만 유지한다.

### 교차 기능 책임 경계

| 교차 영역 | 책임 경계 |
| --- | --- |
| `applications` 사용자 화면 | B가 목록·생성·공고·기업 분석과 화면 셸을 담당하고, C가 적합도·전략·학습 추천 컴포넌트를 담당한다. |
| 프로필 원문과 AI 첨삭 | A가 프로필·이력서·자기소개서 원문 관리를, E가 AI 첨삭 요청과 첨삭 결과를 담당한다. |
| 면접 답변 평가와 첨삭 | D가 면접 세션 내 평가·리포트를, E가 별도 개선안·첨삭 이력을 담당한다. |
| 설정의 알림 항목 | A가 설정 화면 통합을, F가 `notification` 도메인과 알림 설정 API를 담당한다. |
| 법적 문서와 동의 이력 | F가 약관·정책 콘텐츠를, A가 `consent` 도메인과 사용자 동의 이력을 담당한다. |
| AI 사용량 | 각 도메인이 공통 로깅 규약으로 기록하고, E가 결제·크레딧·사용량 조회 화면을 담당한다. 스키마와 공통 로깅은 팀장 Owner의 공통 영역이다. |

## 6. 표준 폴더 소유권

아래 경로는 기능 구현 시 따라야 할 표준 소유권이다. 현재 폴더 존재 여부는 실제 저장소에서 확인한다.

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

B는 `frontend/src/features/applications`의 화면 셸, 목록, 생성, 공고·기업 분석 영역을 소유한다.
C 소유 컴포넌트는 아래 C 담당 경로를 우선한다.

### C 담당

```text
frontend/src/features/analysis
frontend/src/features/dashboard
frontend/src/features/applications/components/FitAnalysisPanel.tsx
frontend/src/features/applications/components/StrategyPanel.tsx
frontend/src/features/applications/components/LearningRecommendationPanel.tsx
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
frontend/src/admin/features/interviews
frontend/src/admin/features/interview-reports

backend/src/main/java/com/careertuner/interview
backend/src/main/java/com/careertuner/file
backend/src/main/java/com/careertuner/admin/interview
```

음성/영상 업로드에 필요한 `frontend/src/app/components/media`, `frontend/src/app/components/upload`는
D가 주로 사용하지만 팀장 Owner의 공통 영역이다. 수정이 필요하면 공통 영역 절차를 따른다.

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

아래 파일과 경로는 팀장 Owner의 공통 영역이다. 기능 담당자가 수정해야 할 때는 수정 필요 사유,
영향 범위, 변경 파일, 관련 기능을 공유한 뒤 팀장 승인 또는 팀 합의 후 반영한다.

```text
frontend/src/app/routes.ts
frontend/src/admin/routes.ts
frontend/src/features/home
frontend/src/app/components/layout/Header.tsx
frontend/src/app/components/layout/Footer.tsx
frontend/src/app/lib/api.ts
frontend/src/app/components/media
frontend/src/app/components/upload
frontend/src/admin/features/prompts

backend/src/main/java/com/careertuner/common
backend/src/main/java/com/careertuner/home
backend/src/main/resources/db/schema.sql
backend/src/main/resources/db/data.sql
backend/build.gradle
backend/src/main/resources/application.yaml
backend/src/main/java/com/careertuner/common/config/SecurityConfig.java
backend/src/main/java/com/careertuner/ai/common
backend/src/main/java/com/careertuner/ai/prompt
backend/src/main/java/com/careertuner/admin/prompt
```

향후 생성할 `frontend/src/admin/features/logs`도 생성 시점부터 공통 영역으로 취급한다.

확정 소유권과 운영 규칙:

```text
Owner: 팀장
수정 전 공유: 수정 사유, 영향 범위, 변경 파일, 관련 기능
합의 필수: 라우팅, 공통 컴포넌트, 공통 API, DB 구조, 인증/권한, AI 프롬프트, 로그 구조
예외: 단순 오타, 주석, 명백한 문서 오류
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
admin/auth
admin/profile
admin/settings
admin/home
admin/consent
admin/jobanalysis
admin/companyanalysis
admin/fitanalysis
admin/analytics
admin/payment
admin/billing
admin/credit
admin/plan
admin/aiusage
admin/legal
admin/company
admin/serviceinfo
admin/prompt
admin/report
admin/notice
admin/faq
admin/notification
```

관리자 프런트의 `frontend/src/admin/features/prompts`는 프롬프트 공통 기능 골격으로 유지한다.
`frontend/src/admin/features/logs`는 시스템 로그 작업을 시작할 때 생성할 예정 경로다.

기존 관리자 골격 담당:

| 관리자 골격 | 담당 |
| --- | --- |
| `admin/auth`, `admin/profile`, `admin/settings` | A |
| `admin/home` | 팀장 |
| `admin/analytics` | C |
| `admin/billing` | E |
| `admin/legal`, `admin/company`, `admin/serviceinfo` | F |

분석 통계 백엔드 표준 경로는 `backend/src/main/java/com/careertuner/admin/analytics`다.
현재 저장소의 `backend/src/main/java/com/careertuner/admin/analysis`는 신규 구현 시 `admin/analytics`로 정리한다.

골격만 있는 패키지나 폴더는 `package-info.java` 또는 `.gitkeep`만 있어도 정상이다.
실제 구현은 담당 기능 작업이 시작될 때 채운다.

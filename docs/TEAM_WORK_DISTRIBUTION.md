# Team Work Distribution

이 문서는 6명 개발자가 CareerTuner를 기능별로 나누어 개발하기 위한 공통 합의안이다.
`PRODUCT_STRUCTURE.md`가 사용자 관점의 제품 구조이고, `FEATURE_MODULE_STRUCTURE.md`가 실제 폴더 구조라면,
이 문서는 각 담당자가 어떤 사용자 기능, AI 기능, 관리자 기능, DB를 함께 책임지는지 정리한다.

담당 지정은 해당 영역을 구현할 때의 소유권을 뜻한다. 사용자 기능이 특정 릴리스에 포함되면 관련 관리자 화면과
관리자 API도 같은 릴리스의 완료 기준에 포함한다.
출시 단계는 `docs/planning/기획.md`, 표준 경로와 교차 기능 경계는 `FEATURE_MODULE_STRUCTURE.md`를 따른다.
각 담당자의 `주요 DB` 목록은 최종 목표 데이터 구조를 포함하므로, 현재 스키마 존재 여부는
`backend/src/main/resources/db/schema.sql`에서 확인한다.

## 1. 적용 판단

초안의 방향은 현재 리포지토리 구조와 잘 맞는다. 특히 6명 모두가 독립적인 AI 기능을 하나 이상 맡도록
수직 분담한 점이 좋다. 기능별로 사용자 화면, 사용자 API, 관리자 화면, 관리자 API를 함께 보는 방식도
현재 모노레포 구조와 맞다.

반영하면서 아래만 정규화한다.

- 사용자 프런트의 서비스 소개 폴더는 `frontend/src/features/service`를 사용한다.
- 백엔드의 서비스 소개 도메인은 기존 패키지명인 `serviceinfo`를 유지한다.
- `frontend/src/features/applications`는 B와 C가 함께 만지는 핵심 화면이므로 컴포넌트 단위 소유권을 명확히 둔다.
- `frontend/src/app/components/media`, `frontend/src/app/components/upload`, `backend/src/main/java/com/careertuner/file`은 D가 주로 쓰지만 공통 영역으로 취급한다.
- `ai/common`, `ai/prompt`, 관리자 프롬프트, 시스템 로그는 팀장 Owner의 공통 영역으로 관리한다.

## 2. 6명 수직 분담

```text
A. 회원/프로필/설정 + AI 이력서·스펙 추출
B. 지원 건/공고문/공고·기업 분석 + AI 공고 분석
C. 스펙 비교/취업 분석/대시보드 + AI 커리어 전략 추천
D. 가상 면접/면접 리포트 + AI 면접관
E. 첨삭/결제/크레딧 + AI 답변·자소서 첨삭
F. 커뮤니티/고객센터/공지/알림 + AI 후기 요약·추천·문의 답변
```

이렇게 나누면 각 담당자가 단순 CRUD만 맡지 않고, 사용자 가치가 드러나는 AI 흐름까지 함께 설명할 수 있다.

## 3. AI 구현 기능 분배

아래 AI 기능은 모두 구현해야 할 항목이다. 공통 AI 클라이언트, 프롬프트 템플릿, 사용량 로깅은
팀장 Owner의 공통 영역이고, 각 담당자는 자기 도메인 안에 도메인별 AI 서비스를 구현한다.

| 번호 | AI 기능 | 구현 담당 |
| --- | --- | --- |
| 1 | 이력서/프로필 AI 요약 | A |
| 2 | 기술스택 AI 추출 | A |
| 3 | 자기소개서 핵심 키워드 추출 | A |
| 4 | 공고문 AI 분석 | B |
| 5 | 기업 현황 AI 요약 | B |
| 6 | 공고-스펙 적합도 AI 분석 | C |
| 7 | 부족 역량 AI 추천 | C |
| 8 | 학습/자격증 AI 추천 | C |
| 9 | 예상 면접 질문 AI 생성 | D |
| 10 | 꼬리 질문 AI 생성 | D |
| 11 | 면접 답변 AI 평가 | D |
| 12 | 답변 AI 첨삭 | E |
| 13 | 자기소개서 AI 첨삭 | E |
| 14 | 포트폴리오 설명 AI 개선 | E |
| 15 | 장기 취업 경향 AI 분석 | C |
| 16 | 커뮤니티 면접 후기 AI 요약 | F |
| 17 | 커뮤니티 게시글 AI 태그 추천 | F |
| 18 | 사용자 관심 기반 게시글 AI 추천 | F |
| 19 | 고객문의 AI 답변 초안 생성 | F |
| 20 | 부적절 게시글/신고 AI 분류 | F |

공통 AI 호출, 프롬프트 템플릿, 사용량 로깅은 `ai/common`, `ai/prompt`, `ai_usage_log`를 통해 공통 규약으로 맞춘다.
각 담당자는 자기 도메인 안에 도메인별 AI 서비스를 둔다.
각 도메인은 공통 규약으로 사용량을 기록하고, E는 결제·크레딧·사용량 조회 화면을 담당한다.
`ai_usage_log` 스키마와 공통 로깅 구현은 특정 기능 담당자가 단독 변경하지 않는 공통 영역이다.

### 관리자 동시 완료 원칙

각 담당자는 사용자 기능을 완료할 때 관련 관리자 화면과 API를 함께 완료한다. 관리자 기능은 빈 메뉴가 아니라
운영자가 실제로 데이터를 확인하고 조치할 수 있는 상태를 뜻한다.

최소 완료 기준:

```text
목록 조회
검색과 필터
상세 조회
필요한 상태 변경 또는 운영 메모
권한 검증
처리 시점 또는 변경 이력 기록
```

## 4. A 담당: 회원/프로필/설정

### 사용자 기능

```text
로그인
회원가입
이메일 인증
비밀번호 찾기
소셜 로그인
내 프로필
이력서 관리
자기소개서 기본 관리
기술스택 관리
자격증/학력 관리
계정 설정
AI 데이터 사용 동의
```

### AI 기능

```text
AI 이력서 요약
AI 기술스택 추출
AI 경력/프로젝트 핵심 키워드 추출
AI 프로필 완성도 진단
```

예시 결과:

```text
보유 기술: React, Java, MySQL
프로젝트 경험: 게시판, 로그인, REST API 연동
강점 키워드: 협업, 문제 해결, 프런트엔드 구현
부족한 정보: 성과 수치, 배포 경험, 역할 상세 설명
```

### 담당 폴더

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

### 주요 DB

```text
users
user_social
email_verification
refresh_token
user_profile
user_consent
```

### 포트폴리오 설명 포인트

```text
회원 인증부터 사용자 프로필 관리까지 구현하고,
AI를 활용해 이력서 텍스트에서 기술스택, 경력 키워드, 부족한 프로필 정보를 자동 추출하는 기능을 개발했다.
```

## 5. B 담당: 지원 건/공고문/공고·기업 분석

### 사용자 기능

```text
지원 건 목록
새 지원 건 만들기
지원 건 상세
공고문 텍스트 등록
공고문 PDF/이미지 등록 준비
공고 원문 보기
공고 분석 결과 보기
기업 분석 결과 보기
지원 상태 관리
```

### AI 기능

```text
AI 공고문 분석
AI 필수 조건 추출
AI 우대 조건 추출
AI 담당 업무 요약
AI 기업 현황 요약
AI 면접 포인트 추출
```

예시 결과:

```text
직무: 프런트엔드 개발자
필수 기술: React, JavaScript, REST API
우대 기술: TypeScript, AWS, Next.js
담당 업무: 웹 서비스 개발 및 유지보수
면접 포인트: React 프로젝트 경험, API 연동 경험, 협업 경험
```

### 담당 폴더

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

B는 `applications` 안에서 아래 화면 요소를 우선 담당한다.

```text
ApplicationList
NewApplicationPage
JobPostingPanel
JobAnalysisPanel
CompanyAnalysisPanel
ApplicationOverviewPanel
```

### 주요 DB

```text
application_case
job_posting
job_analysis
company_analysis
ai_usage_log
```

### 포트폴리오 설명 포인트

```text
사용자가 등록한 채용공고를 지원 건 단위로 관리하고,
AI를 활용해 공고의 필수 조건, 우대 조건, 직무 역량, 기업 분석 포인트를 자동 추출하는 기능을 개발했다.
```

## 6. C 담당: 스펙 비교/취업 분석/대시보드

### 사용자 기능

```text
내 스펙과 공고 비교
직무 적합도 점수
부족 역량 분석
추천 학습
추천 자격증
지원 전략
취업 분석 대시보드
장기 지원 경향 분석
전체 준비 현황 대시보드
```

### AI 기능

```text
AI 공고-스펙 적합도 분석
AI 부족 역량 추천
AI 학습 로드맵 추천
AI 자격증 추천
AI 장기 취업 경향 분석
AI 다음 지원 방향 추천
대시보드 AI 분석 결과 요약
```

예시 결과:

```text
최근 지원한 공고에서 TypeScript와 AWS가 반복적으로 부족 역량으로 나타납니다.
프런트엔드 직무 지원을 유지하려면 TypeScript 프로젝트 1개와 AWS 배포 경험을 추가하는 것이 좋습니다.
```

### 담당 폴더

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

C는 `applications` 안에서 아래 컴포넌트도 담당한다.

```text
frontend/src/features/applications/components/FitAnalysisPanel.tsx
frontend/src/features/applications/components/StrategyPanel.tsx
frontend/src/features/applications/components/LearningRecommendationPanel.tsx
```

### 주요 DB

```text
fit_analysis
job_analysis
application_case
interview_session
interview_answer
ai_usage_log
```

### 포트폴리오 설명 포인트

```text
채용공고의 요구 조건과 사용자 프로필을 비교하여 적합도 점수, 부족 역량, 학습 우선순위를 제공하고,
AI를 활용해 사용자의 장기 지원 경향과 커리어 전략을 추천하며, 대시보드에 핵심 분석 결과를 요약 출력하는 기능을 개발했다.
```

## 7. D 담당: 가상 면접/면접 리포트

### 사용자 기능

```text
AI 가상 면접
면접 모드 선택
예상 질문 목록
면접 세션 생성
질문 출력
답변 입력
음성 녹음
영상 녹화 준비
꼬리 질문
면접 리포트
질문별 평가 결과
```

### AI 기능

```text
AI 예상 면접 질문 생성
AI 꼬리 질문 생성
AI 면접관 대화 진행
AI 답변 평가
AI 면접 리포트 생성
```

예시 평가:

```text
답변이 너무 짧고 구체성이 부족합니다.
본인의 역할, 구현 기능, 문제 해결 경험, 결과를 추가하면 좋습니다.
```

### 담당 폴더

```text
frontend/src/features/interview
frontend/src/admin/features/interviews
frontend/src/admin/features/interview-reports

backend/src/main/java/com/careertuner/interview
backend/src/main/java/com/careertuner/file
backend/src/main/java/com/careertuner/admin/interview
```

음성/영상 업로드 컴포넌트가 생기면 아래 공통 컴포넌트를 함께 사용할 수 있다.
단, 이 영역은 팀장 Owner의 공통 영역이므로 수정 전 팀장 승인 또는 팀 합의가 필요하다.

```text
frontend/src/app/components/media
frontend/src/app/components/upload
```

### 주요 DB

```text
interview_session
interview_question
interview_answer
file_asset
ai_usage_log
```

### 포트폴리오 설명 포인트

```text
지원 건 기반 AI 모의면접 세션을 구현하고,
AI를 활용해 예상 질문, 꼬리 질문, 답변 평가, 면접 리포트를 생성하는 기능을 개발했다.
```

## 8. E 담당: 첨삭/결제/크레딧

### 사용자 기능

```text
AI 첨삭
면접 답변 첨삭
자기소개서 첨삭
이력서 문장 첨삭
포트폴리오 설명 첨삭
요금제
크레딧 충전
크레딧 사용 내역
결제 내역
AI 사용량 확인
```

### AI 기능

```text
AI 면접 답변 첨삭
AI 자기소개서 첨삭
AI 이력서 표현 개선
AI 포트폴리오 설명 개선
AI 사용량 기반 요금제 추천
```

크레딧은 AI 기능과 자연스럽게 연결한다. 현재 정책 초안은 무료 사용자에게 제한된 무료 크레딧과 공고 분석 보관 1건을 제공하고,
AI 기능별로 서로 다른 차감량을 적용하는 것이다. AI 영상 면접은 고비용 기능이므로 텍스트·음성 기능보다 많은 크레딧을 소비한다.
정확한 차감량은 AI 모델, 토큰 비용, 영상 처리 비용, 회의 결과에 따라 변경될 수 있다.

```text
답변 첨삭: 낮은 차감
자기소개서 첨삭: 중간 차감
공고/기업 분석: 중간~높은 차감
AI 영상 면접: 높은 차감
```

E 담당자는 결제 요청 직후가 아니라 구매 확정 또는 결제 성공 검증 후 크레딧을 지급하는 흐름을 구현한다.
실패한 AI 요청은 기본 미차감으로 처리하고, 구매 확정 후에는 기본적으로 환불 불가 정책을 따른다.
법적 의무, 중복 결제, 시스템 오류 등 예외 환불 정책은 관리자 결제 관리에서 확인할 수 있어야 한다.

### 담당 폴더

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

### 주요 DB

```text
correction_request
payment
credit_transaction
ai_usage_log
users.credit
plan
```

### 포트폴리오 설명 포인트

```text
AI를 활용한 면접 답변, 자기소개서, 이력서, 포트폴리오 설명 첨삭 기능을 개발하고,
AI 기능 사용량에 따른 크레딧 차감 및 결제 내역 관리 구조를 구현했다.
```

## 9. F 담당: 커뮤니티/고객센터/공지/알림

### 사용자 기능

```text
커뮤니티
취업 후기
면접 후기
직무별 질문 공유
합격 전략 게시판
댓글
좋아요/북마크
신고
고객센터
FAQ
공지사항
문의하기
알림
회사 소개
법적 문서 페이지
```

### AI 기능

```text
AI 면접 후기 요약
AI 게시글 태그 추천
AI 실제 면접 질문 추출
AI 관심 기반 게시글 추천
AI 부적절 게시글/신고 분류
AI 고객문의 답변 초안 생성
```

예시 요약:

```text
기업: 네이버
직무: 백엔드 개발자
면접 유형: 기술 면접
주요 질문:
- JPA와 MyBatis의 차이를 설명해주세요.
- 인덱스가 필요한 상황을 설명해주세요.
- 팀 프로젝트에서 갈등을 해결한 경험이 있나요?
난이도: 보통
키워드: Java, DB, 협업, 문제 해결
```

### 담당 폴더

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

### 주요 DB

```text
community_post
community_comment
community_reaction
community_report
notice
faq
support_ticket
notification
```

### 포트폴리오 설명 포인트

```text
커뮤니티와 고객센터 기능을 구현하고,
AI를 활용해 면접 후기 요약, 실제 질문 추출, 게시글 태그 추천, 신고 분류, 고객문의 답변 초안 생성을 지원하는 기능을 개발했다.
```

## 10. 담당자별 최종 요약

| 담당자 | 사용자 기능 | AI 기능 | 관리자 기능 |
| --- | --- | --- | --- |
| A | 인증, 회원, 프로필, 설정 | 이력서 요약, 기술스택 추출, 프로필 완성도 진단 | 회원 관리, 동의 이력 관리 |
| B | 지원 건, 공고문, 공고 분석, 기업 분석 | 공고 분석, 필수/우대조건 추출, 기업 요약 | 지원 건 관리, 공고/기업 분석 로그 관리 |
| C | 스펙 비교, 취업 분석, 대시보드 | 적합도 분석, 부족 역량 추천, 학습/자격증 추천 | 분석 통계, 적합도 분석 관리 |
| D | 가상 면접, 면접 리포트 | 질문 생성, 꼬리 질문, 답변 평가, 리포트 생성 | 면접 세션 관리, 면접 리포트 관리 |
| E | 첨삭, 결제, 크레딧 | 답변 첨삭, 자소서 첨삭, 이력서 표현 개선 | 결제 관리, 크레딧 관리, 첨삭 로그 관리 |
| F | 커뮤니티, 고객센터, 알림, 공지 | 후기 요약, 질문 추출, 게시글 추천, 문의 답변 초안 | 게시판/신고, 공지/FAQ/문의 관리 |

## 11. 공통 영역

공통 영역의 Owner는 팀장이다. 아래 영역은 기능 담당자가 임의로 수정하지 않고, 수정 필요 사유와 영향 범위를
공유한 뒤 팀장 승인 또는 팀 합의 후 변경한다.

```text
frontend/src/app/routes.ts
frontend/src/admin/routes.ts
frontend/src/features/home
frontend/src/app/components/layout
frontend/src/app/components/ui
frontend/src/app/components/media
frontend/src/app/components/upload
frontend/src/app/lib
frontend/src/admin/features/prompts

backend/src/main/java/com/careertuner/common
backend/src/main/java/com/careertuner/home
backend/src/main/java/com/careertuner/ai/common
backend/src/main/java/com/careertuner/ai/prompt
backend/src/main/java/com/careertuner/admin/prompt
backend/src/main/resources/db/schema.sql
backend/src/main/resources/db/data.sql
backend/src/main/resources/application.yaml
backend/build.gradle
시스템 로그
프런트 공통 구조
백엔드/DB 공통 구조
AI 공통 구조
```

`frontend/src/admin/features/logs`는 시스템 로그 작업을 시작할 때 생성할 예정인 공통 경로다.

공통 영역은 기능 구현 속도를 위해 필요하지만, 동시에 충돌이 가장 자주 나는 곳이다.
가능하면 각 기능 담당자는 자기 기능 폴더 안에서 먼저 해결하고, 공통화가 필요할 때만 팀장과 합의해 이동한다.

예외:

- 단순 오타, 주석, 명백한 문서 오류 수정은 바로 반영할 수 있다.
- 라우팅, 공통 컴포넌트, 공통 API, DB 구조, 인증/권한, AI 프롬프트, 로그 구조에 영향을 주는 변경은 반드시 합의 후 진행한다.

## 12. 기존 관리자 골격 담당 지정

현재 저장소에 있는 관리자 골격은 아래처럼 담당자를 지정한다. 분석 통계 명칭은 `analytics`로 통일한다.
따라서 현재 `backend/src/main/java/com/careertuner/admin/analysis`는 신규 구현 시
`backend/src/main/java/com/careertuner/admin/analytics`로 정리한다.

| 관리자 골격 | 담당 | 사용 목적 |
| --- | --- | --- |
| `backend/src/main/java/com/careertuner/admin/auth` | A | 관리자 로그인·세션·권한 보조 |
| `backend/src/main/java/com/careertuner/admin/profile` | A | 사용자 프로필 조회와 운영 확인 |
| `backend/src/main/java/com/careertuner/admin/settings` | A | 계정·알림 설정 운영 확인 |
| `backend/src/main/java/com/careertuner/admin/home` | 팀장 | 관리자 홈 또는 운영 홈 공통 영역 |
| `backend/src/main/java/com/careertuner/admin/billing` | E | 결제/구독 운영 화면 보조 |
| `backend/src/main/java/com/careertuner/admin/legal` | F | 약관·정책 콘텐츠 관리 |
| `backend/src/main/java/com/careertuner/admin/company` | F | 서비스 회사/브랜드 소개 관리 |
| `backend/src/main/java/com/careertuner/admin/serviceinfo` | F | 서비스 소개 콘텐츠 관리 |
| `backend/src/main/java/com/careertuner/admin/analysis` → `backend/src/main/java/com/careertuner/admin/analytics` | C | 분석 통계와 대시보드 AI 분석 결과 관리 |

# Feature Module Structure

이 문서는 개발자가 실제 폴더와 담당 범위를 어떻게 나눌지 정리한다.
`PRODUCT_STRUCTURE.md`가 사용자 관점의 지도라면, 이 문서는 코드 소유권과 충돌 방지를 위한 지도다.
6명 담당자별 기능, AI, DB 분담의 상세 내용은 `TEAM_WORK_DISTRIBUTION.md`를 함께 본다.
담당별 자체 LLM의 학습·서빙·검증·fallback·산출물 기준은 `planning/담당별_자체LLM_운영안.md`를 따른다.

## 1. 구조 원칙

- 애플리케이션 런타임 소스는 최상위 `backend`, `frontend`에서 관리하고, 기획·규칙은 `docs`와 루트 설정 문서에 둔다.
- 관리자 프런트는 별도 앱으로 분리하지 않고 `frontend/src/admin` 아래에 둔다.
- 사용자 기능은 `frontend/src/features/<feature>`에 둔다.
- 관리자 기능은 `frontend/src/admin/features/<feature>`에 둔다.
- 백엔드는 도메인 단위로 `backend/src/main/java/com/careertuner/<domain>`에 둔다.
- MyBatis XML은 `backend/src/main/resources/mapper/<domain>`에 둔다.
- 공통 인프라는 `common`, AI 교차 기반은 `ai/common`에 둔다. `ai/prompt`는 향후 공통 프롬프트 엔진의 예약 경계이며 현재는 `package-info.java`만 있다. 실행 프롬프트는 각 기능 도메인에 둔다.
- 사용자 기능이 릴리스 완료로 표시되려면 관련 관리자 화면과 관리자 API도 같은 릴리스 기준으로 완료해야 한다.
- 공통 영역의 Owner는 팀장이며, 라우팅·공통 컴포넌트·공통 API·DB·인증/권한·AI 프롬프트 공통 엔진·공통 로그 구조 변경은 팀장 승인 또는 팀 합의 후 진행한다.

## 2. 기능 ID 표

| 기능 ID | 사이트 메뉴 | 사용자 프런트 | 연관 백엔드 도메인 | 출시 범위 |
| --- | --- | --- | --- | --- |
| `auth` | 로그인/회원가입 | `auth` | `auth`, `user` | 1차 |
| `home` | 홈 | `home` | `home` | 1차 |
| `dashboard` | 대시보드 | `dashboard` | `dashboard` | 1차 |
| `profile` | 내 프로필 | `profile` | `profile` | 1차 |
| `catalog` | 직무·자격 카탈로그 | `catalog` | `catalog` | 2차 |
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

## 2.1 분석 계열 명명 규칙

분석 계열은 의미가 비슷해 보여도 제품 영역과 운영 영역을 구분한다. 새 파일, 라우트, API, DB, 문서를 추가할 때 아래 이름을 우선한다.

| 개념 | 사용 위치 | 표준 이름 | 예 |
| --- | --- | --- | --- |
| 사용자 취업 분석 | 사용자 메뉴, 사용자 라우트, 장기 경향 분석 도메인 | `analysis` | `frontend/src/features/analysis`, `backend/src/main/java/com/careertuner/analysis`, `/api/analysis/summary` |
| 관리자 분석 통계 | 관리자 운영 통계, 집계 지표, 대시보드 AI 분석 결과 관리 | `analytics` | `frontend/src/admin/features/analytics`, `backend/src/main/java/com/careertuner/admin/analytics`, `/api/admin/analytics/summary` |
| 적합도 분석 프런트 경로 | 사용자/관리자 프런트 폴더와 라우트 세그먼트 | `fit-analysis` | `frontend/src/admin/features/fit-analysis`, `/admin/fit-analysis` |
| 적합도 분석 REST 컬렉션 | API에서 여러 적합도 분석 결과를 다루는 컬렉션 리소스 | `fit-analyses` | `/api/fit-analyses`, `/api/admin/fit-analyses` |
| 적합도 분석 백엔드 패키지 | Java 패키지와 MyBatis mapper 경로 | `fitanalysis` | `backend/src/main/java/com/careertuner/fitanalysis`, `backend/src/main/resources/mapper/fitanalysis` |
| 적합도 분석 DB | 테이블, 컬럼, SQL alias | `fit_analysis` | `fit_analysis`, `fit_analysis_id` |
| 적합도 분석 코드 식별자 | Java/TypeScript 타입, 클래스, 메서드 | `FitAnalysis`, `fitAnalysis`, `fitAnalyses` | `FitAnalysisController`, `fitAnalyses` |

금지 또는 회피 이름:

- `analyse`, `fit-analyse`, `fit-analyse(s)`: 영국식 동사 표기는 사용하지 않는다.
- `admin/analysis`: 관리자 분석 통계는 `analytics`만 사용한다. 사용자 취업 분석과 섞지 않는다.
- `analysis-statistics`, `analysisStats`: 관리자 통계 의미를 새로 표현하지 말고 `analytics`로 통일한다.
- `fitAnalysis`를 URL 또는 폴더 세그먼트로 쓰지 않는다. URL/프런트 폴더는 kebab-case인 `fit-analysis`를 쓴다.

프롬프트 운영 경로도 같은 규칙을 따른다. 적합도 분석 프롬프트는 `frontend/src/admin/features/prompts/fit-analysis`와
`backend/src/main/java/com/careertuner/admin/prompt/fitanalysis`를 사용한다. 관리자 분석 통계 프롬프트는
`frontend/src/admin/features/prompts/analytics`와 `backend/src/main/java/com/careertuner/admin/prompt/analytics`를 사용한다.

### 관리자 분석 인접 영역 구분 (analytics / dashboard / home)

`analytics`로 "통일"한다는 규칙은 **관리자 통계 개념의 이름**을 통일하라는 뜻이지(예: `admin/analysis`, `analysisStats` 금지), 관리자 화면을 하나로 합치라는 뜻이 아니다. C의 관리자 영역은 역할이 달라 아래 셋을 구분해 둔다. 사용자 영역의 `home`/`dashboard`/`analysis`와 운영 측면에서 짝을 이룬다.

| 폴더 | 역할(왜 이 이름인가) | 대표 경로 |
| --- | --- | --- |
| `admin/analytics` | 분석·AI **깊은 통계**(점수 분포, 부족 역량, 사용량 추이). 사용자 `analysis`의 운영 통계 짝. | `/api/admin/analytics/summary` |
| `admin/dashboard` | 도메인 횡단 **운영 현황 카운트**(회원/지원 건/분석/면접/AI). 사용자 `dashboard`의 운영 짝, 운영자 랜딩. | `/api/admin/dashboard/overview` |
| `admin/home` | 운영자 **작업 진입점/대기 큐**(분석 실패·미분석·최근 분석) + 바로가기. 사용자 `home`의 운영 짝. | `/api/admin/home/summary` |

이 표는 절대 규칙이 아니라 "왜 이런 이름이 있는지" 혼선을 줄이기 위한 기록이다. 개발하며 더 나은 경계가 보이면 사유를 남기고 이름/범위를 조정한다.

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

모든 표준 하위 계층을 빈 폴더로 미리 만들 필요는 없다. 실제 코드가 생길 때 표준 레이어 이름을 사용하고,
경로 표만으로 구현 완료 여부를 판단하지 않는다.

## 5. 담당자별 확정 분담

아래 표는 **목표 기능의 소유권**을 정한다. 현재 구현 여부나 현재 기본 provider를 뜻하지 않으며, 실행 상태는
각 모듈 README와 소스를 기준으로 판단한다.

| 담당자 | 사용자 기능 | AI 기능 | 관리자 기능 |
| --- | --- | --- | --- |
| A | 인증, 회원, 프로필, 설정, 동의 | 이력서 요약, 기술스택 추출, 프로필 완성도 진단 | 회원 관리, 동의 이력 관리 |
| B | 지원 건, 공고문, 공고 분석, 기업 분석 | 공고 분석, 필수/우대조건 추출, 기업 요약 | 지원 건 관리, 공고/기업 분석 로그 관리 |
| C | 홈, 스펙 비교, 취업 분석, 대시보드 | 적합도 분석, 부족 역량 추천, 학습/자격증 추천, 커리어 전략 추천, 대시보드 AI 분석 출력 | 관리자 홈, 분석 통계, 적합도 분석 관리 |
| D | 가상 면접, 면접 리포트, 면접 파일 | 질문 생성, 꼬리 질문, 답변 평가, 리포트 생성 | 면접 세션 관리, 면접 리포트 관리 |
| E | 첨삭, 결제, 크레딧 | 답변 첨삭, 자소서 첨삭, 이력서 표현 개선 | 결제 관리, 크레딧 관리, 첨삭 로그 관리 |
| F | 커뮤니티, 고객센터, 알림, 공지, 회사/법적 문서 | 후기 요약, 질문 추출, 게시글 추천, 문의 답변 초안 | 게시판/신고, 공지/FAQ/문의, 알림 관리 |

담당자별 상세 사용자 기능, 예시 AI 출력, 주요 DB, 포트폴리오 설명 포인트는
`TEAM_WORK_DISTRIBUTION.md`에 둔다. 이 문서에서는 충돌을 줄이기 위한 실제 경로만 유지한다.

현재 AI 런타임의 빠른 지도는 다음과 같다. 같은 담당 안에서도 작업 종류에 따라 기본 체인이 다르므로
이 표를 단일 전역 순서로 해석하지 않는다.

| 담당 | 현재 런타임 연결 | 기본/안전망 요약 |
| --- | --- | --- |
| A | 프로필 평가 LoRA 선택 경로, Ollama 이력서 구조화 | 평가 LoRA는 기본 비활성; Claude → OpenAI → 규칙. 이력서 구조화는 로컬 Ollama → hosted → 최소 추출 |
| B | Ollama 공고·기업 분석, Claude/OpenAI 어댑터 | 공고는 LOCAL 우선, 기업은 OpenAI 우선; 최종 `self-rules-v1` |
| C | 적합도 설명용 커리어 전략 3B 선택 경로 | OSS는 설정 시 사용; Claude → OpenAI → 결정적 mock |
| D | 면접 생성/채점 OSS 선택 경로, Claude/OpenAI, Realtime | 생성은 Claude/OpenAI/mock, 채점은 OpenAI 기본·OSS 선택 가능 |
| E | 버전 고정 첨삭 3B와 repair | OpenAI 기본; self 선택 시 자체 3B → Anthropic → OpenAI |
| F | Ollama 텍스트·이미지 검열, 챗봇/지원 초안 | 로컬 모델 우선; 기능별로 Claude/OpenAI/mock 안전망 사용 |

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
frontend/src/features/home
frontend/src/features/analysis
frontend/src/features/dashboard
frontend/src/features/catalog
frontend/src/features/applications/components/FitAnalysisPanel.tsx
frontend/src/features/applications/components/StrategyPanel.tsx
frontend/src/features/applications/components/LearningRecommendationPanel.tsx
frontend/src/admin/features/home
frontend/src/admin/features/analytics
frontend/src/admin/features/dashboard
frontend/src/admin/features/fit-analysis

backend/src/main/java/com/careertuner/home
backend/src/main/java/com/careertuner/catalog
backend/src/main/java/com/careertuner/fitanalysis
backend/src/main/java/com/careertuner/analysis
backend/src/main/java/com/careertuner/dashboard
backend/src/main/java/com/careertuner/admin/home
backend/src/main/java/com/careertuner/admin/analytics
backend/src/main/java/com/careertuner/admin/dashboard
backend/src/main/java/com/careertuner/admin/fitanalysis
```

홈은 공개 진입점이지만 기본 대시보드와 준비 현황 요약을 포함하므로 C가 담당한다.

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
 │   ├─ budget/             전체 체인 시간예산
 │   ├─ gpu/                공유 GPU 동시성 permit
 │   ├─ model/              provider tier와 사용자 모델 선택
 │   ├─ ollama/             endpoint probe/fallback
 │   └─ settings/           DB 기반 런타임 설정 조회
 └─ prompt/                 공통 프롬프트 엔진의 예약 경계(현재 package-info만 유지)
```

도메인별 AI 서비스는 각자 자기 도메인 안에 둔다.

```text
profile/ai
applicationcase/service                    B 공고·기업 분석 실행 체인
fitanalysis/ai · analysis/ai · dashboard/ai
interview/service · interview/ai
correction/ai
community/moderation · support/chatbot · ai/chat
```

자체 LLM provider도 같은 원칙을 따른다. 공통 `ai/common`은 provider tier, 시간예산, GPU permit,
Ollama endpoint 해석 같은 교차 기반만 담당하고, transport·prompt builder·JSON schema validator·fallback 정책은
각 담당 도메인 안에 둔다. F 커뮤니티 검열의 Ollama client는 `community/moderation`에, 챗봇 provider 체인은
`ai/chat`과 `support/chatbot`에 구현되어 있다. 공통 계약을 확장할 때는 8번 공통 파일 규칙을 적용한다.

재현 가능한 소형 학습·평가 코드와 fixture는 현재 존재하는 `ml/career-strategy-llm`, `ml/correction-llm`,
`ml/interview-finetune`, `ml/interview-nonverbal`, `ml/job-posting-worker`, `ml/ncs-catalog`에 둔다.
장문 실험 보고서는 `docs/ai-reports`, raw output·benchmark artifact·운영 모델 산출물은 `docs/ai-artifacts`
서브모듈에 둔다. 대용량 adapter/GGUF와 반복 생성 결과를 메인 저장소나 임의의 새 `ml/<domain>` 폴더에
커밋하지 않는다. 상세 경계는 `AI_REPOSITORY_BOUNDARIES.md`를 따른다.

도메인별 프롬프트와 프롬프트 관리 화면도 같은 원칙을 따른다. 현재 `ai/prompt`에는 공통 엔진이나 타입이
구현되어 있지 않고 `package-info.java`만 있다. 실행 중인 prompt builder·catalog·validator와 기능별 프롬프트
내용은 각 담당자의 도메인 하위 또는 해당 도메인의 `resources/prompts`에 둔다.

```text
backend/src/main/java/com/careertuner/<domain>/ai/prompt/
backend/src/main/java/com/careertuner/admin/prompt/<feature>/
frontend/src/admin/features/prompts/<feature>/
```

예를 들어 B는 공고·기업 분석 실행 체인의 프롬프트를, C는 `fitanalysis`·`analysis`·`dashboard`의 프롬프트를
담당한다. 미래에 실제 공통 프롬프트 엔진이나 타입을 `ai/prompt`에 도입할 때만 8번 공통 파일 규칙을 따른다.

## 8. 공통 파일 소유권

아래 파일과 경로는 팀장 Owner의 공통 관리 영역이다. 이는 구현 담당을 팀장에게 배정한다는 뜻이 아니라,
여러 담당자가 함께 쓰는 기반을 바꿀 때 충돌을 막기 위한 승인·합의 규칙이다.
기능 담당자가 수정해야 할 때는 수정 필요 사유, 영향 범위, 변경 파일, 관련 기능을 공유한 뒤
팀장 승인 또는 팀 합의 후 반영한다.

```text
frontend/src/app/routes.ts
frontend/src/admin/routes.ts
frontend/src/app/components/layout/Header.tsx
frontend/src/app/components/layout/Footer.tsx
frontend/src/app/lib/api.ts
frontend/src/app/components/media
frontend/src/app/components/upload
frontend/src/admin/features/prompts        루트 셸만 공통, 하위 <feature>는 담당자 소유

backend/src/main/java/com/careertuner/common
backend/src/main/resources/db/schema.sql
backend/src/main/resources/db/data.sql
backend/build.gradle
backend/src/main/resources/application.yaml
backend/src/main/java/com/careertuner/common/config/SecurityConfig.java
backend/src/main/java/com/careertuner/ai/common
backend/src/main/java/com/careertuner/ai/prompt       예약 경계(현재 package-info만, 향후 공통 엔진)
backend/src/main/java/com/careertuner/admin/prompt    루트 셸만 공통, 하위 <feature>는 담당자 소유
```

전역 `frontend/src/admin/features/logs`를 기본 생성하지 않는다. 기능별 운영 로그가 필요하면
`frontend/src/admin/features/<feature>/logs`, `backend/src/main/java/com/careertuner/admin/<domain>/log`처럼
각 담당자 하위 폴더에 둔다. 공통 로그 스키마, 공통 수집기, 전역 로그 뷰어가 필요할 때만 8번 공통 규칙을 따른다.

확정 소유권과 운영 규칙:

```text
Owner: 팀장
수정 전 공유: 수정 사유, 영향 범위, 변경 파일, 관련 기능
합의 필수: 라우팅, 공통 컴포넌트, 공통 API, DB 구조, 인증/권한, AI 프롬프트 공통 엔진, 공통 로그 구조
예외: 단순 오타, 주석, 명백한 문서 오류
```

`frontend/src/features/service`는 사용자 프런트의 서비스 소개 기능이고,
`backend/src/main/java/com/careertuner/serviceinfo`는 같은 기능의 백엔드 도메인이다.
프런트 폴더명을 `serviceinfo`로 새로 만들지 않는다.

## 9. 공유·관리자 패키지 소유권

`notification`, `file`, `credit`, `consent`는 더 이상 미래 골격이 아니라 controller/service/mapper와 DB 계약을
가진 런타임 도메인이다. `ai/common`에도 시간예산·GPU permit·provider tier·Ollama endpoint 기반이 구현되어
있고, `ai/prompt`는 공통 엔진을 위한 예약 경계만 유지한다. 관리자 패키지는 기능별 구현량이 서로 다르므로
아래 표는 **소유권과 표준 경로**만 나타내며, 완료 여부는 해당 소스와 사용자/관리자 API를 함께 확인한다.
팀장은 8번 공통 파일 소유권에 해당하는 공통 기반 변경을 관리한다.

| 표준 경로 | 담당 | 비고 |
| --- | --- | --- |
| `notification` | F | 알림 도메인과 알림 설정 API |
| `file` | D | 파일 도메인. 공통 업로드/미디어 컴포넌트 수정은 8번 규칙 적용 |
| `credit` | E | 크레딧 장부와 사용 내역 |
| `consent` | A | 사용자 동의 이력 |
| `ai/common` | 8번 공통 규칙 | provider tier·시간예산·GPU permit·Ollama endpoint·런타임 설정. 도메인 AI 서비스는 각 담당자 소유 |
| `ai/prompt` | 8번 공통 규칙 | 현재 공통 엔진의 예약 경계. 기능별 프롬프트는 각 담당자 하위 폴더 |
| `admin/user` | A | 회원 관리 |
| `admin/auth` | A | 관리자 인증·권한 보조 |
| `admin/profile` | A | 사용자 프로필 운영 확인 |
| `admin/settings` | A | 계정·설정 운영 확인 |
| `admin/consent` | A | 동의 이력 관리 |
| `admin/home` | C | 관리자 홈, 기본 대시보드, 준비 현황 요약 |
| `admin/jobanalysis` | B | 공고 분석 관리 |
| `admin/companyanalysis` | B | 기업 분석 관리 |
| `admin/fitanalysis` | C | 적합도 분석 관리 |
| `admin/analytics` | C | 분석 통계와 대시보드 AI 분석 결과 관리 |
| `admin/payment` | E | 결제 관리 |
| `admin/billing` | E | 구독·청구 관리 |
| `admin/credit` | E | 크레딧 지급·차감·환불 예외 관리 |
| `admin/plan` | E | 요금제 관리 |
| `admin/aiusage` | E | AI 사용량·비용 조회 화면 |
| `admin/legal` | F | 약관·정책 콘텐츠 관리 |
| `admin/company` | F | 서비스 회사/브랜드 소개 관리 |
| `admin/serviceinfo` | F | 서비스 소개 콘텐츠 관리 |
| `admin/report` | F | 커뮤니티 신고·운영 리포트 |
| `admin/notice` | F | 공지사항 관리 |
| `admin/faq` | F | FAQ 관리 |
| `admin/notification` | F | 알림 운영 관리 |

프롬프트와 로그는 전역 기능 하나로 몰아두지 않는다. 기능별 프롬프트나 운영 로그가 필요하면 아래처럼
각 담당자의 하위 폴더에 만든다.

```text
frontend/src/admin/features/prompts/<feature>
backend/src/main/java/com/careertuner/admin/prompt/<feature>
frontend/src/admin/features/<feature>/logs
backend/src/main/java/com/careertuner/admin/<domain>/log
```

예:

| 하위 폴더 | 담당 |
| --- | --- |
| `prompts/profile`, `admin/prompt/profile` | A |
| `prompts/job-analysis`, `admin/prompt/jobanalysis` | B |
| `prompts/company-analysis`, `admin/prompt/companyanalysis` | B |
| `prompts/fit-analysis`, `admin/prompt/fitanalysis` | C |
| `prompts/interview`, `admin/prompt/interview` | D |
| `prompts/correction`, `admin/prompt/correction` | E |
| `prompts/community`, `admin/prompt/community` | F |
| `prompts/support`, `admin/prompt/support` | F |

분석 통계 백엔드 표준 경로는 `backend/src/main/java/com/careertuner/admin/analytics`다.
관리자 분석 통계 패키지는 위 표준 경로만 사용한다.

일부 관리자 표준 경로에는 여전히 `package-info.java`만 있을 수 있다. 이 경우 표의 존재를 구현 완료 근거로
쓰지 않으며, 실제 controller/service/mapper, 프런트 라우트, 권한 테스트가 갖춰졌을 때만 완료로 판단한다.

# 기능 소유권 구조

CareerTuner는 기능 단위로 작업을 배분한다. 각 담당자는 해당 기능의 사용자 프런트엔드, 사용자 백엔드, 관리자 프런트엔드, 관리자 백엔드를 함께 책임진다.
사용자 기능이 릴리스에 포함되면 관련 관리자 화면과 관리자 API도 같은 릴리스 완료 기준에 포함한다.

이 문서의 런타임 경로는 기능이 성숙했을 때 따라야 할 표준 경로다. 현재 구현 여부는 실제 소스와
`backend/README.md`, `frontend/README.md`에서 확인한다. 관리자에게 별도 배포/보안/릴리스 경계가 필요하다고
팀이 명시적으로 결정하기 전에는 최상위에 별도 관리자 프런트엔드 앱을 만들지 않는다.

관련 문서:

- `PRODUCT_STRUCTURE.md` — 사용자 관점의 제품/메뉴 구조.
- `FEATURE_MODULE_STRUCTURE.md` — 런타임 폴더 구조 상세, 소유권, AI 분담, 공유 파일 규칙.
- `TEAM_WORK_DISTRIBUTION.md` — 6인 수직 분담, AI 책임, 주요 DB 소유권.
- `planning/자체LLM_팀_도입안.md` — 팀 전체 자체 LLM 도입 근거와 공통 결정.
- `planning/담당별_자체LLM_운영안.md` — A~F 담당별 자체 모델 운영·검증·fallback·산출물 기준.

## 1. 런타임 소스 매핑

| 작업 영역 | 런타임 경로 |
| --- | --- |
| 사용자 프런트엔드 | `frontend/src/features/<feature>/` |
| 관리자 프런트엔드 | `frontend/src/admin/features/<feature>/` |
| 사용자 백엔드 | `backend/src/main/java/com/careertuner/<backend-domain>/` |
| 사용자 MyBatis XML | `backend/src/main/resources/mapper/<backend-domain>/` |
| 관리자 백엔드 | `backend/src/main/java/com/careertuner/admin/<backend-domain>/` |
| 관리자 MyBatis XML | `backend/src/main/resources/mapper/admin/<backend-domain>/` |

현재 보이는 앱에는 초기 프로토타입 페이지가 `frontend/src/app/pages`에 남아 있다. 기능이 성숙해지면 페이지 내부 구현을 `frontend/src/features/<feature>`로 옮기고, `frontend/src/app`에는 라우트 수준의 연결만 남긴다.
관리자 라우트 수준의 연결은 `frontend/src/admin`에 유지하고, 메인 라우터에서 `/admin/**` 아래로 마운트한다.

## 2. 기능 맵

| 기능 폴더 | 사용자 메뉴 범위 | 연관 백엔드 도메인 패키지 |
| --- | --- | --- |
| `auth` | 로그인, 회원가입, 소셜 로그인, 토큰 세션 | `auth`, `user` |
| `home` | 공개 홈, 온보딩 진입, 기본 준비 현황 요약 | `home` |
| `dashboard` | 대시보드 요약, 알림 | `dashboard` |
| `profile` | 기본 정보, 이력서, 자기소개서, 경력/프로젝트, 기술스택, 자격증/학력 | `profile` |
| `applications` | 지원 건, 공고 업로드, 분석 결과, 적합도 비교, 전략, 학습/자격증 추천, 기록 | `applicationcase`, `jobposting`, `jobanalysis`, `companyanalysis`, `fitanalysis` |
| `interview` | 면접 모드, 질문, 연습, 음성, 아바타, 평가, 리포트 | `interview` |
| `correction` | 답변, 자기소개서, 이력서, 포트폴리오 설명 첨삭 | `correction` |
| `analysis` | 지원 경향, 부족 역량, 직무 준비도, 면접 점수 추이, 추천 방향, 대시보드 AI 분석 결과 | `analysis` |
| `community` | 취업 후기, 면접 후기, 직무별 질문, 전략 게시판 | `community` |
| `billing` | 요금제, AI 사용량, 크레딧 충전, 결제 내역 | `payment`, `billing`, `credit` |
| `settings` | 계정, 개인정보, AI 데이터 동의, 알림 | `settings`, `consent`, `notification` |
| `service` | 기능 소개, 서비스 소개, 공개 서비스 내비게이션 | `serviceinfo` |
| `support` | 고객센터, 사용자 가이드, FAQ, 공지, 문의 | `support` |
| `company` | 서비스/회사 소개, 팀, 채용, 블로그, 보도자료, 소셜 채널 | `company` |
| `legal` | 약관, 개인정보처리방침, AI 데이터 동의 문서, 저작권 정책 | `legal`, `consent` |
| `notification` | 알림, 알림 설정 | `notification` |
| `file` | 업로드/저장소 내부 구현 | `file` |
| `ai` | 프롬프트, AI 프로바이더 내부 구현 | `ai` |

연관 백엔드 도메인은 해당 화면의 대표 호출 범위이며 전체 의존성 목록이나 폴더 소유권을 의미하지 않는다.
소유권과 교차 기능 예외는 `FEATURE_MODULE_STRUCTURE.md`와 `TEAM_WORK_DISTRIBUTION.md`를 기준으로 한다.

### 교차 기능 소유권

| 영역 | 주 담당과 협업 경계 |
| --- | --- |
| `frontend/src/features/applications` | B가 화면 셸·공고·기업 분석을, C가 적합도·전략·학습 추천 컴포넌트를 담당한다. |
| `settings`와 `notification` | A가 설정 화면을 통합하고, F가 알림 도메인·API를 담당한다. |
| `legal`과 `consent` | F가 약관·정책 콘텐츠를, A가 사용자 동의 이력과 API를 담당한다. |
| 프로필 원문과 첨삭 | A가 원문 데이터를, E가 첨삭 요청·결과를 담당한다. |
| 면접 평가와 답변 첨삭 | D가 면접 평가·리포트를, E가 별도 첨삭 이력을 담당한다. |
| `ai_usage_log` | 각 도메인이 공통 규약으로 기록하고, E가 사용량·결제 화면을 담당한다. 공통 로깅과 스키마는 공통 영역이다. |

공통 라우팅, 공통 컴포넌트, 공통 API, DB 구조, 인증/권한, AI 프롬프트 공통 엔진, 공통 로그 구조의 Owner는 팀장이다.
기능 담당자가 공통 영역을 수정해야 할 때는 수정 사유와 영향 범위를 공유하고 팀장 승인 또는 팀 합의 후 반영한다.
기능별 프롬프트와 기능별 운영 로그는 각 담당자의 하위 폴더에 둔다.

## 3. 백엔드 패키지 규칙

사용자 API:

```text
backend/src/main/java/com/careertuner/<domain>/
 ├─ controller/
 ├─ service/
 ├─ mapper/
 ├─ domain/
 └─ dto/
```

관리자 API:

```text
backend/src/main/java/com/careertuner/admin/<domain>/
 ├─ controller/
 ├─ service/
 ├─ mapper/
 ├─ domain/
 └─ dto/
```

MyBatis XML 파일은 동일한 도메인명을 따른다:

```text
backend/src/main/resources/mapper/<domain>/
backend/src/main/resources/mapper/admin/<domain>/
```

## 4. 프런트엔드 기능 규칙

사용자 프런트엔드:

```text
frontend/src/features/<feature>/
 ├─ pages/
 ├─ components/
 ├─ api/
 ├─ hooks/
 └─ types/
```

관리자 프런트엔드:

```text
frontend/src/admin/features/<feature>/
 ├─ pages/
 ├─ components/
 ├─ api/
 ├─ hooks/
 └─ types/
```

관리자 전용 레이아웃, 라우트 가드, 내비게이션, 공용 관리자 UI는 아래에 둔다:

```text
frontend/src/admin/
 ├─ components/
 ├─ features/
 ├─ hooks/
 ├─ lib/
 ├─ pages/
 └─ routes.ts
```

버튼, 다이얼로그, 테이블, API 클라이언트 헬퍼 같은 공용 기본 요소는 관리자 전용이 아닌 한
기존 공통 프런트엔드 경로(`frontend/src/app/components/ui`, `frontend/src/app/lib`)에 유지한다.

작업이 시작되면 모든 기능 폴더는 아래 형태를 유지해야 한다:

```text
pages/ components/ api/ hooks/ types/
```

리포지토리는 기능 담당자가 새로운 로컬 구조를 임의로 만들지 않고 바로 작업을 시작할 수 있도록 이 폴더들을 명시적인 플레이스홀더로 유지한다.
서비스 소개는 프런트엔드에서 `frontend/src/features/service`를, 백엔드 도메인 패키지로는 `serviceinfo`를 사용한다.
`frontend/src/features/serviceinfo` 폴더를 새로 만들지 않는다.

## 5. 관리자 앱 분리 결정 규칙

별도 관리자 프런트엔드는 아래 중 하나 이상이 실제 요구사항이 될 때만 재검토한다:

- 관리자를 별도 도메인/네트워크 경계에 배포해야 한다.
- 관리자 인증/세션 정책이 사용자 SPA 셸을 안전하게 공유할 수 없다.
- 관리자 릴리스 주기가 사용자 프런트엔드와 독립적이어야 한다.
- 관리자 번들 크기나 의존성이 사용자 앱에 악영향을 줄 만큼 커진다.

그 전까지는 단일 Vite React 앱이 라우팅, 인증, API 클라이언트, 디자인 토큰, 빌드 도구를 더 단순하게 유지한다.

## 6. 최소 인수인계 체크리스트

기능 담당자가 기능을 해당 릴리스 범위에서 통합 준비 완료로 표시하기 전에, 아래 항목 중 그 릴리스에 포함된 범위를 갖춰야 한다:

- 사용자 라우트/페이지 상태
- 사용자 API 클라이언트
- 사용자 백엔드 controller/service/mapper/dto/domain
- 관련 관리자 라우트/페이지 상태
- 관련 관리자 백엔드 controller/service/mapper/dto/domain
- 영속화가 필요한 경우 MyBatis XML과 샘플 데이터
- 기본 정상 흐름(happy-path)과 실패 상태 UI
- 관리자 기능이 있는 경우 관리자 엔드포인트의 역할/권한 동작
- 기능이 AI를 호출하는 경우 AI 프롬프트/사용량 로깅 동작

## 7. 관리자 골격 담당

현재 저장소의 추가 관리자 골격은 아래처럼 담당한다. 분석 통계 명칭은 `analytics`로 통일한다.
분석 계열의 레이어별 이름은 `FEATURE_MODULE_STRUCTURE.md`의 "분석 계열 명명 규칙"을 따른다.

| 관리자 골격 | 담당 |
| --- | --- |
| `backend/src/main/java/com/careertuner/admin/auth` | A |
| `backend/src/main/java/com/careertuner/admin/profile` | A |
| `backend/src/main/java/com/careertuner/admin/settings` | A |
| `backend/src/main/java/com/careertuner/admin/home` | C |
| `backend/src/main/java/com/careertuner/admin/billing` | E |
| `backend/src/main/java/com/careertuner/admin/legal` | F |
| `backend/src/main/java/com/careertuner/admin/company` | F |
| `backend/src/main/java/com/careertuner/admin/serviceinfo` | F |
| `backend/src/main/java/com/careertuner/admin/analytics` | C |

# 기능 소유권 구조

CareerTuner는 기능 단위로 작업을 배분한다. 각 담당자는 해당 기능의 사용자 프런트엔드, 사용자 백엔드, 관리자 프런트엔드, 관리자 백엔드를 함께 책임진다.

런타임 소스 경로가 기준(source of truth)이다. 관리자에게 별도 배포/보안/릴리스 경계가 필요하다고 팀이 명시적으로 결정하기 전에는
최상위에 별도 관리자 프런트엔드 앱을 만들지 않는다.

관련 문서:

- `PRODUCT_STRUCTURE.md` — 사용자 관점의 제품/메뉴 구조.
- `FEATURE_MODULE_STRUCTURE.md` — 런타임 폴더 구조 상세, 소유권, AI 분담, 공유 파일 규칙.
- `TEAM_WORK_DISTRIBUTION.md` — 6인 수직 분담, AI 책임, 주요 DB 소유권.

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

| 기능 폴더 | 사용자 메뉴 범위 | 백엔드 도메인 패키지 |
| --- | --- | --- |
| `auth` | 로그인, 회원가입, 소셜 로그인, 토큰 세션 | `auth`, `user` |
| `home` | 공개 홈, 온보딩 진입 | `home` |
| `dashboard` | 대시보드 요약, 알림 | `dashboard` |
| `profile` | 기본 정보, 이력서, 자기소개서, 경력/프로젝트, 기술스택, 자격증/학력 | `profile` |
| `applications` | 지원 건, 공고 업로드, 분석 결과, 적합도 비교, 전략, 학습/자격증 추천, 기록 | `applicationcase`, `jobposting`, `jobanalysis`, `companyanalysis`, `fitanalysis` |
| `interview` | 면접 모드, 질문, 연습, 음성, 아바타, 평가, 리포트 | `interview` |
| `correction` | 답변, 자기소개서, 이력서, 포트폴리오 설명 첨삭 | `correction` |
| `analysis` | 지원 경향, 부족 역량, 직무 준비도, 면접 점수 추이, 추천 방향 | `analysis` |
| `community` | 취업 후기, 면접 후기, 직무별 질문, 전략 게시판 | `community` |
| `billing` | 요금제, AI 사용량, 크레딧 충전, 결제 내역 | `payment`, `billing` |
| `settings` | 계정, 개인정보, AI 데이터 동의, 알림 | `settings`, `consent`, `notification` |
| `service` | 기능 소개, 서비스 소개, 공개 서비스 내비게이션 | `serviceinfo` |
| `support` | 고객센터, 사용자 가이드, FAQ, 공지, 문의 | `support` |
| `company` | 서비스/회사 소개, 팀, 채용, 블로그, 보도자료, 소셜 채널 | `company` |
| `legal` | 약관, 개인정보처리방침, AI 데이터 동의 문서, 저작권 정책 | `legal`, `consent` |
| `notification` | 알림, 알림 설정 | `notification` |
| `file` | 업로드/저장소 내부 구현 | `file` |
| `ai` | 프롬프트, AI 프로바이더 내부 구현 | `ai` |

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

기능 담당자가 기능을 통합 준비 완료로 표시하기 전에, 해당 기능은 아래를 갖춰야 한다:

- 사용자 라우트/페이지 상태
- 사용자 API 클라이언트
- 사용자 백엔드 controller/service/mapper/dto/domain
- 관리자 라우트/페이지 상태
- 관리자 백엔드 controller/service/mapper/dto/domain
- 영속화가 필요한 경우 MyBatis XML과 샘플 데이터
- 기본 정상 흐름(happy-path)과 실패 상태 UI
- 관리자 엔드포인트의 역할/권한 동작
- 기능이 AI를 호출하는 경우 AI 프롬프트/사용량 로깅 동작

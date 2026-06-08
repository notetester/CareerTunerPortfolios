# Team Work Distribution

이 문서는 6명 개발자가 CareerTuner를 기능별로 나누어 개발하기 위한 공통 합의안이다.
`PRODUCT_STRUCTURE.md`가 사용자 관점의 제품 구조이고, `FEATURE_MODULE_STRUCTURE.md`가 실제 폴더 구조라면,
이 문서는 각 담당자가 어떤 사용자 기능, AI 기능, 관리자 기능, DB를 함께 책임지는지 정리한다.

담당 지정은 해당 영역을 구현할 때의 소유권을 뜻한다. 사용자 기능이 특정 릴리스에 포함되면 관련 관리자 화면과
관리자 API도 같은 릴리스의 완료 기준에 포함한다.
출시 단계는 `docs/planning/기획.md`, 표준 경로와 교차 기능 경계는 `FEATURE_MODULE_STRUCTURE.md`를 따른다.
각 담당자의 `주요 DB` 목록은 최종 목표 데이터 구조를 포함하므로, 현재 스키마 존재 여부는
`backend/src/main/resources/db/schema.sql`에서 확인한다.

## 1. 분배 기준

CareerTuner의 작업은 기능별 수직 분담을 기준으로 나눈다. 각 담당자는 자기 기능의 사용자 화면,
사용자 API, 관리자 화면, 관리자 API, AI 기능, 주요 DB를 함께 책임진다.
6명 모두가 독립적인 AI 기능을 하나 이상 맡도록 분배하여, 각 기능이 단순 CRUD에 머물지 않고
사용자 가치가 드러나는 흐름까지 완성되도록 한다.

폴더와 도메인 이름은 아래 기준으로 통일한다.

- 사용자 프런트의 서비스 소개 폴더는 `frontend/src/features/service`를 사용한다.
- 백엔드의 서비스 소개 도메인은 기존 패키지명인 `serviceinfo`를 유지한다.
- `frontend/src/features/applications`는 B와 C가 함께 만지는 핵심 화면이므로 컴포넌트 단위 소유권을 명확히 둔다.
- `frontend/src/app/components/media`, `frontend/src/app/components/upload`, `backend/src/main/java/com/careertuner/file`은 D가 주로 쓰지만 공통 영역으로 취급한다.
- `ai/common`, `ai/prompt`의 공통 엔진과 공통 로그 구조는 팀장 Owner의 공통 영역으로 관리한다.
- 기능별 프롬프트와 운영 로그는 각 담당자의 AI 또는 관리자 기능 하위 폴더에 두어 서로 침범하지 않게 한다.

## 2. 6명 수직 분담

```text
A. 회원/프로필/설정 + AI 이력서·스펙 추출
B. 지원 건/공고문/공고·기업 분석 + AI 공고 분석
C. 홈/스펙 비교/취업 분석/대시보드 + AI 커리어 전략 추천
D. 가상 면접/면접 리포트 + AI 면접관
E. 첨삭/결제/크레딧 + AI 답변·자소서 첨삭
F. 커뮤니티/고객센터/공지/알림 + AI 후기 요약·추천·문의 답변
```

이 분배는 각 담당자가 단순 CRUD만 맡지 않고, 사용자 가치가 드러나는 AI 흐름까지 함께 구현하도록 하기 위한 기준이다.

## 3. AI 구현 기능 분배

아래 AI 기능은 모두 구현해야 할 항목이다. 공통 AI 클라이언트, 프롬프트 템플릿, 사용량 로깅은
팀장 Owner의 공통 영역이고, 각 담당자는 자기 도메인 안에 도메인별 AI 서비스를 구현한다.

| 번호 | AI 기능 | 구현 담당 |
| --- | --- | --- |
| 1 | 이력서/프로필 AI 요약 | A |
| 2 | 기술스택 AI 추출 | A |
| 3 | 자기소개서 핵심 키워드 추출 | A |
| 4 | 경력/프로젝트 핵심 키워드 추출 | A |
| 5 | 프로필 완성도 AI 진단 | A |
| 6 | 공고문 AI 분석 | B |
| 7 | 필수 조건 AI 추출 | B |
| 8 | 우대 조건 AI 추출 | B |
| 9 | 담당 업무 AI 요약 | B |
| 10 | 기업 현황 AI 요약 | B |
| 11 | 면접 포인트 AI 추출 | B |
| 12 | 공고-스펙 적합도 AI 분석 | C |
| 13 | 부족 역량 AI 추천 | C |
| 14 | 학습 로드맵 AI 추천 | C |
| 15 | 자격증 AI 추천 | C |
| 16 | 장기 취업 경향 AI 분석 | C |
| 17 | 다음 지원 방향 AI 추천 | C |
| 18 | 대시보드 AI 분석 결과 요약 | C |
| 19 | 예상 면접 질문 AI 생성 | D |
| 20 | 꼬리 질문 AI 생성 | D |
| 21 | AI 면접관 대화 진행 | D |
| 22 | 면접 답변 AI 평가 | D |
| 23 | 면접 리포트 AI 생성 | D |
| 24 | 면접 답변 AI 첨삭 | E |
| 25 | 자기소개서 AI 첨삭 | E |
| 26 | 이력서 표현 AI 개선 | E |
| 27 | 포트폴리오 설명 AI 개선 | E |
| 28 | 사용량 기반 요금제 AI 추천 | E |
| 29 | 커뮤니티 면접 후기 AI 요약 | F |
| 30 | 커뮤니티 게시글 AI 태그 추천 | F |
| 31 | 실제 면접 질문 AI 추출 | F |
| 32 | 사용자 관심 기반 게시글 AI 추천 | F |
| 33 | 부적절 게시글/신고 AI 분류 | F |
| 34 | 고객문의 AI 답변 초안 생성 | F |

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

A는 사용자의 기본 계정과 스펙 원천 데이터를 책임진다. 이 영역의 데이터는 B의 공고 분석, C의 적합도 분석,
D의 면접 질문 생성, E의 첨삭 기능이 공통으로 참조하는 기반 정보이므로 입력 품질, 동의 상태, 버전 관리,
AI 추출 결과의 신뢰도를 함께 관리한다.

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

사용자 기능 상세 책임:

| 영역 | 상세 책임 |
| --- | --- |
| 인증 | 이메일 회원가입, 로그인, 로그아웃, 토큰 재발급, 이메일 인증, 비밀번호 찾기, 소셜 로그인 연결과 해제 |
| 계정 | 내 정보 조회, 이름·이메일·비밀번호 변경, 계정 상태 확인, 탈퇴 또는 비활성화 요청 흐름 |
| 프로필 기본 정보 | 희망 직무, 희망 산업, 선호 지역·근무 형태, 포트폴리오 링크, 언어 능력 같은 지원 기준 정보 관리 |
| 이력서 | 이력서 원문 저장, 경력·프로젝트·학력·자격증·기술스택 입력, AI 추출 결과 확인과 사용자 수정 |
| 자기소개서 | 자기소개서 원문 저장, 문항별 또는 기본 자기소개서 관리, 핵심 키워드 추출 결과 확인 |
| 기술스택 | 직접 입력 기술과 AI 추출 기술의 병합, 중복·오타 정리, 주요 기술과 보조 기술 구분 |
| 동의 | AI 데이터 사용 동의, 필수/선택 동의 이력, 동의 철회 시 AI 기능 제한 처리 |
| 설정 | 알림 수신 설정, 개인정보 표시 범위, 보안 설정, 계정 관련 운영 상태 표시 |

### AI 기능

이 섹션의 AI 기능은 3번 표의 A 담당 항목과 같은 명칭으로 관리한다. A 영역에 새 AI 기능을 추가하거나 삭제할 때는
3번 표와 이 섹션을 함께 수정한다.

```text
이력서/프로필 AI 요약
기술스택 AI 추출
자기소개서 핵심 키워드 추출
경력/프로젝트 핵심 키워드 추출
프로필 완성도 AI 진단
```

AI 기능 상세 책임:

| 3번 번호 | AI 기능 | 주요 입력 | 주요 출력과 활용 |
| --- | --- | --- | --- |
| 1 | 이력서/프로필 AI 요약 | `resume_text`, `career`, `projects`, `education`, `certificates`, `desired_job` | 지원자가 가진 경력·강점·직무 방향을 짧게 요약한다. 프로필 화면, 지원 건 생성 시 기본 스펙 요약, C의 적합도 분석 입력으로 활용한다. |
| 2 | 기술스택 AI 추출 | `resume_text`, `projects`, `portfolio_links`, 사용자가 직접 입력한 `skills` | 기술명 후보, 숙련도 힌트, 중복·오타 정리 후보를 추출한다. 사용자가 확정한 값만 `skills` 기준 데이터로 반영한다. |
| 3 | 자기소개서 핵심 키워드 추출 | `self_intro`, 자기소개서 문항 정보 | 강점 키워드, 가치관, 협업·문제 해결·성과 사례, 문장별 근거를 추출한다. E의 자기소개서 첨삭과 C의 지원 전략 추천에서 참조할 수 있다. |
| 4 | 경력/프로젝트 핵심 키워드 추출 | `career`, `projects`, `resume_text` | 수행 역할, 사용 기술, 문제 해결 방식, 정량 성과, 프로젝트 맥락을 구조화한다. 면접 질문 생성과 공고-스펙 비교의 근거 데이터로 활용한다. |
| 5 | 프로필 완성도 AI 진단 | `user_profile` 전체, 동의 상태, 최근 수정 시점 | 누락 항목, 근거가 약한 항목, 보강 우선순위, 다음 입력 제안을 제공한다. 사용자가 프로필을 보완하도록 안내하고 분석 품질 저하를 줄인다. |

AI 처리 원칙:

```text
AI가 추출한 값은 자동 확정하지 않고 사용자 확인 또는 수정 단계를 둔다.
원문 텍스트와 AI 추출 결과를 구분해 저장한다.
공고 분석, 적합도 분석, 면접, 첨삭에서 재사용할 수 있도록 구조화된 결과를 우선 생성한다.
AI 데이터 사용 동의가 없거나 철회된 사용자는 AI 추출·요약 기능을 실행하지 않는다.
실패한 AI 요청은 사용자에게 재시도 가능한 상태로 표시하고, 공통 사용량 로그 규약을 따른다.
```

예시 결과:

```text
보유 기술: React, Java, MySQL
프로젝트 경험: 게시판, 로그인, REST API 연동, 배포 자동화
경력/프로젝트 키워드: API 연동, 인증 흐름, 데이터 모델링, 성능 개선
자기소개서 키워드: 협업, 문제 해결, 사용자 관점, 꾸준한 개선
프로필 요약: 프런트엔드 구현 경험이 강하고 백엔드 연동 경험도 갖춘 주니어 웹 개발자
부족한 정보: 성과 수치, 배포 경험의 구체적 역할, 장애 대응 사례
보강 제안: 프로젝트별 기여 범위와 수치 성과를 추가하면 적합도 분석과 면접 답변 품질이 좋아진다.
```

### 관리자 기능

```text
회원 목록 조회
회원 검색과 필터
회원 상세 조회
프로필/이력서/자기소개서 운영 확인
이메일 인증 상태 확인
소셜 로그인 연결 상태 확인
AI 데이터 사용 동의 이력 조회
계정 상태 변경 또는 운영 메모
프로필 AI 처리 결과와 실패 이력 확인
```

관리자 완료 기준:

| 영역 | 상세 기준 |
| --- | --- |
| 회원 관리 | 이메일, 이름, 가입일, 상태, 소셜 연동 여부, 최근 로그인 시점을 기준으로 목록과 상세를 조회한다. |
| 프로필 운영 확인 | 이력서 원문, 자기소개서 원문, 경력·프로젝트·기술스택·자격증 입력 상태를 확인할 수 있어야 한다. |
| 동의 이력 | AI 데이터 사용 동의, 선택 동의, 철회 시점과 처리자를 확인할 수 있어야 한다. |
| 상태 변경 | 비활성화, 운영 메모, 위험 계정 표시처럼 운영자가 필요한 조치를 남길 수 있어야 한다. |
| AI 운영 확인 | 요약·추출·진단 요청의 성공/실패, 사용량 로그 연결, 사용자 확정 여부를 확인할 수 있어야 한다. |

### 담당 폴더

```text
frontend/src/features/auth
frontend/src/features/profile
frontend/src/features/settings
frontend/src/admin/features/users
frontend/src/admin/features/consents
frontend/src/admin/features/prompts/profile

backend/src/main/java/com/careertuner/auth
backend/src/main/java/com/careertuner/user
backend/src/main/java/com/careertuner/profile
backend/src/main/java/com/careertuner/profile/ai
backend/src/main/java/com/careertuner/profile/ai/prompt
backend/src/main/java/com/careertuner/settings
backend/src/main/java/com/careertuner/consent
backend/src/main/java/com/careertuner/admin/user
backend/src/main/java/com/careertuner/admin/consent
backend/src/main/java/com/careertuner/admin/prompt/profile
```

`frontend/src/admin/features/prompts`와 `backend/src/main/java/com/careertuner/admin/prompt`의 루트 셸은 공통 영역이다.
A는 그 하위의 `profile` 프롬프트만 담당한다. 공통 AI 클라이언트, 공통 프롬프트 엔진, 공통 로그 수집기는
팀장 Owner 영역이므로 수정 전 합의가 필요하다.

### 주요 DB

```text
users
user_social
email_verification
refresh_token
user_profile
user_profile_version
user_consent
ai_usage_log
```

데이터 책임:

```text
users: 회원 기본 식별자, 로그인 이메일, 계정 상태
user_social: 소셜 로그인 제공자와 외부 계정 연결
email_verification: 이메일 인증 코드와 인증 완료 시점
refresh_token: 재발급 토큰 회전, 만료, 폐기 상태
user_profile: 희망 직무, 학력, 경력, 프로젝트, 기술스택, 자격증, 포트폴리오, 이력서 원문, 자기소개서 원문
user_profile_version: 분석 재현성을 위한 프로필 스냅샷
user_consent: AI 데이터 사용 동의와 철회 이력
ai_usage_log: A 영역 AI 요약·추출·진단 요청의 사용량 기록
```

연동 경계:

```text
B, C, D, E가 참조하는 사용자 스펙의 원천은 A의 프로필 데이터다.
C의 적합도 분석이나 D의 면접 질문 생성이 프로필 데이터를 가공하더라도 원본 프로필 수정 책임은 A에게 있다.
E는 크레딧 차감과 사용량 조회 화면을 담당하지만, A의 AI 요청도 공통 규약에 따라 ai_usage_log에 기록한다.
인증/권한, DB 스키마, 공통 AI 엔진을 바꿔야 하는 경우에는 공통 영역 규칙을 따른다.
```

### 포트폴리오 설명 포인트

```text
회원 인증부터 사용자 프로필 관리까지 구현하고,
AI를 활용해 이력서와 프로필 요약, 기술스택 추출, 자기소개서·경력·프로젝트 핵심 키워드 추출,
프로필 완성도 진단까지 이어지는 사용자 스펙 정리 흐름을 개발했다.
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

이 섹션의 AI 기능은 3번 표의 B 담당 항목과 같은 명칭으로 관리한다.

```text
공고문 AI 분석
필수 조건 AI 추출
우대 조건 AI 추출
담당 업무 AI 요약
기업 현황 AI 요약
면접 포인트 AI 추출
```

AI 기능 상세 책임:

| 3번 번호 | AI 기능 | 주요 입력 | 주요 출력과 활용 |
| --- | --- | --- | --- |
| 6 | 공고문 AI 분석 | 공고 원문 텍스트, PDF/이미지 OCR 결과, 지원 건의 기업·직무 정보 | 공고 구조, 직무, 요구 역량, 업무 범위, 채용 절차를 구조화한다. |
| 7 | 필수 조건 AI 추출 | 공고문 AI 분석 결과, 공고 원문 | 반드시 충족해야 하는 기술, 경력, 학력, 자격, 근무 조건을 추출한다. C의 적합도 분석 기준으로 전달한다. |
| 8 | 우대 조건 AI 추출 | 공고문 AI 분석 결과, 공고 원문 | 우대 기술, 경험, 산업 이해, 협업 방식 같은 가산 요소를 추출한다. |
| 9 | 담당 업무 AI 요약 | 공고문 AI 분석 결과, 업무 설명 영역 | 실제 수행 업무를 짧은 문장과 태그로 요약한다. 지원 건 상세와 면접 준비 화면에서 활용한다. |
| 10 | 기업 현황 AI 요약 | 기업명, 공고의 회사 설명, 외부 출처 또는 수동 입력 자료 | 사업 영역, 최근 이슈, 서비스, 성장 포인트를 요약한다. 사실과 AI 추론은 구분해 저장한다. |
| 11 | 면접 포인트 AI 추출 | 공고문 분석 결과, 기업 현황 요약, 필수/우대 조건 | 면접에서 검증될 가능성이 높은 기술·경험·협업 질문 포인트를 뽑아 D의 면접 질문 생성 입력으로 활용한다. |

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
AI를 활용해 공고문 분석, 필수·우대 조건 추출, 담당 업무 요약, 기업 현황 요약,
면접 포인트 추출까지 이어지는 공고·기업 분석 흐름을 개발했다.
```

## 6. C 담당: 홈/스펙 비교/취업 분석/대시보드

### 사용자 기능

```text
홈
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

이 섹션의 AI 기능은 3번 표의 C 담당 항목과 같은 명칭으로 관리한다.

```text
공고-스펙 적합도 AI 분석
부족 역량 AI 추천
학습 로드맵 AI 추천
자격증 AI 추천
장기 취업 경향 AI 분석
다음 지원 방향 AI 추천
대시보드 AI 분석 결과 요약
```

AI 기능 상세 책임:

| 3번 번호 | AI 기능 | 주요 입력 | 주요 출력과 활용 |
| --- | --- | --- | --- |
| 12 | 공고-스펙 적합도 AI 분석 | A의 프로필 스냅샷, B의 필수/우대 조건, 지원 건 정보 | 적합도 점수, 매칭 근거, 부족 근거, 사용자에게 설명 가능한 비교 결과를 생성한다. |
| 13 | 부족 역량 AI 추천 | 적합도 분석 결과, 필수/우대 조건, 사용자의 현재 스펙 | 단기 보완 역량, 장기 보완 역량, 지원 전 반드시 채워야 할 항목을 추천한다. |
| 14 | 학습 로드맵 AI 추천 | 부족 역량, 희망 직무, 사용자 학습 가능 기간 | 학습 순서, 실습 프로젝트, 추천 기간, 우선순위를 제안한다. |
| 15 | 자격증 AI 추천 | 희망 직무, 산업, 부족 역량, 기존 자격증 | 취득 가치가 있는 자격증과 우선순위를 추천하고, 과도한 자격증 준비는 낮은 우선순위로 안내한다. |
| 16 | 장기 취업 경향 AI 분석 | 여러 지원 건, 적합도 이력, 면접/첨삭 결과, 시간 흐름 | 반복적으로 부족한 역량, 직무 선택 패턴, 합격 가능성이 높은 방향을 분석한다. |
| 17 | 다음 지원 방향 AI 추천 | 장기 취업 경향, 최근 지원 결과, 관심 직무 | 다음에 지원할 직무·기업 유형, 보완 후 재지원 전략, 준비 우선순위를 추천한다. |
| 18 | 대시보드 AI 분석 결과 요약 | 적합도, 부족 역량, 학습 추천, 지원 현황 | 홈과 대시보드에서 바로 읽을 수 있는 핵심 요약과 다음 액션을 제공한다. |

예시 결과:

```text
최근 지원한 공고에서 TypeScript와 AWS가 반복적으로 부족 역량으로 나타납니다.
프런트엔드 직무 지원을 유지하려면 TypeScript 프로젝트 1개와 AWS 배포 경험을 추가하는 것이 좋습니다.
```

### 담당 폴더

```text
frontend/src/features/home
frontend/src/features/analysis
frontend/src/features/dashboard
frontend/src/admin/features/analytics
frontend/src/admin/features/home
frontend/src/admin/features/dashboard
frontend/src/admin/features/fit-analysis

backend/src/main/java/com/careertuner/home
backend/src/main/java/com/careertuner/fitanalysis
backend/src/main/java/com/careertuner/analysis
backend/src/main/java/com/careertuner/dashboard
backend/src/main/java/com/careertuner/admin/analytics
backend/src/main/java/com/careertuner/admin/home
backend/src/main/java/com/careertuner/admin/dashboard
backend/src/main/java/com/careertuner/admin/fitanalysis
```

C는 `applications` 안에서 아래 컴포넌트도 담당한다.

```text
frontend/src/features/applications/components/FitAnalysisPanel.tsx
frontend/src/features/applications/components/StrategyPanel.tsx
frontend/src/features/applications/components/LearningRecommendationPanel.tsx
```

홈은 공개 진입점이지만 기본 대시보드, 준비 현황 요약, 최근 분석 결과 진입을 포함하므로 C가 담당한다.

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

이 섹션의 AI 기능은 3번 표의 D 담당 항목과 같은 명칭으로 관리한다.

```text
예상 면접 질문 AI 생성
꼬리 질문 AI 생성
AI 면접관 대화 진행
면접 답변 AI 평가
면접 리포트 AI 생성
```

AI 기능 상세 책임:

| 3번 번호 | AI 기능 | 주요 입력 | 주요 출력과 활용 |
| --- | --- | --- | --- |
| 19 | 예상 면접 질문 AI 생성 | B의 공고 분석, C의 적합도 분석, A의 프로필 스냅샷 | 직무 질문, 프로젝트 질문, 인성 질문, 기업 맞춤 질문을 생성한다. |
| 20 | 꼬리 질문 AI 생성 | 사용자의 답변, 이전 질문, 공고 요구 역량 | 답변의 빈틈, 근거 부족, 추가 검증 포인트를 바탕으로 후속 질문을 생성한다. |
| 21 | AI 면접관 대화 진행 | 면접 세션 상태, 질문 목록, 사용자 답변 | 면접 흐름을 유지하고 다음 질문, 재질문, 종료 시점을 제어한다. |
| 22 | 면접 답변 AI 평가 | 질문, 사용자 답변, 공고 요구 조건, 평가 기준 | 구체성, 직무 적합성, 논리성, 개선 포인트를 질문별로 평가한다. |
| 23 | 면접 리포트 AI 생성 | 면접 세션 전체, 질문별 평가, 답변 기록 | 총평, 강점, 약점, 다음 연습 과제, 지원 건별 면접 준비 리포트를 생성한다. |

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
AI를 활용해 예상 질문, 꼬리 질문, 면접관 대화 진행, 답변 평가, 면접 리포트를 생성하는 기능을 개발했다.
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

이 섹션의 AI 기능은 3번 표의 E 담당 항목과 같은 명칭으로 관리한다.

```text
면접 답변 AI 첨삭
자기소개서 AI 첨삭
이력서 표현 AI 개선
포트폴리오 설명 AI 개선
사용량 기반 요금제 AI 추천
```

AI 기능 상세 책임:

| 3번 번호 | AI 기능 | 주요 입력 | 주요 출력과 활용 |
| --- | --- | --- | --- |
| 24 | 면접 답변 AI 첨삭 | D의 면접 질문과 답변, 답변 평가 결과, 공고 요구 역량 | 답변 구조, 근거, STAR 형식, 구체적 표현을 개선한다. |
| 25 | 자기소개서 AI 첨삭 | A의 자기소개서 원문, A의 핵심 키워드, 지원 건 정보 | 문장 흐름, 직무 연관성, 강점 표현, 중복 문장을 개선한다. |
| 26 | 이력서 표현 AI 개선 | A의 이력서 원문, 경력·프로젝트 키워드, 기술스택 | 성과 중심 문장, 역할 명확화, 기술 표현, 정량 지표 보강 방향을 제안한다. |
| 27 | 포트폴리오 설명 AI 개선 | 포트폴리오 링크 또는 설명, 프로젝트 키워드, 희망 직무 | 프로젝트 설명, 문제 해결 과정, 기술 선택 이유, 결과 표현을 개선한다. |
| 28 | 사용량 기반 요금제 AI 추천 | 사용자의 AI 사용량, 크레딧 잔액, 최근 기능 사용 패턴 | 사용자에게 맞는 요금제 또는 충전 필요성을 추천하되 결제 강요처럼 보이지 않게 안내한다. |

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
AI 기능 사용량에 따른 크레딧 차감, 결제 내역 관리, 사용량 기반 요금제 추천 구조를 구현했다.
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

이 섹션의 AI 기능은 3번 표의 F 담당 항목과 같은 명칭으로 관리한다.

```text
커뮤니티 면접 후기 AI 요약
커뮤니티 게시글 AI 태그 추천
실제 면접 질문 AI 추출
사용자 관심 기반 게시글 AI 추천
부적절 게시글/신고 AI 분류
고객문의 AI 답변 초안 생성
```

AI 기능 상세 책임:

| 3번 번호 | AI 기능 | 주요 입력 | 주요 출력과 활용 |
| --- | --- | --- | --- |
| 29 | 커뮤니티 면접 후기 AI 요약 | 면접 후기 게시글, 회사·직무 태그, 댓글 맥락 | 기업, 직무, 면접 유형, 난이도, 핵심 질문, 키워드를 요약한다. |
| 30 | 커뮤니티 게시글 AI 태그 추천 | 게시글 제목과 본문, 게시판 유형, 기존 태그 | 직무, 기술, 기업, 면접 유형, 합격 전략 같은 검색·필터 태그를 추천한다. |
| 31 | 실제 면접 질문 AI 추출 | 면접 후기 본문, 댓글, 사용자가 표시한 질문 영역 | 실제로 질문받은 문장과 유사 질문을 추출해 D의 면접 질문 참고 데이터로 활용할 수 있게 한다. |
| 32 | 사용자 관심 기반 게시글 AI 추천 | 사용자의 관심 직무, 지원 기업, 읽은 글, 북마크, 검색 이력 | 관련 후기, 질문, 합격 전략 글을 추천한다. 개인정보와 민감 정보는 추천 근거에서 제외한다. |
| 33 | 부적절 게시글/신고 AI 분류 | 신고 사유, 게시글/댓글 본문, 작성자 이력 | 욕설, 광고, 개인정보 노출, 허위 정보 의심 등 운영 분류와 우선순위를 제안한다. 최종 조치는 운영자가 확정한다. |
| 34 | 고객문의 AI 답변 초안 생성 | 문의 내용, FAQ, 공지, 사용자 상태, 기존 처리 이력 | 상담원이 검토할 답변 초안을 생성한다. 환불·정책·개인정보 이슈는 확정 답변이 아니라 참고 초안으로 표시한다. |

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
ai_usage_log
```

### 포트폴리오 설명 포인트

```text
커뮤니티와 고객센터 기능을 구현하고,
AI를 활용해 면접 후기 요약, 게시글 태그 추천, 실제 질문 추출, 관심 기반 게시글 추천,
신고 분류, 고객문의 답변 초안 생성을 지원하는 기능을 개발했다.
```

## 10. 담당자별 최종 요약

| 담당자 | 사용자 기능 | AI 기능 | 관리자 기능 |
| --- | --- | --- | --- |
| A | 인증, 회원, 프로필, 설정 | 이력서/프로필 요약, 기술스택 추출, 자기소개서·경력/프로젝트 키워드 추출, 프로필 완성도 진단 | 회원 관리, 프로필 운영 확인, 동의 이력 관리 |
| B | 지원 건, 공고문, 공고 분석, 기업 분석 | 공고문 분석, 필수/우대 조건 추출, 담당 업무 요약, 기업 현황 요약, 면접 포인트 추출 | 지원 건 관리, 공고/기업 분석 로그 관리 |
| C | 홈, 스펙 비교, 취업 분석, 대시보드 | 적합도 분석, 부족 역량 추천, 학습 로드맵·자격증 추천, 장기 취업 경향 분석, 다음 지원 방향 추천, 대시보드 요약 | 관리자 홈, 분석 통계, 적합도 분석 관리 |
| D | 가상 면접, 면접 리포트 | 예상 질문 생성, 꼬리 질문 생성, AI 면접관 대화 진행, 답변 평가, 리포트 생성 | 면접 세션 관리, 면접 리포트 관리 |
| E | 첨삭, 결제, 크레딧 | 면접 답변·자기소개서 첨삭, 이력서·포트폴리오 표현 개선, 사용량 기반 요금제 추천 | 결제 관리, 크레딧 관리, 첨삭 로그 관리 |
| F | 커뮤니티, 고객센터, 알림, 공지 | 면접 후기 요약, 게시글 태그 추천, 실제 면접 질문 추출, 관심 기반 추천, 신고 분류, 문의 답변 초안 | 게시판/신고, 공지/FAQ/문의 관리 |

## 11. 공통 영역

공통 영역의 Owner는 팀장이다. 아래 영역은 기능 담당자가 임의로 수정하지 않고, 수정 필요 사유와 영향 범위를
공유한 뒤 팀장 승인 또는 팀 합의 후 변경한다.

```text
frontend/src/app/routes.ts
frontend/src/admin/routes.ts
frontend/src/app/components/layout
frontend/src/app/components/ui
frontend/src/app/components/media
frontend/src/app/components/upload
frontend/src/app/lib
frontend/src/admin/features/prompts 루트 셸

backend/src/main/java/com/careertuner/common
backend/src/main/java/com/careertuner/ai/common
backend/src/main/java/com/careertuner/ai/prompt 공통 엔진
backend/src/main/java/com/careertuner/admin/prompt 루트 셸
backend/src/main/resources/db/schema.sql
backend/src/main/resources/db/data.sql
backend/src/main/resources/application.yaml
backend/build.gradle
공통 로그 스키마와 수집기
프런트 공통 구조
백엔드/DB 공통 구조
AI 공통 구조
```

전역 `frontend/src/admin/features/logs`를 기본 생성하지 않는다. 기능별 운영 로그가 필요하면
`frontend/src/admin/features/<feature>/logs`, `backend/src/main/java/com/careertuner/admin/<domain>/log`처럼
각 담당자 하위 폴더에 둔다. 공통 로그 스키마, 공통 수집기, 전역 로그 뷰어가 필요할 때만 공통 영역으로 다룬다.

공통 영역은 기능 구현 속도를 위해 필요하지만, 동시에 충돌이 가장 자주 나는 곳이다.
가능하면 각 기능 담당자는 자기 기능 폴더 안에서 먼저 해결하고, 공통화가 필요할 때만 팀장과 합의해 이동한다.

예외:

- 단순 오타, 주석, 명백한 문서 오류 수정은 바로 반영할 수 있다.
- 라우팅, 공통 컴포넌트, 공통 API, DB 구조, 인증/권한, AI 프롬프트 공통 엔진, 공통 로그 구조에 영향을 주는 변경은 반드시 합의 후 진행한다.
- 기능별 프롬프트와 기능별 로그 하위 폴더는 각 담당자가 소유한다. 공통 엔진이나 루트 셸 변경만 팀장 승인 또는 팀 합의를 거친다.

## 12. 기존 관리자 골격 담당 지정

현재 저장소에 있는 관리자 골격은 아래처럼 담당자를 지정한다. 분석 통계 명칭은 `analytics`로 통일한다.
관리자 분석 통계 패키지는 `analytics` 경로만 사용한다.

| 관리자 골격 | 담당 | 사용 목적 |
| --- | --- | --- |
| `backend/src/main/java/com/careertuner/admin/auth` | A | 관리자 로그인·세션·권한 보조 |
| `backend/src/main/java/com/careertuner/admin/profile` | A | 사용자 프로필 조회와 운영 확인 |
| `backend/src/main/java/com/careertuner/admin/settings` | A | 계정·알림 설정 운영 확인 |
| `backend/src/main/java/com/careertuner/admin/home` | C | 관리자 홈, 기본 대시보드, 준비 현황 요약 |
| `backend/src/main/java/com/careertuner/admin/billing` | E | 결제/구독 운영 화면 보조 |
| `backend/src/main/java/com/careertuner/admin/legal` | F | 약관·정책 콘텐츠 관리 |
| `backend/src/main/java/com/careertuner/admin/company` | F | 서비스 회사/브랜드 소개 관리 |
| `backend/src/main/java/com/careertuner/admin/serviceinfo` | F | 서비스 소개 콘텐츠 관리 |
| `backend/src/main/java/com/careertuner/admin/analytics` | C | 분석 통계와 대시보드 AI 분석 결과 관리 |

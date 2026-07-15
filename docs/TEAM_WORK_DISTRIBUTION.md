# Team Work Distribution

이 문서는 6명 개발자가 CareerTuner를 기능별로 나누어 개발하기 위한 공통 합의안이다.
`PRODUCT_STRUCTURE.md`가 사용자 관점의 제품 구조이고, `FEATURE_MODULE_STRUCTURE.md`가 실제 폴더 구조라면,
이 문서는 각 담당자가 어떤 사용자 기능, AI 기능, 관리자 기능, DB를 함께 책임지는지 정리한다.

이 문서의 기능 목록은 **목표 책임 범위**, provider·모델 표는 **현재 런타임 상태**로 구분한다. 과거 도입
결정과 실험 계획은 planning 문서에 남아 있을 수 있으나, 현재 기본값은 백엔드 설정과 모듈 README를 우선한다.

담당 지정은 해당 영역을 구현할 때의 소유권을 뜻한다. 사용자 기능이 특정 릴리스에 포함되면 관련 관리자 화면과
관리자 API도 같은 릴리스의 완료 기준에 포함한다.
출시 단계는 `docs/planning/기획.md`, 표준 경로와 교차 기능 경계는 `FEATURE_MODULE_STRUCTURE.md`를 따른다.
각 담당자의 `주요 DB` 목록은 최종 목표 데이터 구조를 포함하므로, 현재 스키마 존재 여부는
`backend/src/main/resources/db/schema.sql`에서 확인한다.
자체 LLM 도입 근거와 공통 결정은 `docs/planning/자체LLM_팀_도입안.md`, 담당별 모델 운영·검증·fallback·산출물 기준은
`docs/planning/담당별_자체LLM_운영안.md`를 따른다.

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
- `ai/common`, 공통 프롬프트 엔진의 예약 경계인 `ai/prompt`, 공통 로그 구조는 팀장 Owner의 공통 영역으로 관리한다.
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

아래 AI 기능은 담당별 목표 기능 카탈로그다. 표에 있다는 사실만으로 현재 구현 완료나 특정 provider 사용을
의미하지 않는다. 공통 AI 기반, 프롬프트 계약, 사용량 로깅은
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

provider tier·시간예산·GPU permit 같은 교차 기반은 `ai/common`, 사용량은 `ai_usage_log`의 공통 규약으로
맞춘다. `ai/prompt`에는 아직 실행 엔진이 없으며, 각 담당자는 자기 도메인 안에 AI 서비스와 실제
프롬프트 catalog·builder·validator를 둔다.
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
A는 그 하위의 `profile` 프롬프트만 담당한다. 공통 AI 기반, 향후 `ai/prompt`에 도입할 공통 프롬프트 엔진,
공통 로그 수집기는 팀장 Owner 영역이므로 수정 전 합의가 필요하다.

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

B는 CareerTuner의 핵심 단위인 지원 건과 그 안에 연결되는 공고 원문, 공고 분석, 기업 분석을 책임진다.
이 영역의 결과는 C의 적합도 분석, D의 면접 질문 생성, E의 첨삭 맥락으로 이어지므로 공고 원문과 AI 분석 결과의
출처, 버전, 사용자 확인 상태를 함께 관리한다.

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

사용자 기능 상세 책임:

| 영역 | 상세 책임 |
| --- | --- |
| 지원 건 | 기업명, 직무명, 지원 상태, 즐겨찾기, 보관/삭제 여부를 기준으로 지원 건을 생성·조회·수정한다. |
| 공고 원문 | 텍스트 직접 입력, PDF/이미지 등록 준비, OCR 추출 텍스트, 원문 보기, 수정 이력을 관리한다. |
| 공고 분석 | 필수 조건, 우대 조건, 담당 업무, 경력 수준, 고용 형태, 난이도, 요약 정보를 사용자가 확인할 수 있게 제공한다. |
| 기업 분석 | 기업 요약, 산업, 최근 이슈, 경쟁사, 출처 URL, 확인 시점, 재조회 필요 시점을 관리한다. |
| 지원 상태 | `DRAFT`, `ANALYZING`, `READY`, `APPLIED`, `CLOSED` 상태 전환과 상태별 화면 동작을 관리한다. |
| 분석 재실행 | 공고 원문이 바뀌었거나 기업 정보가 오래된 경우 재분석 진입점을 제공하고 기존 분석 결과와 구분한다. |
| 사용자 확인 | AI가 추출한 공고 조건과 기업 정보를 사용자가 확정하거나 수정할 수 있는 흐름을 둔다. |
| 공유 경계 | C, D, E가 참조하는 분석 결과를 깨지 않도록 지원 건 ID와 공고 revision 기준을 유지한다. |

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

B의 자체 AI 1순위는 공고문 전체를 한 번에 생성형 모델로 대체하는 것이 아니라, 공고문 텍스트를 문장 단위로 나눈 뒤
필수 조건, 우대 조건, 담당 업무, 기술 스택 등으로 분류하는 구조화 추출이다. OCR은 PDF/이미지에서 입력 텍스트를 확보하는
단계로 보고, 자체 모델의 핵심은 OCR 이후 문장 분류와 조건 추출에 둔다.

| 3번 번호 | AI 기능 | 주요 입력 | 주요 출력과 활용 |
| --- | --- | --- | --- |
| 6 | 공고문 AI 분석 | 공고 원문 텍스트, PDF/이미지 OCR 결과, 지원 건의 기업·직무 정보 | 공고 구조, 직무, 요구 역량, 업무 범위, 채용 절차를 구조화한다. |
| 7 | 필수 조건 AI 추출 | 공고문 AI 분석 결과, 공고 원문 | 반드시 충족해야 하는 기술, 경력, 학력, 자격, 근무 조건을 추출한다. C의 적합도 분석 기준으로 전달한다. |
| 8 | 우대 조건 AI 추출 | 공고문 AI 분석 결과, 공고 원문 | 우대 기술, 경험, 산업 이해, 협업 방식 같은 가산 요소를 추출한다. |
| 9 | 담당 업무 AI 요약 | 공고문 AI 분석 결과, 업무 설명 영역 | 실제 수행 업무를 짧은 문장과 태그로 요약한다. 지원 건 상세와 면접 준비 화면에서 활용한다. |
| 10 | 기업 현황 AI 요약 | 기업명, 공고의 회사 설명, 외부 출처 또는 수동 입력 자료 | 사업 영역, 최근 이슈, 서비스, 성장 포인트를 요약한다. 사실과 AI 추론은 구분해 저장한다. |
| 11 | 면접 포인트 AI 추출 | 공고문 분석 결과, 기업 현황 요약, 필수/우대 조건 | 면접에서 검증될 가능성이 높은 기술·경험·협업 질문 포인트를 뽑아 D의 면접 질문 생성 입력으로 활용한다. |

AI 처리 원칙:

```text
공고 원문, OCR 추출 텍스트, AI 분석 결과를 서로 구분해 저장한다.
AI가 추출한 필수/우대 조건은 사용자가 확인하거나 수정할 수 있어야 한다.
기업 현황은 확인된 사실, 외부 출처, AI 추론을 구분해 표시한다.
오래된 기업 정보는 재조회 또는 재분석이 필요하다는 상태를 남긴다.
분석 중인 지원 건은 상태를 `ANALYZING`으로 표시하고, 실패 시 재시도 가능한 오류 상태를 제공한다.
```

예시 결과:

```text
직무: 프런트엔드 개발자
필수 기술: React, JavaScript, REST API
우대 기술: TypeScript, AWS, Next.js
담당 업무: 웹 서비스 개발 및 유지보수
기업 현황: B2B SaaS 채용 플랫폼을 운영하며 최근 AI 매칭 기능을 확장 중
면접 포인트: React 프로젝트 경험, API 연동 경험, 협업 경험
출처: 공고 원문, 기업 홈페이지, 채용 페이지
```

### 관리자 기능

```text
지원 건 목록 조회
지원 건 검색과 필터
지원 건 상세 조회
공고 원문과 추출 텍스트 확인
공고 분석 결과 확인과 운영 메모
기업 분석 결과와 출처 확인
분석 실패 이력 조회
지원 건 상태 변경
공고/기업 분석 프롬프트 운영 확인
```

관리자 완료 기준:

| 영역 | 상세 기준 |
| --- | --- |
| 지원 건 관리 | 사용자, 기업명, 직무명, 상태, 생성일, 보관/삭제 여부로 목록과 상세를 조회한다. |
| 공고 원문 확인 | 원문 텍스트, 업로드 파일 URL, OCR 추출 텍스트, 등록 시점을 확인할 수 있어야 한다. |
| 공고 분석 운영 | 필수/우대 조건, 담당 업무, 난이도, 요약, 분석 실행 시점을 확인하고 운영 메모를 남길 수 있어야 한다. |
| 기업 분석 운영 | 기업 요약, 최근 이슈, 산업, 경쟁사, 면접 포인트, 출처와 확인 시점을 함께 확인한다. |
| 오류 대응 | 분석 실패 원인, 재시도 여부, 사용량 로그 연결을 확인하고 사용자 문의 대응에 필요한 정보를 제공한다. |
| 상태 변경 | 잘못 생성된 지원 건이나 분석 오류 건에 대해 상태 변경과 처리 이력을 남길 수 있어야 한다. |

### 담당 폴더

```text
frontend/src/features/applications
frontend/src/admin/features/application-cases
frontend/src/admin/features/job-analysis
frontend/src/admin/features/company-analysis
frontend/src/admin/features/prompts/job-analysis
frontend/src/admin/features/prompts/company-analysis

backend/src/main/java/com/careertuner/applicationcase
backend/src/main/java/com/careertuner/jobposting
backend/src/main/java/com/careertuner/jobanalysis
backend/src/main/java/com/careertuner/jobanalysis/ai
backend/src/main/java/com/careertuner/jobanalysis/ai/prompt
backend/src/main/java/com/careertuner/companyanalysis
backend/src/main/java/com/careertuner/companyanalysis/ai
backend/src/main/java/com/careertuner/companyanalysis/ai/prompt
backend/src/main/java/com/careertuner/admin/applicationcase
backend/src/main/java/com/careertuner/admin/jobanalysis
backend/src/main/java/com/careertuner/admin/companyanalysis
backend/src/main/java/com/careertuner/admin/prompt/jobanalysis
backend/src/main/java/com/careertuner/admin/prompt/companyanalysis
```

`frontend/src/admin/features/prompts`와 `backend/src/main/java/com/careertuner/admin/prompt`의 루트 셸은 공통 영역이다.
B는 그 하위의 `job-analysis`, `company-analysis` 프롬프트만 담당한다. 공통 AI 기반이나 향후
`ai/prompt`에 도입할 공통 프롬프트 엔진을 바꿔야 하면 공통 영역 규칙을 따른다.

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

데이터 책임:

```text
application_case: 지원 건의 기업명, 직무명, 상태, 즐겨찾기, 보관/삭제 기준
job_posting: 공고 원문, 업로드 파일 URL, OCR 추출 텍스트, 공고 입력 방식
job_analysis: 고용 형태, 경력 수준, 필수 기술, 우대 기술, 담당 업무, 자격 요건, 난이도, 요약
company_analysis: 기업 요약, 최근 이슈, 산업, 경쟁사, 면접 포인트, 출처
ai_usage_log: B 영역 공고문 분석, 조건 추출, 기업 요약, 면접 포인트 추출 요청의 사용량 기록
```

연동 경계:

```text
A의 프로필 데이터는 B가 수정하지 않고, 지원 건과 공고 분석 결과만 관리한다.
C는 B의 필수/우대 조건과 담당 업무를 적합도 분석 기준으로 사용한다.
D는 B의 면접 포인트와 기업 현황을 면접 질문 생성의 입력으로 사용한다.
E는 B의 공고/기업 분석 결과를 자기소개서·답변 첨삭 맥락으로 참조할 수 있다.
공고 업로드 파일 저장 방식과 OCR 공통 컴포넌트가 필요하면 D 또는 공통 영역 규칙을 따른다.
```

### 포트폴리오 설명 포인트

```text
사용자가 등록한 채용공고를 지원 건 단위로 관리하고,
AI를 활용해 공고문 분석, 필수·우대 조건 추출, 담당 업무 요약, 기업 현황 요약,
면접 포인트 추출까지 이어지는 공고·기업 분석 흐름을 개발했다.
```

## 6. C 담당: 홈/스펙 비교/취업 분석/대시보드/직무·자격 카탈로그

C는 사용자가 지금 지원해도 되는지, 무엇을 보완해야 하는지, 다음에 어떤 방향으로 움직여야 하는지를 보여주는
분석과 대시보드 영역을 책임진다. B의 공고 분석 결과와 A의 프로필 스냅샷을 비교하고, D/E에서 생성되는 면접·첨삭 결과까지
장기 추세 분석에 활용하되 원본 데이터의 소유권은 각 담당 영역에 남긴다.

### 사용자 기능

```text
홈
내 스펙과 공고 비교
직무 적합도 점수
부족 역량 분석
추천 학습
추천 자격증
NCS 직무능력표준 검색·상세
국가·민간 자격증 검색·상세
지원 전략
취업 분석 대시보드
장기 지원 경향 분석
전체 준비 현황 대시보드
```

사용자 기능 상세 책임:

| 영역 | 상세 책임 |
| --- | --- |
| 홈 | 로그인 후 핵심 상태, 최근 지원 건, 최근 분석 결과, 다음 액션을 요약해 보여준다. |
| 스펙 비교 | 공고별 필수/우대 조건과 사용자 프로필을 비교해 매칭·부족 항목을 설명 가능한 형태로 보여준다. |
| 적합도 점수 | 점수만 제공하지 않고 매칭 근거, 부족 근거, 점수 산정 기준, 분석 시점을 함께 제공한다. |
| 부족 역량 | 당장 지원 전 보완할 항목과 장기적으로 쌓아야 할 항목을 나눠 보여준다. |
| 추천 학습 | 부족 역량별 학습 순서, 실습 과제, 예상 기간, 완료 체크 상태를 관리한다. |
| 추천 자격증 | 직무·산업별 실효성이 있는 자격증을 우선순위와 이유와 함께 제안한다. |
| 직무·자격 카탈로그 | NCS 세분류·능력단위·기술과 국가·민간 자격 정보를 공개 검색·상세 화면으로 제공한다. |
| 지원 전략 | 현재 지원 건에 대한 지원 가능성, 보완 전략, 다음 준비 과제를 정리한다. |
| 취업 분석 대시보드 | 여러 지원 건의 적합도 변화, 반복 부족 역량, 직무 선택 패턴, 준비 현황을 시각화한다. |
| 장기 경향 | 시간 흐름에 따른 지원 직무, 기업 유형, 분석 결과, 면접 결과, 첨삭 결과의 변화를 해석한다. |

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

AI 처리 원칙:

```text
분석에는 반드시 어떤 프로필 스냅샷과 어떤 공고 분석 결과를 사용했는지 남긴다.
적합도 점수는 설명 가능한 근거와 함께 제공하고, 단일 점수만 단독으로 노출하지 않는다.
부족 역량 추천은 필수 조건 미충족, 우대 조건 보완, 장기 성장 항목을 구분한다.
학습과 자격증 추천은 사용자의 희망 직무, 시간, 기존 경험을 고려해 과도한 추천을 줄인다.
장기 경향 분석은 여러 지원 건의 누적 데이터를 사용하되 개별 원본 데이터는 각 담당 영역에서 관리한다.
```

예시 결과:

```text
최근 지원한 공고에서 TypeScript와 AWS가 반복적으로 부족 역량으로 나타납니다.
프런트엔드 직무 지원을 유지하려면 TypeScript 프로젝트 1개와 AWS 배포 경험을 추가하는 것이 좋습니다.
현재 지원 건 적합도: 74점
매칭 근거: React 프로젝트 경험, REST API 연동 경험, 협업 경험
부족 근거: TypeScript 실무 경험 부족, 클라우드 배포 경험 부족
다음 액션: TypeScript 리팩토링 프로젝트를 포트폴리오에 추가하고 AWS 배포 과정을 정리한다.
```

### 관리자 기능

```text
분석 통계 목록 조회
사용자별 분석 이력 조회
지원 건별 적합도 분석 결과 조회
부족 역량과 추천 학습 결과 확인
장기 취업 경향 분석 결과 확인
대시보드 요약 결과 확인
분석 실패 이력 조회
분석 결과 운영 메모
적합도/분석 프롬프트 운영 확인
```

관리자 완료 기준:

| 영역 | 상세 기준 |
| --- | --- |
| 적합도 분석 관리 | 지원 건, 사용자, 점수, 매칭 항목, 부족 항목, 분석 시점을 기준으로 목록과 상세를 조회한다. |
| 추천 결과 확인 | 추천 학습, 추천 자격증, 지원 전략이 어떤 입력을 근거로 생성됐는지 확인할 수 있어야 한다. |
| 대시보드 운영 | 전체 사용자 또는 기간별 분석 수, 평균 적합도, 반복 부족 역량 같은 운영 지표를 확인한다. |
| 장기 분석 확인 | 여러 지원 건을 묶은 경향 분석 결과와 요약 문구, 생성 시점을 확인할 수 있어야 한다. |
| 오류 대응 | 분석 실패 원인, 재시도 가능 여부, 사용량 로그 연결을 확인한다. |
| 운영 메모 | 과도한 추천, 잘못된 분석, 사용자 문의 대응 내용을 운영 메모로 남길 수 있어야 한다. |

### 담당 폴더

```text
frontend/src/features/home
frontend/src/features/analysis
frontend/src/features/dashboard
frontend/src/features/catalog
frontend/src/admin/features/analytics
frontend/src/admin/features/home
frontend/src/admin/features/dashboard
frontend/src/admin/features/fit-analysis
frontend/src/admin/features/prompts/fit-analysis
frontend/src/admin/features/prompts/analytics

backend/src/main/java/com/careertuner/home
backend/src/main/java/com/careertuner/catalog
backend/src/main/java/com/careertuner/fitanalysis
backend/src/main/java/com/careertuner/fitanalysis/ai
backend/src/main/java/com/careertuner/fitanalysis/ai/prompt
backend/src/main/java/com/careertuner/analysis
backend/src/main/java/com/careertuner/analysis/ai
backend/src/main/java/com/careertuner/analysis/ai/prompt
backend/src/main/java/com/careertuner/dashboard
backend/src/main/java/com/careertuner/dashboard/ai
backend/src/main/java/com/careertuner/dashboard/ai/prompt
backend/src/main/java/com/careertuner/admin/analytics
backend/src/main/java/com/careertuner/admin/home
backend/src/main/java/com/careertuner/admin/dashboard
backend/src/main/java/com/careertuner/admin/fitanalysis
backend/src/main/java/com/careertuner/admin/prompt/fitanalysis
backend/src/main/java/com/careertuner/admin/prompt/analytics
```

`frontend/src/admin/features/prompts`와 `backend/src/main/java/com/careertuner/admin/prompt`의 루트 셸은 공통 영역이다.
C는 그 하위의 `fit-analysis`, `analytics` 프롬프트만 담당한다. 적합도 분석은 `fitanalysis`,
장기 경향과 대시보드 요약은 `analysis` 또는 `dashboard` 하위에서 관리한다.
분석 계열 명명은 `FEATURE_MODULE_STRUCTURE.md`의 "분석 계열 명명 규칙"을 따른다. 요약하면 사용자 취업 분석은
`analysis`, 관리자 통계와 집계 운영은 `analytics`, 적합도 분석 프런트 경로는 `fit-analysis`, REST 컬렉션은
`fit-analyses`, Java 패키지는 `fitanalysis`, DB 테이블은 `fit_analysis`를 사용한다. `fit-analyse`와
`admin/analysis`는 사용하지 않는다.

C는 `applications` 안에서 아래 컴포넌트도 담당한다.

```text
frontend/src/features/applications/components/FitAnalysisPanel.tsx
frontend/src/features/applications/components/StrategyPanel.tsx
frontend/src/features/applications/components/LearningRecommendationPanel.tsx
```

위 컴포넌트의 재분석 버튼·이력 패널 연결을 위해 `ApplicationDetailPage.tsx`에 전달 속성이나 배치 코드를 추가할 수 있다.
단, 지원 건 상세 셸, 공고 저장·업로드, 공고/기업 분석 탭 자체의 동작은 B 소유이므로 해당 로직을 변경할 때는 B와 합의한다.

홈은 공개 진입점이지만 기본 대시보드, 준비 현황 요약, 최근 분석 결과 진입을 포함하므로 C가 담당한다.

### 주요 DB

```text
fit_analysis
ncs_classification
certificate
certificate_exam_schedule
job_analysis
application_case
interview_session
interview_answer
ai_usage_log
```

데이터 책임:

```text
fit_analysis: 적합도 점수, 매칭 기술, 부족 기술, 추천 학습, 추천 자격증, 지원 전략
ncs_classification: 공개 NCS 검색·상세에 사용하는 세분류와 능력단위 계층 스냅샷
certificate, certificate_exam_schedule: 공개 자격증 검색·상세와 국가 시험일정 스냅샷
job_analysis: B가 만든 공고 분석 결과를 읽기 전용 기준 데이터로 참조
application_case: 지원 상태와 지원 건 맥락을 읽기 전용 기준 데이터로 참조
interview_session: D가 만든 면접 결과를 장기 경향 분석 입력으로 참조
interview_answer: D/E가 만든 답변 평가와 개선 답변을 장기 경향 분석 입력으로 참조
ai_usage_log: C 영역 적합도 분석, 역량 추천, 학습/자격증 추천, 장기 경향 분석, 대시보드 요약 요청의 사용량 기록
```

연동 경계:

```text
A의 프로필 스냅샷과 B의 공고 분석 결과를 기준으로 분석하되 원본 데이터는 수정하지 않는다.
B의 공고 조건이 바뀌면 기존 적합도 분석과 새 적합도 분석을 구분한다.
D의 면접 결과와 E의 첨삭 결과는 장기 경향 분석의 입력으로만 사용하고 원본 수정은 각 담당자에게 맡긴다.
대시보드 공통 레이아웃이나 라우팅 변경이 필요하면 공통 영역 규칙을 따른다.
```

### 포트폴리오 설명 포인트

```text
채용공고의 요구 조건과 사용자 프로필을 비교하여 적합도 점수, 부족 역량, 학습 우선순위를 제공하고,
AI를 활용해 사용자의 장기 지원 경향과 커리어 전략을 추천하며, 대시보드에 핵심 분석 결과를 요약 출력하는 기능을 개발했다.
```

## 7. D 담당: 가상 면접/면접 리포트

D는 지원 건 기반 가상 면접의 전체 세션 흐름을 책임진다. B의 공고·기업 분석과 C의 적합도 분석을 바탕으로
예상 질문을 만들고, 사용자의 답변을 저장·평가하며, 면접 종료 후 리포트와 다음 연습 과제를 제공한다.
음성·영상 파일을 다루는 경우 파일 저장과 업로드 UI는 공통 영역 또는 D의 파일 도메인 규칙을 따른다.

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

사용자 기능 상세 책임:

| 영역 | 상세 책임 |
| --- | --- |
| 면접 시작 | 지원 건 선택, 면접 모드 선택, 질문 개수, 진행 방식, 시작 조건을 관리한다. |
| 질문 목록 | 예상 질문, 기술 질문, 인성 질문, 상황 질문, 꼬리 질문을 구분하고 정렬 순서를 관리한다. |
| 면접 진행 | 현재 질문, 답변 입력, 다음 질문 이동, 꼬리 질문 표시, 세션 종료 흐름을 관리한다. |
| 답변 저장 | 텍스트 답변, 음성 URL, 영상 URL, 답변 작성 시점, 질문 연결 관계를 저장한다. |
| 평가 결과 | 질문별 점수, 피드백, 개선 방향, 답변 보완 예시를 사용자에게 제공한다. |
| 면접 리포트 | 총점, 강점, 약점, 반복 문제, 다음 연습 과제를 세션 단위로 정리한다. |
| 미디어 준비 | 음성 녹음과 영상 녹화 준비 상태를 제공하되 업로드/미디어 공통 컴포넌트 수정은 합의 후 진행한다. |
| 재연습 | 기존 세션을 기반으로 같은 질문 재답변, 꼬리 질문 재생성, 리포트 재확인을 지원한다. |

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

AI 처리 원칙:

```text
질문은 지원 건, 공고 분석, 기업 분석, 프로필 스냅샷을 기준으로 생성한다.
꼬리 질문은 사용자의 실제 답변 이후에 생성하고, 원 질문과 답변의 연결 관계를 남긴다.
면접관 대화 진행은 세션 상태를 기준으로 동작하며 임의로 사용자의 답변을 수정하지 않는다.
답변 평가는 점수, 근거, 개선 포인트를 함께 제공하고 단순 점수만 노출하지 않는다.
면접 리포트는 세션 종료 후 생성하며 질문별 평가와 전체 총평을 구분한다.
```

예시 평가:

```text
질문: React 프로젝트에서 상태 관리를 어떻게 설계했나요?
평가: 답변이 너무 짧고 구체성이 부족합니다.
강점: 사용한 기술을 명확히 언급했습니다.
보완점: 본인의 역할, 구현 기능, 문제 해결 경험, 결과를 추가하면 좋습니다.
꼬리 질문: 해당 상태 관리 방식이 성능 문제를 만든 적은 없었나요?
```

### 관리자 기능

```text
면접 세션 목록 조회
면접 세션 검색과 필터
면접 세션 상세 조회
질문 목록과 답변 기록 확인
음성/영상 파일 연결 상태 확인
질문별 평가 결과 확인
면접 리포트 확인
AI 질문·평가 실패 이력 조회
면접 운영 메모
면접 프롬프트 운영 확인
```

관리자 완료 기준:

| 영역 | 상세 기준 |
| --- | --- |
| 세션 관리 | 사용자, 지원 건, 면접 모드, 시작/종료 시점, 총점, 생성일을 기준으로 목록과 상세를 조회한다. |
| 질문 관리 | 질문 유형, 질문 내용, 정렬 순서, 꼬리 질문 여부, 생성 근거를 확인할 수 있어야 한다. |
| 답변 확인 | 텍스트 답변, 음성 URL, 영상 URL, 점수, 피드백, 개선 답변을 질문별로 확인한다. |
| 리포트 확인 | 총평, 강점, 약점, 다음 연습 과제, 생성 시점을 확인할 수 있어야 한다. |
| 오류 대응 | 질문 생성, 꼬리 질문 생성, 답변 평가, 리포트 생성 실패 이력과 사용량 로그 연결을 확인한다. |
| 파일 확인 | 업로드 파일의 존재 여부와 연결 상태를 확인하되 공통 파일 정책 자체는 임의 변경하지 않는다. |

### 담당 폴더

```text
frontend/src/features/interview
frontend/src/admin/features/interviews
frontend/src/admin/features/interview-reports
frontend/src/admin/features/prompts/interview

backend/src/main/java/com/careertuner/interview
backend/src/main/java/com/careertuner/interview/ai
backend/src/main/java/com/careertuner/interview/ai/prompt
backend/src/main/java/com/careertuner/file
backend/src/main/java/com/careertuner/admin/interview
backend/src/main/java/com/careertuner/admin/prompt/interview
```

`frontend/src/admin/features/prompts`와 `backend/src/main/java/com/careertuner/admin/prompt`의 루트 셸은 공통 영역이다.
D는 그 하위의 `interview` 프롬프트만 담당한다. `backend/src/main/java/com/careertuner/file`은 D가 주로 사용하지만
공통 업로드·미디어 정책에 영향을 주는 변경은 팀장 승인 또는 팀 합의를 거친다.

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

데이터 책임:

```text
interview_session: 지원 건별 면접 세션, 모드, 시작/종료 시점, 총점, 리포트
interview_question: 세션별 질문, 질문 유형, 꼬리 질문 여부, 정렬 순서
interview_answer: 질문별 답변 텍스트, 음성 URL, 영상 URL, 점수, 피드백, 개선 답변
file_asset: 음성·영상 파일 원본 또는 연결 정보
ai_usage_log: D 영역 질문 생성, 꼬리 질문 생성, 면접관 진행, 답변 평가, 리포트 생성 요청의 사용량 기록
```

연동 경계:

```text
A의 프로필 스냅샷, B의 공고/기업 분석, C의 적합도 분석을 질문 생성 입력으로 참조한다.
E는 D의 면접 질문과 답변을 첨삭 입력으로 사용할 수 있지만 원본 면접 기록은 D가 관리한다.
C는 면접 점수와 반복 약점을 장기 경향 분석에 참조할 수 있다.
음성·영상 업로드 컴포넌트, 파일 저장 정책, 미디어 재생 공통 UI는 공통 영역 규칙을 따른다.
```

### 포트폴리오 설명 포인트

```text
지원 건 기반 AI 모의면접 세션을 구현하고,
AI를 활용해 예상 질문, 꼬리 질문, 면접관 대화 진행, 답변 평가, 면접 리포트를 생성하는 기능을 개발했다.
```

## 8. E 담당: 첨삭/결제/크레딧

E는 사용자의 작성물을 실제 지원에 맞게 다듬는 첨삭 기능과, AI 기능 사용에 필요한 결제·크레딧·사용량 흐름을 책임진다.
첨삭은 A의 프로필·자기소개서, B의 공고 분석, D의 면접 답변을 맥락으로 활용하며, 결제와 크레딧은 구매 확정과
사용량 로그를 기준으로 정산 가능하게 관리한다.

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

사용자 기능 상세 책임:

| 영역 | 상세 책임 |
| --- | --- |
| AI 첨삭 홈 | 첨삭 가능한 항목, 최근 첨삭 이력, 남은 크레딧, 추천 진입점을 제공한다. |
| 면접 답변 첨삭 | D의 질문과 답변을 기준으로 구조, 근거, 표현, 직무 연관성을 개선한다. |
| 자기소개서 첨삭 | A의 자기소개서 원문과 B의 공고 맥락을 반영해 문항별 답변을 개선한다. |
| 이력서 문장 첨삭 | A의 이력서 원문, 경력·프로젝트 키워드를 바탕으로 성과 중심 문장으로 다듬는다. |
| 포트폴리오 설명 첨삭 | 프로젝트 설명, 기술 선택 이유, 역할, 결과를 지원 직무에 맞게 정리한다. |
| 요금제 | 무료/유료 요금제, 포함 크레딧, 기능별 제한, 결제 전 확인 정보를 제공한다. |
| 크레딧 | 충전, 차감, 잔액, 사용 내역, 실패 요청 미차감, 관리자 조정 이력을 관리한다. |
| 결제 | 결제 요청, 결제 성공 검증, 실패 처리, 결제 내역 조회, 환불 예외 상태를 관리한다. |
| 사용량 | 기능별 AI 사용량, 차감 크레딧, 사용 일시, 관련 지원 건을 사용자가 확인할 수 있게 한다. |

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

AI 처리 원칙:

```text
첨삭 결과는 원문을 덮어쓰지 않고 개선안과 변경 이유를 별도로 제공한다.
면접 답변 첨삭은 D의 질문·평가 맥락을 유지하되 원본 면접 답변은 수정하지 않는다.
자기소개서와 이력서 첨삭은 A의 원본 데이터를 참조하지만 확정 반영은 사용자의 선택으로 처리한다.
사용량 기반 요금제 추천은 사용자의 이익을 우선하고 결제 강요나 과장 표현을 피한다.
AI 요청 성공 여부, 토큰 사용량, 차감 크레딧은 공통 사용량 로그 규약에 맞춰 기록한다.
```

### E 자체 모델 운영 기준

E의 자체 모델은 면접 답변, 자기소개서, 이력서, 포트폴리오 설명을 하나의 첨삭 모델로 다루는 통합 모델이다.
현재 고정 서비스 태그는 `careertuner-e-correction-3b:delivery-s-f16-20260708`이며, 부동 `latest` 태그를
운영 기준으로 쓰지 않는다. 원문에 없는 경험을 만들어내지 않는 원문 보존형 개선 후보 생성기로 운용한다.

| 우선순위 | 모델/방식 | 용도 |
| --- | --- | --- |
| 1 | 팀 공통 Qwen2.5-3B 기반 E LoRA 통합 모델 | 네 첨삭 task를 처리하는 단일 자체 모델 |
| 2 | strict JSON Schema와 동일 3B repair | 위험 표현, 길이, 문단, 허위 사실, 출력 계약 검증 및 1회 보정 |
| 3 | Anthropic 기반 첨삭 | 자체 3B와 repair 실패 시 1차 외부 fallback |
| 4 | OpenAI 기반 첨삭 | Anthropic 실패 또는 미설정 시 최종 fallback |

E 모델 결과는 `corrected_text`, `changes`, `risk_flags`, `preserved_meaning`, `added_facts`, `confidence`를 포함하는 JSON으로 받는다.
서버는 원문 의미 보존, 허위 회사명·기술명·수치·경력 추가 여부, 금지 표현, 길이 제한, 변경 이유 존재 여부를 검증한 뒤 저장한다.
첨삭 결과는 A의 프로필 원문이나 D의 면접 답변 원본을 덮어쓰지 않고, `correction_request` 또는 첨삭 이력 테이블에 별도로 저장한다.

예시 결과:

```text
원문: 저는 React 프로젝트에서 API 연결을 담당했습니다.
개선안: React 기반 게시판 프로젝트에서 인증 API와 게시글 CRUD API를 연동하고, 오류 처리 흐름을 개선했습니다.
개선 이유: 역할, 구현 범위, 기술 맥락이 드러나도록 문장을 구체화했습니다.
추가 제안: 처리 시간 단축, 오류율 감소, 사용자 피드백 같은 성과 수치를 보강하면 더 좋습니다.
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

### 관리자 기능

```text
첨삭 요청 목록 조회
첨삭 요청 검색과 필터
첨삭 원문과 개선안 확인
첨삭 실패 이력 조회
결제 목록 조회
결제 상세와 결제 상태 확인
크레딧 지급·차감·환불 예외 이력 조회
요금제 목록과 상세 관리
AI 사용량과 비용 조회
첨삭/요금제 추천 프롬프트 운영 확인
```

관리자 완료 기준:

| 영역 | 상세 기준 |
| --- | --- |
| 첨삭 관리 | 사용자, 첨삭 유형, 원문, 개선안, 요청 상태, 생성 시점, 관련 지원 건을 조회한다. |
| 결제 관리 | 결제 금액, 요금제, 지급 크레딧, 결제 상태, 결제 성공 시점, 실패 사유를 확인한다. |
| 크레딧 관리 | 지급, 차감, 환불 예외, 관리자 조정, 사용 기능, 관련 사용량 로그를 확인한다. |
| 요금제 관리 | 요금제명, 가격, 지급 크레딧, 기능 제한, 노출 상태를 관리한다. |
| 사용량 조회 | 기능별 토큰 사용량, 차감 크레딧, 사용자별 사용량, 기간별 비용 추정을 확인한다. |
| 오류 대응 | 첨삭 실패, 결제 실패, 중복 결제, 크레딧 미지급 같은 문의 대응 정보를 확인한다. |

### 담당 폴더

```text
frontend/src/features/correction
frontend/src/features/billing
frontend/src/admin/features/corrections
frontend/src/admin/features/payments
frontend/src/admin/features/credits
frontend/src/admin/features/plans
frontend/src/admin/features/ai-usage
frontend/src/admin/features/prompts/correction
frontend/src/admin/features/prompts/billing

backend/src/main/java/com/careertuner/correction
backend/src/main/java/com/careertuner/correction/ai
backend/src/main/java/com/careertuner/correction/ai/prompt
backend/src/main/java/com/careertuner/payment
backend/src/main/java/com/careertuner/billing
backend/src/main/java/com/careertuner/billing/ai
backend/src/main/java/com/careertuner/billing/ai/prompt
backend/src/main/java/com/careertuner/credit
backend/src/main/java/com/careertuner/admin/correction
backend/src/main/java/com/careertuner/admin/payment
backend/src/main/java/com/careertuner/admin/credit
backend/src/main/java/com/careertuner/admin/plan
backend/src/main/java/com/careertuner/admin/aiusage
backend/src/main/java/com/careertuner/admin/prompt/correction
backend/src/main/java/com/careertuner/admin/prompt/billing
```

`frontend/src/admin/features/prompts`와 `backend/src/main/java/com/careertuner/admin/prompt`의 루트 셸은 공통 영역이다.
E는 그 하위의 `correction`, `billing` 프롬프트만 담당한다. 결제 연동, 크레딧 정책, 공통 사용량 조회 구조를
바꿔야 하는 경우에는 팀장 승인 또는 팀 합의를 거친다.

### 주요 DB

```text
correction_request
payment
credit_transaction
ai_usage_log
users.credit
plan
```

데이터 책임:

```text
correction_request: 첨삭 유형, 원문, 개선안, 요청 상태, 관련 사용자와 지원 건
payment: 결제 금액, 요금제, 지급 크레딧, 결제 상태, 결제 성공/실패 시점
credit_transaction: 크레딧 지급, 차감, 환불 예외, 관리자 조정, 관련 AI 요청
ai_usage_log: E 영역 첨삭, 표현 개선, 요금제 추천 요청과 전체 AI 사용량 조회 기준
users.credit: 사용자의 현재 크레딧 잔액
plan: 요금제명, 가격, 지급 크레딧, 기능 제한, 노출 상태
```

연동 경계:

```text
A의 이력서·자기소개서 원문과 D의 면접 답변 원본은 E가 직접 수정하지 않고 개선안을 별도로 제공한다.
B의 공고 분석 결과는 첨삭 맥락으로 참조하지만 공고 분석 결과 자체는 B가 관리한다.
크레딧 차감은 AI 요청 성공 여부와 공통 사용량 로그를 기준으로 처리한다.
결제 요청만으로 크레딧을 지급하지 않고 결제 성공 검증 후 지급한다.
공통 결제 정책, 환불 정책, 사용량 로그 스키마 변경은 공통 영역 규칙을 따른다.
```

### 포트폴리오 설명 포인트

```text
AI를 활용한 면접 답변, 자기소개서, 이력서, 포트폴리오 설명 첨삭 기능을 개발하고,
AI 기능 사용량에 따른 크레딧 차감, 결제 내역 관리, 사용량 기반 요금제 추천 구조를 구현했다.
```

## 9. F 담당: 커뮤니티/고객센터/공지/알림

F는 사용자 간 정보 공유와 운영 커뮤니케이션 영역을 책임진다. 커뮤니티 게시글, 댓글, 신고, 고객문의,
공지, FAQ, 알림, 회사/서비스 소개, 법적 문서까지 사용자가 서비스 안에서 정보를 찾고 도움을 받는 흐름을 관리한다.
AI 기능은 요약·추천·분류·답변 초안 생성까지 지원하되, 신고 처리와 고객문의 최종 답변은 운영자가 확정한다.

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

사용자 기능 상세 책임:

| 영역 | 상세 책임 |
| --- | --- |
| 커뮤니티 | 게시글 목록, 상세, 작성, 수정, 삭제, 카테고리, 검색, 정렬, 조회수를 관리한다. |
| 취업/면접 후기 | 기업명, 직무명, 면접 유형, 난이도, 실제 질문, 합격 전략 정보를 구조화해 보여준다. |
| 직무별 질문 공유 | 직무, 기술스택, 기업 유형별 질문과 답변을 탐색할 수 있게 한다. |
| 댓글 | 댓글 작성, 수정, 삭제, 익명 표시, 댓글 수, 신고 연결을 관리한다. |
| 좋아요/북마크 | 게시글 반응, 북마크, 관심 기반 추천에 필요한 사용자 상호작용을 관리한다. |
| 신고 | 게시글/댓글 신고 사유, 신고 상태, 처리 결과, 중복 신고 여부를 관리한다. |
| 고객센터 | FAQ, 공지사항, 문의하기, 이용 가이드, 문의 처리 상태를 제공한다. |
| 알림 | 문의 답변, 공지, 커뮤니티 반응, 시스템 안내 알림을 목록과 읽음 상태로 관리한다. |
| 회사/서비스 소개 | 회사 소개, 서비스 소개, 브랜드 정보, 주요 기능 설명 콘텐츠를 관리한다. |
| 법적 문서 | 이용약관, 개인정보처리방침, 환불 정책 등 법적 문서 페이지를 관리한다. |

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

AI 처리 원칙:

```text
면접 후기 요약은 원문 게시글을 보존하고 요약 결과를 별도 데이터로 취급한다.
실제 면접 질문 추출은 사용자가 작성한 후기에서 질문 문장과 추론된 유사 질문을 구분한다.
게시글 추천은 사용자의 관심 정보와 행동 데이터를 사용하되 민감 정보는 추천 근거로 노출하지 않는다.
부적절 게시글/신고 분류는 운영자 판단을 돕는 제안이며 자동 제재로 처리하지 않는다.
고객문의 답변 초안은 상담원이 검토하는 초안이고, 정책·환불·개인정보 이슈는 확정 답변으로 자동 발송하지 않는다.
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
추천 태그: 백엔드, 기술면접, 데이터베이스, 협업
신고 분류 예시: 개인정보 노출 의심, 운영자 검토 필요
```

### 관리자 기능

```text
커뮤니티 게시글 목록 조회
게시글 검색과 필터
게시글 상세와 댓글 확인
신고 목록과 신고 상세 확인
신고 상태 변경과 운영 메모
공지사항 목록과 상세 관리
FAQ 목록과 상세 관리
고객문의 목록과 답변 처리
알림 발송과 발송 이력 확인
회사/서비스 소개 콘텐츠 관리
법적 문서 콘텐츠 관리
커뮤니티/고객센터 프롬프트 운영 확인
```

관리자 완료 기준:

| 영역 | 상세 기준 |
| --- | --- |
| 게시글 관리 | 카테고리, 작성자, 기업명, 직무명, 신고 여부, 작성일을 기준으로 목록과 상세를 조회한다. |
| 댓글 관리 | 게시글별 댓글, 신고 여부, 삭제 상태, 작성 시점을 확인할 수 있어야 한다. |
| 신고 관리 | 신고 사유, AI 분류 결과, 처리 상태, 처리자, 처리 시점, 운영 메모를 관리한다. |
| 공지/FAQ 관리 | 제목, 내용, 노출 여부, 정렬 순서, 작성/수정 시점을 관리한다. |
| 고객문의 관리 | 문의 유형, 문의 내용, AI 답변 초안, 상담원 답변, 처리 상태, 처리 시점을 관리한다. |
| 알림 관리 | 발송 대상, 알림 유형, 내용, 발송 상태, 읽음 여부를 확인한다. |
| 콘텐츠 관리 | 회사 소개, 서비스 소개, 약관·정책 문서의 노출 상태와 수정 이력을 확인한다. |

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
frontend/src/admin/features/prompts/community
frontend/src/admin/features/prompts/support

backend/src/main/java/com/careertuner/community
backend/src/main/java/com/careertuner/community/ai
backend/src/main/java/com/careertuner/community/ai/prompt
backend/src/main/java/com/careertuner/support
backend/src/main/java/com/careertuner/support/ai
backend/src/main/java/com/careertuner/support/ai/prompt
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
backend/src/main/java/com/careertuner/admin/prompt/community
backend/src/main/java/com/careertuner/admin/prompt/support
```

`frontend/src/admin/features/prompts`와 `backend/src/main/java/com/careertuner/admin/prompt`의 루트 셸은 공통 영역이다.
F는 그 하위의 `community`, `support` 프롬프트만 담당한다. 회사/서비스 소개와 법적 문서는 AI 기능이 아니라
콘텐츠 운영 기능으로 관리한다.

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

데이터 책임:

```text
community_post: 게시글, 카테고리, 제목, 본문, 기업명, 직무명, 면접 유형, 난이도, 익명 여부
community_comment: 게시글별 댓글, 작성자, 내용, 작성/수정 시점
community_reaction: 좋아요, 북마크, 관심 기반 추천에 필요한 사용자 반응
community_report: 신고 대상, 신고 사유, AI 분류 결과, 처리 상태, 처리자, 처리 시점
notice: 공지사항 제목, 내용, 노출 여부, 작성/수정 시점
faq: FAQ 질문, 답변, 카테고리, 노출 여부, 정렬 순서
support_ticket: 문의 유형, 문의 내용, AI 답변 초안, 상담원 답변, 처리 상태
notification: 알림 유형, 대상 사용자, 내용, 발송 상태, 읽음 여부
ai_usage_log: F 영역 후기 요약, 태그 추천, 질문 추출, 게시글 추천, 신고 분류, 문의 답변 초안 요청의 사용량 기록
```

연동 경계:

```text
D는 F가 추출한 실제 면접 질문을 참고 데이터로 사용할 수 있지만 커뮤니티 원문과 신고 처리는 F가 관리한다.
C는 사용자의 관심 직무나 지원 경향을 추천 입력으로 제공할 수 있지만 커뮤니티 추천 노출 정책은 F가 관리한다.
E의 환불·결제 정책 문의는 고객문의 답변 초안에 활용할 수 있으나 최종 정책 판단은 E와 운영자가 확인한다.
공지, FAQ, 법적 문서, 회사/서비스 소개 콘텐츠의 라우팅이나 공통 레이아웃 변경은 공통 영역 규칙을 따른다.
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
backend/src/main/java/com/careertuner/ai/prompt 공통 프롬프트 엔진 예약 경계(package-info만 존재)
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

## 12. 관리자 패키지 담당 지정

현재 저장소의 관리자 패키지는 아래처럼 담당자를 지정한다. 표는 소유권을 나타내며 구현 완료표가 아니다.
일부 경로는 실제 API와 화면이 구현되어 있고 일부는 예약 패키지만 남아 있으므로, 완료 여부는 소스·라우트·권한
테스트를 함께 확인한다. 분석 통계 명칭은 `analytics`로 통일하고 해당 패키지만 사용한다.

| 관리자 패키지 | 담당 | 사용 목적 |
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

## 13. 자체 LLM 파인튜닝 트랙 (현재 상태)

초기에는 34개 목표 AI 기능의 외부 API 의존을 줄이기 위한 공통 LoRA 계획으로 출발했지만, 현재 런타임은
이미 자체 모델·hosted provider·규칙 엔진·mock을 도메인 특성에 맞게 조합한다. 모든 담당자가 하나의 베이스,
하나의 서빙 방식, 하나의 폴백 순서를 강제하지 않는다. 초기 의사결정의 근거와 실험 가정은
`docs/planning/자체LLM_팀_도입안.md`, 목표 운영 계약은 `docs/planning/담당별_자체LLM_운영안.md`, 현재
기본값과 환경변수는 `backend/README.md` 및 각 `ml/*/README.md`를 따른다.

### 담당자별 현재 연결

| 담당 | 현재 자체/로컬 모델 연결 | 기본 상태와 hosted 안전망 |
| --- | --- | --- |
| A | `qwen3-profile-lora-v4` 선택 경로, 이력서 구조화 `qwen3:8b` | 평가 LoRA는 기본 비활성; Claude → OpenAI → 규칙. 구조화는 Ollama → Claude/OpenAI → 최소 추출 |
| B | `careertuner-b-jobposting-r1` Ollama | 공고는 LOCAL 우선, 기업은 OpenAI 우선; Claude/OpenAI/LOCAL을 거쳐 `self-rules-v1` |
| C | `careertuner-c-career-strategy-3b` | 기본 provider는 hosted이며 OSS URL과 provider를 설정하면 자체 모델을 시도; 최종 결정적 mock |
| D | Qwen2.5-3B LoRA/Ollama `interview-3b` 산출물과 OSS gateway | 평가 기본은 hosted, OSS는 명시 설정; 실패 시 Claude → OpenAI → mock |
| E | `careertuner-e-correction-3b:delivery-s-f16-20260708` | 기본 provider는 OpenAI; self 설정 시 자체 3B + repair → Anthropic → OpenAI |
| F | `careertuner-mod`, `gemma4`, `qwen2.5vl:7b`, 챗봇 `qwen3:8b` | Ollama 우선; 기능별 Claude/OpenAI/mock 폴백 |

### 공통 영역과 산출물 정책

- `backend/.../ai/common`에는 provider tier, 시간예산, GPU permit, Ollama endpoint resolver, DB 런타임 설정만 둔다. 도메인 transport·prompt·validator·fallback은 담당 도메인에 둔다.
- 베이스 모델과 라이선스는 모델별로 확인한다. 현재 C/D/E의 Qwen2.5-3B 계열은 연구 라이선스 제약을 모델 카드에 명시하고, 상용 전환 전 별도 허가 또는 베이스 교체와 회귀검증이 필요하다.
- 메인 저장소 `ml/`에는 재현 코드, validator, 소형 fixture만 둔다. 장문 보고서는 `docs/ai-reports`, raw output·반복 benchmark·대형 모델 산출물은 `docs/ai-artifacts` 서브모듈에 둔다.
- adapter, merged weight, GGUF 같은 대형 파일과 raw 생성 결과는 메인 저장소에 커밋하거나 Git LFS로 누적하지 않는다. 운영 DB에도 학습 원문이나 모델 가중치를 적재하지 않는다.

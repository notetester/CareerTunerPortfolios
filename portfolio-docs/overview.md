# 프로젝트 개요

CareerTuner는 채용공고에 맞춰 지원자의 스펙과 면접 답변을 조정하는 AI 취업 전략·가상 면접 준비 플랫폼입니다. 기존 취업 사이트가 공고를 나열하는 데 그친다면, CareerTuner의 관리 단위는 공고가 아니라 **지원 건(Application Case)** 입니다. 하나의 기업·직무·공고 조합을 지원 건으로 만들고, 그 안에서 공고 분석 → 기업 분석 → 내 스펙 비교(적합도) → 예상 질문 → AI 모의면접 → 답변 첨삭까지의 준비 흐름을 하나로 묶어 관리합니다.

여러 지원 건이 쌓이면 반복적으로 부족한 역량, 직무 선택 패턴, 면접 점수 추이 같은 장기 취업 경향을 분석해 다음 지원 방향을 제안합니다. 즉 단발성 도구가 아니라, 지원 건을 축으로 취업 준비 전 과정을 누적·추적하는 워크스페이스를 지향합니다.

## 한 줄 요약

| 항목 | 내용 |
| --- | --- |
| 제품 정체성 | 채용공고 맞춤 취업 전략 + AI 가상 면접 플랫폼 |
| 핵심 단위 | 지원 건(Application Case) — 공고가 아니라 기업·직무·공고 조합 |
| 준비 흐름 | 공고→분석→스펙 비교→예상 질문→모의면접→첨삭→장기 경향 분석 |
| 백엔드 | Spring Boot 4.1.0 · Java 21 · MyBatis · MySQL 8 · Spring Security (REST `/api/**`) |
| 프런트 | React 19 · Vite 8 · TypeScript · Tailwind CSS v4 · react-router 7 (사용자 SPA + 관리자 SPA) |
| 멀티플랫폼 | 반응형 웹 · PWA · Capacitor(Android/iOS) · C++17/Qt/QML 데스크톱 |
| AI | 도메인별 provider 분기 + 폴백 체인(외부 API ↔ 자체 파인튜닝 LLM) |
| 규모 | 백엔드 Java 약 980개 파일 · REST 컨트롤러 69개 · MyBatis 매퍼 XML 72개 · DB 테이블 72개 · 프런트 TS/TSX 약 400개 |
| 팀 | 6인(A~F) 기능별 수직 분담 |
| 데모 | `mock` 빌드(백엔드 없이 동작) — 웹 데모 / Android APK / iOS 시뮬레이터 |

## 주요 기능

- **지원 건 관리**: 기업·직무·마감일·공고 원문(텍스트/PDF/이미지/URL)을 등록하고, 진행 상태와 보관·삭제(소프트 삭제)를 지원 건 단위로 관리.
- **공고·기업 분석**: 공고문에서 필수/우대 조건, 담당 업무, 면접 포인트를 구조화 추출하고 기업 현황을 요약.
- **스펙 비교·적합도**: 프로필 스냅샷과 공고 조건을 비교해 적합도 점수, 매칭 근거, 부족 근거를 설명 가능한 형태로 제공.
- **AI 가상 면접**: 지원 건 기반 세션에서 예상 질문·꼬리 질문 생성, 답변 저장(텍스트/음성/영상), 질문별 평가, 면접 리포트 생성.
- **AI 첨삭**: 면접 답변·자기소개서·이력서·포트폴리오 설명을 지원 맥락에 맞게 개선.
- **취업 경향 분석·대시보드**: 여러 지원 건을 종합해 반복 부족 역량, 면접 점수 변화, 다음 지원 방향을 요약.
- **AI 오케스트레이터(자동 준비)**: 사용자 요청을 동적 plan으로 분해하고 의존 그래프 기반으로 여러 준비 단계를 병렬 실행(SSE 진행률 스트리밍).
- **관리자 콘솔·결제/크레딧**: 회원·결제·AI 사용량·게시판/신고·프롬프트 템플릿 운영과 크레딧 기반 과금.

## 핵심 구현

### 지원 건 중심의 백엔드 도메인 지도

백엔드는 `com.careertuner` 아래 도메인 패키지로 나뉘며, 각 도메인은 `controller → service → mapper → domain (+ dto)` 4계층을 따릅니다. 실제 소스에 존재하는 사용자 도메인은 다음과 같습니다.

```text
auth · user · profile · consent · settings           (회원/프로필/동의)
applicationcase · jobposting · jobanalysis · companyanalysis   (지원 건/공고·기업 분석)
fitanalysis · analysis · dashboard · home            (적합도/취업 분석/대시보드)
interview · file                                     (가상 면접/파일)
correction · billing · payment · credit              (첨삭/결제/크레딧)
community · support · notification · legal · company · serviceinfo
ai                                                   (autoprep·chat·intake·prompt·common)
```

관리자 API는 `com.careertuner.admin.<domain>` 아래에 사용자 도메인과 대칭 구조로 존재하며(`admin/interview`, `admin/fitanalysis`, `admin/billing` 등), `aiusage`·`prompt`·`log`·`superadmin` 같은 운영 전용 패키지도 포함합니다. 영속성은 JPA를 쓰지 않고 **MyBatis만** 사용하며, `@Mapper` 인터페이스 + `resources/mapper/**/*.xml` 조합으로 구현합니다. 모든 응답은 `common/web/ApiResponse<T>` envelope으로 감쌉니다.

### 지원 건 준비 흐름을 담는 화면 모듈

프런트는 `frontend/src/features/<기능>/{pages,components,api,hooks,types}` 구조입니다. 실제 존재하는 사용자 기능 폴더는 다음과 같습니다.

```text
auth · profile · settings · onboarding
applications · interview · correction · analysis · dashboard · home
community · support · notification · billing · legal · company · service
autoprep · landing
```

지원 건 상세 화면(`applications`)이 핵심 작업공간으로, 개요·공고 분석·기업 분석·스펙 비교·전략·학습/자격증 추천·예상 질문·가상 면접 연결·첨삭 기록·리포트 패널을 한 곳에 모읍니다. 관리자 SPA는 별도 앱이 아니라 `frontend/src/admin/features/<기능>/` 아래에서 동일한 라우터에 `/admin/**`로 마운트됩니다. 관리자 기능 폴더는 사용자 기능과 대칭으로 `interviews`·`interview-reports`·`fit-analysis`·`payments`·`moderation`·`prompts` 등 30여 개가 존재합니다.

### AI 가상 면접 세션 파이프라인(D 영역 예시)

면접 도메인은 세션 생성부터 리포트까지를 하나의 흐름으로 구현합니다. 컨트롤러는 `InterviewController`(`/api/interview`), 미디어 업로드용 `InterviewMediaController`, 지식/학습 운영용 `InterviewKnowledgeController`·`InterviewTrainingController`로 분리되어 있습니다. 세션·질문·답변은 `interview_session`, `interview_question`, `interview_answer` 테이블에 저장하고, 음성/영상 파일은 `file` 도메인의 `file_asset`으로 연결합니다. 예상 질문 생성 → 답변 저장 → 꼬리 질문 생성 → 질문별 평가 → 면접 리포트가 하나의 세션 상태 기계로 이어집니다.

### AI 오케스트레이터(자동 준비 흐름)

`ai/autoprep` 패키지는 사용자의 준비 요청을 자동 파이프라인으로 처리합니다. `AutoPrepPlanner`가 요청을 동적 `PrepPlan`으로 분해하고, `AutoPrepOrchestrator`가 단계 간 의존 그래프를 따라 실행하며, `AutoPrepIntakeService`가 정보가 부족하면 되묻고, `AutoPrepAttachmentLoader`가 첨부 파일을 게이팅합니다. 진행 상황은 `PrepProgress`로 SSE 스트리밍되어 프런트 `features/autoprep`에서 실시간 표시됩니다.

## 설계 결정과 트레이드오프

- **공고가 아니라 지원 건 중심**: 분석·면접·첨삭 결과를 흩어 두지 않고 지원 건에 귀속시켜, 장기 경향 분석과 재현성(어떤 프로필 스냅샷·공고 revision으로 분석했는지)을 확보합니다. 대신 도메인 간 참조 경계가 늘어나 소유권 규칙을 문서로 명시해야 합니다.
- **관리자 앱 분리 대신 단일 SPA**: 관리자 화면을 별도 앱으로 떼지 않고 `frontend/src/admin/` 아래에서 같은 라우터·인증·디자인 토큰을 공유합니다. 별도 도메인/네트워크 경계, 독립 릴리즈 주기 같은 요구가 실제로 생길 때만 분리를 재검토합니다.
- **MyBatis 전용**: ORM 자동 매핑 대신 SQL을 매퍼 XML로 직접 관리해 쿼리 제어권과 가독성을 확보합니다. 대신 반복 CRUD 보일러플레이트는 감수합니다.
- **AI provider 분기 + 폴백**: 도메인마다 외부 API(Anthropic/OpenAI)와 자체 파인튜닝 LLM 중 하나를 환경변수로 선택하고, 자체 모델 미서빙 환경에서는 외부 API로 자동 폴백합니다. 자체 모델을 실험·전환하는 동안에도 사용자 흐름이 끊기지 않도록 하는 트레이드오프입니다.
- **자체 모델 + 뉴로-심볼릭 방어**: 적합도 점수·지원 판단은 규칙 엔진이 소유하고 자체 LoRA는 설명만 생성하므로, 프롬프트 주입이 모델 문장을 흔들어도 판정은 바뀌지 않습니다. 자체 모델은 도메인별로 학습했고 현재 C·E는 학습 재료·adapter 백업으로 재현 가능한 반면 D·B는 원 학습 데이터 소실로 재현이 어렵습니다. 또한 베이스(Qwen2.5-3B)가 연구용(비상업) 라이선스라, 상용 기본값 승격에는 상업 친화 베이스 재학습이나 외부 provider 경로가 필요합니다.
- **포트폴리오 목적·비출시**: 결제·약관·무료 티어 등 정책 흐름은 완결적으로 구현하되 실제 스토어 출시는 목표에 두지 않아, 데모는 백엔드 없이 mock으로 동작합니다.

## 데이터 · 연동

- **DB**: MySQL 8, 스키마는 `backend/src/main/resources/db/schema.sql`(테이블 72개). 지원 건 계열(`application_case`, `job_posting`, `job_analysis`, `company_analysis`), 적합도(`fit_analysis`), 면접(`interview_session`, `interview_question`, `interview_answer`), 회원/프로필(`users`, `user_profile`, `user_consent`), 결제/크레딧(`payment`, `credit_product`, `credit_transaction`), 공통 `ai_usage_log` 등으로 구성됩니다.
- **AI provider(값이 아니라 키 이름 기준)**: 공통 클라이언트는 `ai/common`·`ai/prompt`로 통일하고, 도메인별로 provider를 분기합니다.
  - 면접 1차 provider는 Anthropic Claude Haiku(`ANTHROPIC_MODEL`), 키가 없으면 OpenAI(`OPENAI_MODEL`)로 폴백.
  - 답변 평가(`INTERVIEW_EVAL_PROVIDER`), C 적합도/취업 분석(`CAREERTUNER_ANALYSIS_AI_PROVIDER`), B 공고 분석(`B_ANALYSIS_OLLAMA_MODEL`), 프로필(`PROFILE_AI_FINETUNED_MODEL`)은 `openai`(기본/폴백)와 `oss`(자체 파인튜닝) 중 선택.
  - 자체 LLM은 Ollama(`AI_OLLAMA_BASE_URL`, 기본값 로컬 `localhost:11434`, 운영 시 원격 4090 GPU) 기반으로 서빙하며, base-url 미설정 시 OSS 비활성으로 기존 외부 API 동작을 유지합니다.
- **사용량 로깅**: 모든 AI 호출은 공통 규약으로 `ai_usage_log`에 기록하고, 결제/크레딧 도메인(E)이 사용량·과금 화면을 담당합니다.

## 팀 구성 — 6인 수직 분담(A~F)

기능별 수직 분담제로, 각 담당자는 자기 기능의 사용자 화면·사용자 API·관리자 화면·관리자 API·AI 기능·주요 DB를 함께 책임집니다. 공통 라우팅, 공통 컴포넌트, 공통 API, DB 구조, 인증/권한, AI 프롬프트 공통 엔진은 팀장 Owner 영역입니다.

| 담당 | 영역 | 대표 AI 기능 | 주요 백엔드 도메인 |
| --- | --- | --- | --- |
| A | 회원/프로필/설정 | 이력서 요약·기술스택 추출·프로필 진단 | `auth`, `user`, `profile`, `consent`, `settings` |
| B | 지원 건/공고문/공고·기업 분석 | 공고문 분석·필수/우대 조건 추출·기업 요약 | `applicationcase`, `jobposting`, `jobanalysis`, `companyanalysis` |
| C | 홈/스펙 비교/취업 분석/대시보드 | 적합도 분석·부족 역량·장기 경향·대시보드 요약 | `fitanalysis`, `analysis`, `dashboard`, `home` |
| D | 가상 면접/면접 리포트 | 예상·꼬리 질문·면접관 진행·답변 평가·리포트 | `interview`, `file` |
| E | 첨삭/결제/크레딧 | 답변·자소서·이력서·포트폴리오 첨삭 | `correction`, `billing`, `payment`, `credit` |
| F | 커뮤니티/고객센터/공지/알림 | 후기 요약·게시글 태그·문의 답변 초안 | `community`, `support`, `notification`, `legal`, `company` |

## 데모에서 볼 수 있는 것

라이브 데모는 백엔드 없이 동작하는 **mock 빌드**로, 로그인 화면에 아무 값이나 입력하면 데모 계정으로 진입합니다. 지원 건 목록·상세, 공고 분석·적합도·전략 패널, AI 가상 면접 세션(질문 출력·답변·평가·리포트), 첨삭, 대시보드/분석 요약, 관리자 콘솔까지 실제 화면 흐름을 mock 데이터로 확인할 수 있습니다. 웹 데모 외에 Android APK, iOS 시뮬레이터 빌드도 제공됩니다.

## 사용 기술

- **백엔드**: Spring Boot 4.1.0, Java 21, Spring Security, MyBatis, MySQL 8, springdoc-openapi. REST는 전부 `/api/**` 하위, 응답은 `ApiResponse<T>` envelope.
- **프런트엔드**: React 19, Vite 8, TypeScript, Tailwind CSS v4, shadcn/ui, react-router 7. 사용자 SPA와 관리자 SPA를 단일 앱에서 라우팅.
- **멀티플랫폼**: 반응형 웹 · PWA · Capacitor(Android/iOS) · C++17/Qt/QML 데스크톱 앱.
- **AI**: Anthropic/OpenAI 외부 API와 Ollama 기반 자체 파인튜닝 LLM(LoRA, 원격 4090 GPU)을 도메인별 provider 분기·폴백으로 운용. 공통 프롬프트 엔진과 `ai_usage_log` 사용량 로깅.

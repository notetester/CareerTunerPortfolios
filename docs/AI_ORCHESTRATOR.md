# AI 오케스트레이터 현재 구현 가이드

> 상태: active
> 담당: 공통 AutoPrep 계약 + A~F 도메인 handler
> 마지막 소스 대조: 2026-07-14, `dev` 기준 `23bb4d22`
> 다시 검증할 조건: `ai/autoprep`, `ai/intake`, 통합 챗봇, handler 또는 세 플랫폼 진입 화면 변경

AI 오케스트레이터(AutoPrep)는 “지원 준비를 통째로 해줘” 같은 요청을 지원 건과 준비 모드로 확정한 뒤, A~F 기능을 하나의 실행 계획으로 묶어 진행 상태를 보여 주는 공통 조정 계층이다. 각 도메인의 판단·저장·AI provider를 다시 구현하지 않고 기존 서비스를 호출한다.

과거 설계와 구현 전제의 누적 기록은 [2026-06 설계 스냅샷](archive/2026-06/ai-orchestrator-design-snapshot.md)에 보관한다. 그 문서의 “예정”, “미구현”, 모델명은 현재 상태로 읽지 않는다.

## 사용자 진입점

| 플랫폼 | 현재 진입 | 구현 |
| --- | --- | --- |
| 웹 | 홈의 AutoPrep launcher/modal | `frontend/src/features/autoprep/` |
| 네이티브 모바일 | 검색창 중심 AppHome 대화 스레드 | `frontend/src/features/onboarding/AppHome.tsx` |
| 데스크톱 | 홈 AutoPrep 카드와 SSE 진행 표시 | `desktop/core/AutoPrepRunner.*`, QML home |

모바일 AppHome의 입력은 통합 챗봇으로 흐르고, 확정된 지원 건·모드는 AutoPrep 실행으로 연결된다. 대화 목록과 복원은 서버 `conversationId`를 사용한다. 데스크톱은 `/intake`와 POST SSE `/run/stream`을 직접 소비한다.

## 실행 흐름

```text
사용자 요청
  ├─ 단일 단계 UI: POST /api/auto-prep/intake
  └─ 대화형 UI: POST /api/chatbot/ask 또는 /api/chatbot/intake/ask
          ↓
지원 건·면접 모드·요청 파트 확정
          ↓
AutoPrepPlanner: PrepPlan 생성 + 의존 단계 보강
          ↓
AutoPrepOrchestrator: 의존 그래프 병렬 실행
          ↓
plan → part-start → substep → part-done → done/error (SSE)
```

`AutoPrepPlanner`는 요청에서 회사·직무·면접 모드와 필요한 파트를 분류한다. 명시한 `applicationCaseId`를 우선하고, 회사명이 있으면 본인 지원 건에서 매칭한다. 파싱 실패 시 잘못 추측한 단일 단계로 축소하지 않고 빈 슬롯·전체 파트라는 보수적 기본값으로 돌아간다. 실행 전 `AutoPrepIntakeService`가 필요한 지원 건과 모드를 되묻는다.

## 여섯 파트와 의존성

| key | 사용자 기능 | handler | 의존 |
| --- | --- | --- | --- |
| `PROFILE` | 프로필·역량 준비 | `ProfilePrepHandler` | 없음 |
| `JOB` | 공고·기업 분석 | `JobPrepHandler` | 없음 |
| `FIT` | 직무 적합도·전략 | `FitPrepHandler` | `JOB` |
| `WRITE` | 자기소개서·답변 첨삭 | `WritePrepHandler` | 없음 |
| `INTERVIEW` | 면접 질문 준비 | `InterviewPrepHandler` | `JOB` |
| `COMMUNITY` | 관련 후기·커뮤니티 자료 | `CommunityPrepHandler` | 없음 |

독립 파트는 동시에 시작하고 `FIT`·`INTERVIEW`는 `JOB` 완료 후 실행한다. 사용자가 일부 파트만 요청해도 필요한 선행 파트는 planner가 자동 포함한다. handler가 없거나 비활성이면 `SKIPPED`, 도메인 처리 실패는 `FAILED`로 기록하고 다른 독립 파트는 계속 진행한다.

오케스트레이터가 공통 점수나 답변을 대신 만들지는 않는다. 각 handler는 해당 도메인 service를 호출하므로 provider, 모델, fallback, DB 쓰기와 검증 규칙의 소유권은 A~F 런타임에 남는다.

## API 계약

모든 일반 응답은 `ApiResponse<T>` envelope를 사용한다.

| 메서드 | 경로 | 역할 |
| --- | --- | --- |
| POST | `/api/auto-prep/intake` | 요청을 해석하고 `ready`, 다음 질문, 지원 건·모드 후보 반환 |
| POST | `/api/auto-prep/run` | 같은 계획을 동기 실행해 전체 결과 반환 |
| POST | `/api/auto-prep/run/stream` | 계획과 파트 진행을 SSE로 반환 |
| POST | `/api/auto-prep/run/cancel` | 현재 사용자의 `runId` 실행에 협력적 취소 요청 |
| POST | `/api/auto-prep/job-posting-case/upload` | 업로드 파일 기반 지원 건 생성 또는 기존 건 재사용 |
| POST | `/api/chatbot/intake/ask` | `conversationId` 기반 대화형 슬롯 수집 |
| POST | `/api/chatbot/ask` | 통합 챗봇에서 일반 대화·인테이크·온보딩 라우팅 |
| GET | `/api/chatbot/conversations` | 본인 대화 세션 목록 |
| GET | `/api/chatbot/conversations/recent` | 최근 세션 복원 |
| GET | `/api/chatbot/conversations/{id}/messages` | 소유권 확인 후 메시지 복원 |
| DELETE | `/api/chatbot/conversations/{id}` | 본인 세션과 연관 intake 상태 정리 |

SSE event는 `plan`, `part-start`, `substep`, `part-done`, `done`, `error`다. 서버 타임아웃은 300초이며 클라이언트도 이 상한과 네트워크 끊김을 terminal 상태로 처리해야 한다. 내부 예외 문자열은 사용자에게 그대로 보내지 않는다.

## 대화·슬롯 저장 경계

- `chatbot_conversation_memory`: 사용자, 지원 건, 제목, 메시지 JSON과 갱신 시각을 저장한다.
- `chatbot_intake_slot`: `conversationId`별 확정 지원 건·모드·원 요청·상태를 저장해 서버 재시작 뒤 복원한다.
- 지원 건이 새로 확정되면 일반 잡담 세션과 준비 세션이 섞이지 않도록 필요한 대화 구간을 새 `conversationId`로 fork한다.
- 기존 `conversationId`를 다시 보낼 때는 소유자를 확인해 다른 사용자의 대화를 읽거나 이어 쓰지 못하게 한다.
- 대화 삭제 시 논리 연관된 intake slot도 함께 정리해 고아 데이터를 남기지 않는다.

현재 이 저장은 대화와 intake 연속성의 정본이다. 각 실행의 모든 SSE event와 파트 결과를 불변 이력으로 보관하는 별도 AutoPrep 실행 원장은 아니다. 장기 감사가 필요하면 대화 메모리에 무제한으로 덧붙이지 말고 실행 이력 스키마를 별도 설계한다.

## 파일·동의·보안

- `/api/auto-prep/**`는 로그인 사용자와 AI 데이터 동의를 요구하며 실제 실행은 이력서 분석 동의도 요구한다.
- `/run/cancel`은 요청 사용자의 `runId`에만 적용한다. 스트림 등록보다 취소가 먼저 도착해도 조기 취소 상태를 보존해 이후 실행을 중단한다.
- `/job-posting-case/upload`는 multipart의 `file`, `sourceType`, `pendingFileId`와 선택적 `attachmentFileIds`를 받는다. 사용자별 `pendingFileId`를 멱등 키로 사용해 재전송 때 중복 지원 건을 만들지 않고 기존 건을 반환한다.
- 첨부는 `file_asset` 소유권과 plan별 개수 정책을 확인한다. 텍스트, 텍스트 PDF, DOCX처럼 loader가 지원하는 형식만 본문으로 사용한다.
- 업로드 후 실행 전에 이탈한 pending AutoPrep 파일은 프런트 계정 경계와 백엔드 cleanup이 정리한다.
- 지원 건 조회는 항상 인증 사용자 범위에서 수행한다. 클라이언트가 보낸 회사명이나 conversation id만으로 다른 사용자 데이터를 선택하지 않는다.
- 한 파트의 실패가 전체 응답에 내부 stack trace를 노출하거나 다른 파트의 성공 결과를 버리게 하지 않는다.

## provider와 장애 동작

planner의 의도 분류는 `InterviewLlmGateway`를 사용하지만, 실제 여섯 파트는 각 도메인의 provider 체인을 따른다. 따라서 이 문서에 공통 “현재 모델명”을 고정하지 않는다. 현재 값은 백엔드 설정, 해당 모듈 README/model card, 관리자 AI 사용량 로그를 함께 확인한다.

- 의도 분류 실패: 빈 슬롯·전체 준비로 안전 degrade하고 intake가 필요한 값을 묻는다.
- handler 비활성: 해당 파트를 `SKIPPED`로 표시한다.
- handler 실패: 해당 파트만 `FAILED`, 독립 파트는 계속한다.
- SSE 단절: 클라이언트가 미종결 파트를 실패로 정리하고 재시도를 제공한다.
- Sites의 AWS 전체 장애: 첫 불확실한 쓰기는 실패 처리하고, 사용자가 outage-demo에서 다시 시도한 경우에만 저장되지 않는 시연 sequence를 보여 준다.

## 변경 시 확인

```bash
cd backend
./gradlew test --tests '*AutoPrep*' --tests '*Intake*'

cd ../frontend
npm run typecheck
npm run test:pending-auto-prep-files
npm run test:chatbot-account-scope
```

데스크톱을 변경했다면 `desktop` 빌드와 `DesktopCoreTests`의 AutoPrep stream·계정 전환 테스트도 실행한다. 수동 확인에서는 다음을 최소 범위로 잡는다.

1. 지원 건이 없는 사용자, 하나인 사용자, 여러 개인 사용자의 되묻기
2. 전체 준비와 “면접만” 같은 부분 준비의 plan·의존성
3. 한 handler 실패 시 나머지 파트 완주와 terminal SSE
4. 새로고침·재로그인·서버 재시작 후 본인 세션 복원
5. 다른 계정의 `conversationId` 접근 거부
6. 웹·모바일·데스크톱의 동일한 지원 건·모드·파트 결과 표시

## 관련 정본

- [아키텍처](ARCHITECTURE.md)
- [기능 모듈 구조](FEATURE_MODULE_STRUCTURE.md)
- [제품 구조](PRODUCT_STRUCTURE.md)
- [환경 프로파일](ENVIRONMENTS.md)
- [시연 준비도 검증](verification/README.md)

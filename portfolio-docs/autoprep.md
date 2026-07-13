# AI 오케스트레이터 (자동 준비)

CareerTuner의 여러 AI 기능(프로필 정리·공고 분석·적합도·자소서 교정·예상 면접 질문·커뮤니티 큐레이션)은 원래 각각 별도 화면에서 따로 실행하는 단위 기능입니다. AI 오케스트레이터는 이들을 하나의 흐름으로 조립해, `"네이버 백엔드 신입 통째로 준비해줘"` 같은 한 줄 자연어 요청만으로 필요한 준비 단계를 자동으로 판단하고 순서대로 실행합니다.

핵심은 세 가지입니다. LLM이 요청을 해석해 **동적 실행 계획(plan)** 을 세우고, 오케스트레이터가 계획의 단계를 **의존 그래프대로 병렬 실행**하며, 진행 과정을 **SSE로 실시간 스트리밍**합니다. 실행 전에는 챗봇형 **인테이크**로 부족한 정보를 되묻고, 첨부 파일은 요금제에 따라 **게이팅**합니다. 백엔드 진입점은 `com.careertuner.ai.autoprep` 패키지의 `AutoPrepController`(`/api/auto-prep/**`)입니다.

## 주요 기능

- 한 줄 자연어 요청을 해석해 실행할 준비 단계를 자동 선택 (전체 준비 / 특정 단계만)
- 단계 간 의존 관계(예: 적합도·면접은 공고 분석 선행)를 지켜 독립 단계는 동시에, 의존 단계는 선행 완료 후 병렬 실행
- SSE로 계획·단계 시작·서브스텝·단계 완료를 실시간 스트리밍
- 실행 전 인테이크 챗봇이 지원 건·면접 모드 같은 부족한 슬롯을 한 번에 하나씩 되묻기
- 첨부 파일 요금제 게이팅(무료 1개 / 유료 5개)과 텍스트 본문 추출
- 미구현·비활성 단계는 `SKIPPED`, 개별 단계 실패는 `FAILED`로 기록하고 항상 완주

## 핵심 구현

### 두뇌: 동적 실행 계획 생성 (`AutoPrepPlanner`)

`AutoPrepPlanner.plan()`은 한 줄 요청을 LLM 분류기(`INTENT_SYSTEM` 프롬프트 + JSON 스키마 강제)에 넘겨 회사·직무·면접 모드와 **실행할 파트 배열**을 추출합니다. LLM 호출은 면접 도메인의 공통 게이트웨이 `InterviewLlmGateway`를 재사용합니다. 이 게이트웨이는 `자체 파인튜닝 모델 → Claude → OpenAI` 폴백 체인을 구현하되, 자체 모델 활성화 화이트리스트(`FallbackInterviewLlmGateway.OSS_GENERATION_TASKS`)가 현재 비어 있어 의도 파싱은 실질적으로 Claude에서 시작합니다.

- `"통째로/전체/다"` 또는 모호한 요청이면 파트 배열을 비워 전체 파이프(`PrepPlan.defaultSteps()` = `PROFILE, JOB, FIT, WRITE, INTERVIEW, COMMUNITY`)로 처리하고, `"면접만"`처럼 콕 집으면 해당 단계만 선택합니다.
- 단계 간 의존(`FIT`·`INTERVIEW` ← `JOB`)은 LLM 판단에 맡기지 않고 `addWithDeps()`로 코드가 클로저 보강합니다. 예를 들어 면접만 선택해도 공고 분석(`JOB`)이 자동으로 끌려옵니다.
- 선택된 단계는 항상 `defaultSteps` 순서로 재정렬되고, 전체와 같으면 `intent`가 `FULL_PREP`, 아니면 `CUSTOM_PREP`으로 기록됩니다.
- 슬롯 파싱값보다 실제 지원 건이 우선입니다. `resolveCase()`는 명시 `caseId` → 회사명 매칭 → (회사 모호 시) 최근 지원 건 순으로 결정하며, 회사를 콕 집었는데 매칭 실패하면 엉뚱한 폴백 대신 `null`을 반환해 인테이크 되묻기를 유도합니다.
- LLM 파싱이 실패해도 빈 슬롯·전체 파트로 진행하도록 예외를 흡수합니다.

계획은 `PrepPlan(intent, slots, steps)` 레코드로 표현됩니다.

### 의존 그래프 병렬 실행 (`AutoPrepOrchestrator`)

오케스트레이터는 `PrepPlan.steps()`를 `DEPS`(`FIT`·`INTERVIEW`가 `JOB`에 의존) 기준으로 병렬 실행합니다. 각 단계를 `CompletableFuture`로 감싸되, 의존 단계의 future가 끝난 뒤(`CompletableFuture.allOf(depFutures).thenRunAsync(...)`) 실행되도록 그래프를 구성합니다.

- 독립 파트(프로필·공고·자소서·커뮤니티)는 동시에 출발하고, 적합도·면접은 공고 분석 완료 후 시작합니다.
- 단계 결과는 `prior`(`ConcurrentHashMap`)에 key별로 누적되어 다음 단계의 `PrepStepContext`로 전달됩니다.
- 실행 스레드풀은 데몬 스레드 기반 `newCachedThreadPool`이며 `@PreDestroy`에서 정리합니다.
- 동기(`run`)와 SSE(`runStream`)가 같은 `executeParallel()` 로직을 공유하고, 진행 보고 방식만 `PartListener`로 분리합니다. 동기 실행은 결과를 계획 순서로 재정렬해 한 번에 반환합니다.

### 확장 가능한 파트 핸들러 (`PrepStepHandler`)

각 준비 단계는 `PrepStepHandler` 인터페이스 구현체입니다. 오케스트레이터는 스프링이 주입한 `List<PrepStepHandler>`를 `key()`로 인덱싱(`byKey()`)해 호출하므로, 새 파트는 `@Component`로 핸들러를 추가하기만 하면 오케스트레이터 수정 없이 자동 등록됩니다.

- `key()` — 단계 식별자(`PROFILE`/`JOB`/`FIT`/`WRITE`/`INTERVIEW`/`COMMUNITY`)
- `enabled()` — 서빙·데이터 미준비 시 `false`를 반환하면 오케스트레이터가 `SKIPPED` 처리
- `handle(context, progress)` — `progress.substep(name, desc)`로 세부 작업을 보고하면 SSE로 실시간 전송

예: `InterviewPrepHandler`(D 담당)는 지원 건이 없으면 스킵하고, 있으면 `세션 준비`·`질문 생성` 두 서브스텝으로 나눠 실제 면접 세션 생성과 예상 질문 생성을 수행합니다. `FitPrepHandler`는 공고 분석 결과를 소스로 읽으므로 `JOB` 뒤에 실행되며, `근거 검색`·`채점`·`검증` 서브스텝을 보고합니다.

### SSE 진행 스트리밍

`POST /api/auto-prep/run/stream`은 `SseEmitter`(타임아웃 300초)로 `plan` → `part-start` → `substep` → `part-done` → `done` 순 이벤트를 흘려보냅니다. 병렬 파트가 동시에 emitter를 호출하므로 `send()`는 emitter 단위로 `synchronized` 처리하고, 클라이언트가 끊으면 `IOException`을 감지해 스트림을 중단합니다.

프런트(`autoPrepApi.ts`)에서는 SSE가 `ApiResponse` envelope를 타지 않으므로 공용 `api()` 래퍼 대신 `fetch`로 직접 스트림을 읽고 토큰을 수동 첨부하며, `\n\n` 경계로 이벤트를 파싱합니다. `useAutoPrepRun` 훅이 이벤트를 리듀서로 누적해 파트별 상태(`pending`/`running`/`done`/`skipped`/`failed`)를 관리합니다.

### 인테이크 되묻기 (`AutoPrepIntakeService`)

`POST /api/auto-prep/intake`는 요청을 미리 해석만 하고 실행하지 않습니다. 같은 두뇌(`AutoPrepPlanner.plan`)로 계획을 세운 뒤 부족한 슬롯을 순서대로 검사합니다.

1. 지원 건이 필요한 단계(`JOB`·`FIT`·`INTERVIEW`)가 있는데 `applicationCaseId`가 없으면 → `nextAsk="CASE"`, 후보 지원 건 목록 반환
2. 면접(`INTERVIEW`)이 있는데 모드 미지정이면 → `nextAsk="MODE"`, 6종 모드 선택지 반환
3. 슬롯이 다 차면 → `ready=true`

상태는 서버에 두지 않고 클라이언트가 슬롯(`applicationCaseId`·`mode`)을 누적해 매 턴 다시 보내는 stateless 방식입니다. `ready=true`가 되면 같은 요청을 그대로 `/run`(또는 `/run/stream`)에 넘깁니다. 자소서 교정(`WRITE`)이 예상되면 실행 지연을 줄이기 위해 교정 모델을 비동기 워밍업(`warmAsync`)합니다.

### 첨부 파일 게이팅 (`AutoPrepAttachmentLoader`)

요청의 `attachmentFileIds`를 요금제 한도 내에서 로드합니다. 사용자 플랜을 조회해 무료(FREE/BASIC) 1개, 유료(PRO/PREMIUM) 5개까지 허용하고, 한도 초과분은 로그만 남기고 버립니다. 텍스트형(`content-type`이 `text`로 시작)은 최대 12,000자까지 본문을 추출해 핸들러가 참조하도록 하며, 개별 파일 로드 실패는 건너뛰어 항상 진행합니다.

## 설계 결정과 트레이드오프

- **파트 선택은 LLM, 의존 보강은 코드.** 어떤 단계가 필요한지는 자연어 판단이 필요해 LLM에 맡기되, 단계 간 의존은 정확성이 중요하므로 코드가 클로저로 강제합니다. LLM이 `FIT`만 골라도 `JOB`이 자동으로 포함되어 근거 없는 실행을 막습니다.
- **회사 매칭 실패 시 폴백하지 않음.** 회사를 명시했는데 못 찾으면 최근 지원 건으로 넘어가지 않고 `null`을 반환해 인테이크가 되묻게 합니다. 엉뚱한 지원 건으로 준비가 진행되는 사고를 방지합니다.
- **완주 보장.** 미구현·비활성 단계는 `SKIPPED`, 실패는 `FAILED`로 기록하고 다른 단계는 계속 진행합니다. 한 파트가 깨져도 전체 흐름이 멈추지 않습니다.
- **stateless 인테이크.** 대화 상태를 서버에 저장하지 않고 클라이언트가 슬롯을 누적해 재전송합니다. 세션 저장소가 필요 없어 구현이 단순하지만, 대화 문맥은 클라이언트 책임입니다.
- **동기·SSE 로직 통합.** `run`과 `runStream`이 병렬 실행 코어를 공유하고 `PartListener`로만 갈라져, 진행 보고 방식이 바뀌어도 실행 로직은 한 곳만 유지합니다.
- **핸들러 자동 등록.** `List<PrepStepHandler>` 주입 + key 인덱싱 구조라 새 파트 추가 시 오케스트레이터를 건드리지 않습니다. 대신 파트 간 의존(`DEPS`)은 오케스트레이터·플래너 양쪽에 선언되어 있어 새 의존 추가 시 두 곳을 함께 손봐야 합니다.

## 데이터 · 연동

- **AI provider**: 요청 의도 파싱은 `InterviewLlmGateway`를 통합니다. 게이트웨이는 `자체 파인튜닝 모델 → Claude → OpenAI` 순으로 폴백하도록 설계돼 있으나, 자체 생성 모델 화이트리스트가 비어 있는 현재는 Claude(`ANTHROPIC_MODEL` 설정값)가 1차입니다. 2차 폴백 모델 이름은 `careertuner.interview.model.generation` 설정(기본 `gpt-5.4-mini`)으로 주입됩니다.
- **도메인 서비스 연동**: 각 핸들러가 담당 도메인 서비스를 호출합니다 — `InterviewService`(면접 세션·질문), `FitAnalysisService`(적합도), `CommunityPostService`(인기 글), 그리고 프로필·공고·자소서 교정 핸들러의 해당 서비스. 지원 건은 `ApplicationCaseService.list()`로 조회합니다.
- **파일**: 첨부는 `FileService.download()`로 로드하며, 사용자 플랜은 `UserMapper.findById()`로 확인합니다. 자소서 교정 예상 시 `CorrectionModelWarmupService`로 모델을 선워밍합니다.
- **엔드포인트**: `POST /api/auto-prep/intake`(되묻기), `POST /api/auto-prep/run`(동기), `POST /api/auto-prep/run/stream`(SSE). 모두 `@AuthenticationPrincipal AuthUser`로 사용자 컨텍스트를 받습니다.

## 사용 기술

- **백엔드**: Spring Boot / Java 21, `CompletableFuture` 기반 의존 그래프 병렬 실행, `SseEmitter` 서버 전송 이벤트, `ExecutorService`(데몬 캐시드 풀), `ApiResponse` envelope
- **AI**: LLM 의도 분류(JSON 스키마 강제), `자체 파인튜닝 모델 → Claude → OpenAI` 폴백 게이트웨이(현재 자체 모델 tier는 비활성)
- **프런트엔드**: React 19 / TypeScript, `fetch` 스트림 리더 기반 SSE 파싱, 커스텀 훅(`useAutoPrepRun`) + 리듀서 상태 관리
- **패턴**: 전략(핸들러 자동 등록), 리스너(동기·SSE 진행 보고 분리), stateless 멀티턴 인테이크

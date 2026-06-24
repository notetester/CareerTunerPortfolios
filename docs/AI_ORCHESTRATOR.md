# AI 오케스트레이터 — 설계·구현·협업 가이드

> 한 줄 요청("네이버 백엔드 신입 통째로 준비해줘")을 받아 취업 준비 6개 영역을 한 번에 자동으로 돌리는 기능이다.
> 이 문서는 그 **AI 오케스트레이터 전용 문서**다. 지금까지 설명이 `ARCHITECTURE.md`와 `면접 자율 에이전트 로드맵.md`에 흩어져 있어서, 한곳에 모아 새로 정리했다.

## 0. 이 문서는 누가 왜 읽나

| 담당 | 이 문서에서 봐야 할 곳 |
| --- | --- |
| **F (현정석)** — 인테이크 챗봇 | 5장(슬롯 명세), 10장(챗봇 확장 가이드). **사용자와 대화해서 무엇을 채워 넘겨야 하는지**가 핵심 |
| **C (이정국, 팀장)** — ChatGPT식 디벨롭 | 11장(세션 자동저장), 9장(폴백), 12장(키 공유·보안) |
| **D (정원일)** — 오케스트레이터·면접 | 전체. 코어 소유자 |
| A·B·E | 7장(파트 핸들러) — 자기 도메인이 오케스트레이터에 어떻게 물리는지 |

> 용어는 **AI 오케스트레이터**로 통일한다. 외부 제품명(다른 에이전트 서비스 이름 등)을 코드·문서·UI에 쓰지 않는다.

이 문서는 "지금 코드가 실제로 이렇게 돈다"(현재 구현)와 "앞으로 이렇게 갈 예정"(구상)을 구분해서 적는다. 구상 항목은 **(예정)** 표시를 붙였다.

---

## 1. 한눈에 보는 전체 그림

```
[사용자] "네이버 백엔드 신입 통째로 준비해줘"  (+ 자소서/이력서 파일 첨부 가능)
   │
   ▼
┌─────────────────────────────────────────────────────────────┐
│ ① 인테이크 챗봇  (F 담당 / 기존 커뮤니티 챗봇 확장 예정)         │
│    - 사용자와 대화하며 부족한 정보(슬롯)를 채운다             │
│    - "어느 지원 건?" "면접 모드는?" 같은 걸 되묻는다           │
│    - 다 채워지면 AutoPrepRequest 를 만들어 오케스트레이터에 넘김 │
└─────────────────────────────────────────────────────────────┘
   │  AutoPrepRequest { query, applicationCaseId, mode, coverLetterText, attachmentFileIds }
   ▼
┌─────────────────────────────────────────────────────────────┐
│ ② 두뇌 / 플래너  AutoPrepPlanner                              │
│    - 한 줄 문장을 AI로 해석 → 슬롯(회사·직무·모드) 추출        │
│    - "무슨 파트를 어떤 순서로 돌릴지" 실행계획(PrepPlan) 생성   │
└─────────────────────────────────────────────────────────────┘
   │  PrepPlan { intent, slots, steps:["PROFILE","JOB","FIT","WRITE","INTERVIEW","COMMUNITY"] }
   ▼
┌─────────────────────────────────────────────────────────────┐
│ ③ 오케스트레이터  AutoPrepOrchestrator                        │
│    - steps 를 "의존 그래프"대로 병렬 실행                      │
│    - 독립 파트는 동시에, 의존 파트는 선행 완료 후              │
│    - 진행 상황을 SSE(실시간 스트림)로 화면에 흘려보냄          │
└─────────────────────────────────────────────────────────────┘
   │         ┌──────────┬──────────┬──────────┐  (동시 출발)
   ▼         ▼          ▼          ▼          ▼
 PROFILE    JOB ───┐   WRITE     COMMUNITY
  (A)       (B)    │    (E)        (F)
                   │
            ┌──────┴──────┐  (JOB 끝난 뒤)
            ▼             ▼
           FIT         INTERVIEW
           (C)            (D)
   │
   ▼
[화면] AutoPrepWorkView — 6파트 타임라인이 실시간으로 채워짐 → "면접 시작" 버튼
```

- 괄호 안 알파벳(A~F)은 담당 팀원이다. 오케스트레이터는 각 팀원의 기존 도메인 서비스(공고 분석, 적합도, 면접 등)를 **그대로 호출**해서 묶는 역할이다. 새 AI 로직을 따로 만든 게 아니다.
- 현재 코드는 **②두뇌 + ③오케스트레이터**가 완성돼 있다. **①인테이크 챗봇**은 지금은 백엔드 `AutoPrepIntakeService`(되묻기 API)와 프론트 채팅 모달로만 돌아가고, F가 만드는 **대화형 인테이크 챗봇으로 확장 예정**이다.

---

## 2. 핵심 개념 3개

### 2.1 오케스트레이터(Orchestrator)란

오케스트라 지휘자처럼, **여러 AI 작업을 순서·동시성에 맞게 지휘**하는 코드다. 직접 연주(=AI 호출)는 각 악기(=도메인 서비스)가 하고, 지휘자는 "누가 먼저, 누가 동시에, 누가 나중에"만 통제한다.

### 2.2 슬롯(Slot)이란

작업을 시작하려면 채워야 하는 **빈칸**이다. 식당 주문서를 떠올리면 된다. "메뉴(무엇을 준비?), 어느 지원 건?, 면접 모드?"가 빈칸이고, 이 빈칸을 **슬롯**이라고 부른다. 인테이크 챗봇의 일은 결국 **이 빈칸 채우기**다. (자세한 슬롯 정의는 5장)

### 2.3 파트(Part) / 스텝(Step)이란

오케스트레이터가 돌리는 **하나의 작업 단위**다. 모두 6개가 있다.

| 키(step) | 이름 | 담당 | 하는 일 |
| --- | --- | --- | --- |
| `PROFILE` | 프로필·역량 정리 | A | 사용자 스펙·강점 요약 |
| `JOB` | 공고 분석 | B | 채용공고에서 핵심 요건·키워드 추출 |
| `FIT` | 적합도 분석 | C | 지원 건과 내 스펙의 적합도·전략 |
| `WRITE` | 자소서 교정 | E | 자소서 문장 첨삭 |
| `INTERVIEW` | 예상 면접 질문 | D | 면접 세션 생성 + 예상 질문 생성 |
| `COMMUNITY` | 커뮤니티 큐레이션 | F | 관련 인기글(면접 후기·팁) 모음 |

> 프론트의 표시용 메타데이터(아이콘·예상 소요시간)는 `frontend/src/features/autoprep/types/autoPrep.ts`의 `PREP_PARTS` 상수에 있다.

---

## 3. 전체 실행 흐름 (단계별)

### 3.1 인테이크 (되묻기)

- **엔드포인트**: `POST /api/auto-prep/intake`
- **백엔드**: `AutoPrepIntakeService.intake()`
- 한 줄 요청을 받아 두뇌(플래너)로 해석한 뒤, **부족한 슬롯이 있으면 한 번에 하나씩 되묻는다.**
- 되묻기 순서는 딱 2가지다:
  1. 지원 건이 필요한 파트(`JOB`·`FIT`·`INTERVIEW`)가 있는데 `applicationCaseId`가 없음 → `nextAsk = "CASE"` (지원 건 후보 목록을 함께 내려줌)
  2. 면접(`INTERVIEW`)이 있는데 모드가 없음 → `nextAsk = "MODE"` (모드 선택지 6개를 내려줌)
- 둘 다 채워지면 `ready = true`. 이때 실행으로 넘어간다.

> **상태 저장은 서버가 안 한다(stateless).** 클라이언트가 매 턴 슬롯(`applicationCaseId`·`mode`)을 누적해서 다시 보낸다. 그래서 현재 인테이크는 "되묻기 판정기"에 가깝다. 진짜 대화 맥락 기억은 F의 챗봇 메모리(10·11장)가 담당한다.

### 3.2 두뇌 / 플래너

- **백엔드**: `AutoPrepPlanner.plan()`
- 한 줄 문장(`query`)을 AI로 해석해서:
  - **슬롯 추출**: 회사·직무·모드를 뽑는다. (예: "네이버 백엔드 압박면접" → company=네이버, jobTitle=백엔드, mode=PRESSURE)
  - **파트 선택**: "통째로/전체/다" → 6개 전부. "면접만" → INTERVIEW만(+의존인 JOB 자동 추가). 불명확하면 전체.
- AI 호출은 면접 도메인의 공통 게이트웨이(`InterviewLlmGateway`)를 빌려 쓴다. 즉 **두뇌도 폴백 체인(9장)을 탄다.**
- 지원 건 결정 규칙: `applicationCaseId` 명시값이 1순위 → 없으면 회사명으로 매칭 → 그래도 없으면 되묻기 유도.

### 3.3 오케스트레이터 (병렬 실행)

- **엔드포인트**: `POST /api/auto-prep/run/stream` (실시간 SSE) 또는 `POST /api/auto-prep/run` (동기, 한 번에 결과)
- **백엔드**: `AutoPrepOrchestrator`
- 의존 그래프는 단 두 줄이다:

```java
// AutoPrepOrchestrator.java
private static final Map<String, List<String>> DEPS = Map.of(
        "FIT", List.of("JOB"),
        "INTERVIEW", List.of("JOB"));
```

- 해석: **FIT·INTERVIEW는 JOB(공고 분석)이 끝나야 시작**한다(공고 분석 결과가 필요하니까). 나머지 PROFILE·WRITE·COMMUNITY·JOB은 의존이 없으니 **동시에 출발**한다.
- 구현은 `CompletableFuture`(자바의 비동기 작업 객체)로 한다:

```java
CompletableFuture<Void> future = CompletableFuture
        .allOf(depFutures)          // 선행 파트들이 끝나길 기다렸다가
        .thenRunAsync(() -> runPart(...), sseExecutor);  // 이 파트 실행
```

  - `allOf(depFutures)`: 의존 파트의 작업이 다 끝날 때까지 대기. 의존이 없으면 빈 배열이라 즉시 통과 → 바로 시작.
  - `thenRunAsync(..., sseExecutor)`: 전용 스레드풀에서 실행 → 독립 파트들이 진짜로 병렬로 돈다.

### 3.4 SSE — 실시간 진행 표시

SSE(Server-Sent Events)는 **서버가 한 번 연결로 이벤트를 계속 밀어 보내는** 단방향 스트림이다. 채팅 타이핑처럼 진행 상황이 한 줄씩 화면에 뜨는 게 이거다.

오케스트레이터가 보내는 이벤트 순서:

| 이벤트 | 의미 | 데이터 |
| --- | --- | --- |
| `plan` | 실행계획 확정 | PrepPlan 전체 |
| `part-start` | 한 파트 시작 | `{ key }` |
| `substep` | 파트 안의 세부 작업 보고 | `{ key, name, desc }` |
| `part-done` | 파트 완료 | PrepStepResult |
| `done` | 전체 종료 | `{ message }` |

- 여러 파트가 동시에 돌기 때문에, 같은 연결로 이벤트를 보낼 때 충돌나지 않게 `send()`를 `synchronized(emitter)`로 묶었다.
- 프론트는 `frontend/src/features/autoprep/hooks/useAutoPrepRun.ts`의 `reduce()`가 이 이벤트들을 받아 화면 상태로 바꾼다. (`pending → running → done/skipped/failed`)
- SSE 타임아웃은 5분(`SSE_TIMEOUT_MS = 300_000`).

---

## 4. 한 가지를 꼭 기억하자 — "끝까지 완주한다"

오케스트레이터는 한 파트가 잘못돼도 **전체를 멈추지 않는다.** 결과 상태는 3가지다.

| 상태 | 언제 | 화면 |
| --- | --- | --- |
| `DONE` | 정상 완료 | 결과 + 요약 |
| `SKIPPED` | 핸들러가 없거나 비활성, 또는 필요한 슬롯/입력이 없음 | "준비중"으로 건너뜀 |
| `FAILED` | 실행 중 예외 발생 | 실패 표시, 하지만 나머지는 계속 |

`AutoPrepOrchestrator.runPart()`/`executeOne()`이 각 파트를 try-catch로 감싸서, 한 파트의 예외가 다른 파트로 번지지 않게 막는다. 이게 다음 5·6장에서 말하는 **"슬롯이 다 안 와도 작동한다"의 기술적 근거**다.

---

## 5. 슬롯 명세 — 인테이크 챗봇이 채워 넘겨야 할 것 (F 필독)

**이 장이 F 담당에게 가장 중요하다.** 인테이크 챗봇이 사용자와 대화해서 최종적으로 만들어 넘겨야 하는 객체는 단 하나, `AutoPrepRequest`다.

### 5.1 넘겨야 할 그릇 — `AutoPrepRequest`

```java
// backend/.../ai/autoprep/dto/AutoPrepRequest.java
public record AutoPrepRequest(
    String query,                 // 한 줄 자연어 요청
    Long applicationCaseId,       // 지원 건 ID
    String mode,                  // 면접 모드 코드
    String coverLetterText,       // 자소서 원문
    List<Long> attachmentFileIds  // 첨부 파일 id 목록
) {}
```

| 필드 | 필수/선택 | 누가 채우나 | 설명 |
| --- | --- | --- | --- |
| `query` | **권장** | 사용자 원문 그대로 | "네이버 백엔드 신입 통째로 준비해줘". 두뇌가 여기서 회사·직무·모드·파트를 추출한다. 비어 있으면 전체 파트 기본 실행 |
| `applicationCaseId` | **조건부 필수** | 챗봇이 되묻기로 확정 | `JOB`/`FIT`/`INTERVIEW`를 돌리려면 반드시 필요. 명시되면 `query` 파싱보다 우선 |
| `mode` | **조건부 필수** | 챗봇이 되묻기로 확정 | `INTERVIEW`를 돌리면 필요. 6종 코드 중 하나 |
| `coverLetterText` | 선택 | 사용자 입력 또는 첨부에서 추출 | `WRITE`(자소서 교정)가 쓴다. 없으면 첨부 텍스트 파일에서 자동 추출, 그것도 없으면 WRITE는 SKIPPED |
| `attachmentFileIds` | 선택 | `POST /api/file/upload` 결과 id | 이력서·자소서 등. 무료 1개/유료 다수 게이팅(8장) |

### 5.2 두뇌가 내부에서 채우는 슬롯 — `PrepSlots`

`query`를 두뇌가 해석하면 이 4칸이 채워진다. 인테이크 챗봇이 직접 만들 필요는 없지만, **"무엇을 확정해야 ready인지"** 이해하는 데 중요하다.

```java
// backend/.../ai/autoprep/PrepSlots.java
public record PrepSlots(String company, String jobTitle, String mode, Long applicationCaseId) {
    public boolean hasCase() { return applicationCaseId != null; }
}
```

### 5.3 면접 모드 6종 (`mode` 코드)

`AutoPrepIntakeService.MODE_OPTIONS`에 정의돼 있다. 인테이크 챗봇은 이 라벨로 사용자에게 묻고, 코드로 넘긴다.

| 코드 | 라벨 |
| --- | --- |
| `BASIC` | 기본 면접 |
| `JOB` | 직무 면접 |
| `PERSONALITY` | 인성 면접 |
| `PRESSURE` | 압박 면접 |
| `RESUME` | 자소서 기반 |
| `COMPANY` | 기업 맞춤 |

### 5.4 인테이크 챗봇이 넘기는 두 가지 방법

1. **직접 실행(권장·간단)**: 대화로 슬롯을 다 모았으면 `AutoPrepRequest`를 만들어 바로 `POST /api/auto-prep/run/stream` 호출. 끝.
2. **되묻기 API 경유(현재 프론트 방식)**: `POST /api/auto-prep/intake`를 `ready=true`가 될 때까지 반복 호출(매번 채워진 슬롯을 누적 전송) → ready 되면 `run/stream`. 무엇을 더 물어야 하는지(`nextAsk`)를 서버가 알려주니, 챗봇이 자체 판단을 줄이고 싶을 때 유용하다.

> **F가 확인용으로 기억할 한 줄**: "대화로 `applicationCaseId`(지원 건)와 (면접이 포함되면) `mode`만 확정하면, 나머지는 비어 있어도 오케스트레이터가 돈다."

---

## 6. "슬롯이 다 안 와도 작동한다"가 사실인 이유

이건 설계 의도이고 실제 코드로 보장된다. 두 겹의 안전장치가 있다.

**① 인테이크 단계 — 필요한 것만 되묻는다**

```java
// AutoPrepIntakeService.java
private static final Set<String> CASE_REQUIRED = Set.of("JOB", "FIT", "INTERVIEW");
boolean needsCase = plan.steps().stream().anyMatch(CASE_REQUIRED::contains);
```

플랜에 지원 건 필요 파트가 **하나도 없으면**(예: 프로필·자소서·커뮤니티만), `applicationCaseId`가 없어도 바로 `ready=true`다. 면접이 없으면 `mode`도 안 묻는다. 필요 없는 슬롯은 처음부터 요구하지 않는다.

**② 실행 단계 — 못 채운 파트만 건너뛴다**

각 파트 핸들러는 자기에게 필요한 입력이 없으면 스스로 `SKIPPED`를 반환한다(예: 자소서가 없으면 `WRITE`가 SKIPPED). 한 파트의 SKIPPED/FAILED는 다른 파트에 영향을 주지 않는다(4장). 그래서 **부분 정보만으로도 "할 수 있는 것까지" 준비가 된다.**

예시:
- `query`만 주고 지원 건 없음 + 면접 미포함 플랜 → 프로필 요약 등 **지원 건 불필요 파트만** 실행, 나머지 SKIPPED.
- 지원 건은 줬지만 자소서가 없음 → JOB·FIT·INTERVIEW는 정상, WRITE만 SKIPPED.

---

## 7. 6개 파트 핸들러 상세

모든 파트는 같은 인터페이스를 구현한다. **새 파트를 추가하려면 이 인터페이스만 구현**하면 오케스트레이터 본체는 안 고쳐도 된다(선언적 설계).

```java
// backend/.../ai/autoprep/PrepStepHandler.java
public interface PrepStepHandler {
    String key();                                  // "PROFILE" | "JOB" | ...
    default boolean enabled() { return true; }     // false 면 SKIPPED
    PrepStepResult handle(PrepStepContext ctx, PrepProgress progress);
}
```

핸들러는 `PrepStepContext`로 입력을 받는다: `userId`, `applicationCaseId`, `slots`, `coverLetterText`, `attachments`, 그리고 **`prior`(앞 파트들의 결과 맵)**. `prior` 덕분에 FIT·INTERVIEW가 JOB의 공고 분석 결과를 받아 쓸 수 있다.

| 핸들러 | key | 호출하는 도메인 서비스 | 선행 조건 | 산출물 |
| --- | --- | --- | --- | --- |
| `ProfilePrepHandler` | PROFILE | 프로필 요약 AI | 프로필 있어야 함(없으면 SKIPPED) | 역량 요약 |
| `JobPrepHandler` | JOB | 공고 분석 서비스 | `applicationCaseId` 필수 | 공고 분석 결과 |
| `FitPrepHandler` | FIT | 적합도 분석 서비스 | caseId + **JOB 완료** | 적합도·전략 |
| `WritePrepHandler` | WRITE | 자소서 교정 서비스 | 자소서 텍스트(직접/첨부) | 교정 결과 |
| `InterviewPrepHandler` | INTERVIEW | 면접 서비스(세션+질문) | caseId + **JOB 완료** + mode | 면접 세션 + 예상 질문 |
| `CommunityPrepHandler` | COMMUNITY | 커뮤니티 인기글 조회 | 없음(독립) | 인기글 목록 |

---

## 8. API 명세

| 엔드포인트 | 메서드 | 요청 | 응답 | 용도 |
| --- | --- | --- | --- | --- |
| `/api/auto-prep/intake` | POST | `AutoPrepRequest` | `AutoPrepIntakeResponse` | 되묻기(멀티턴). `ready`/`nextAsk`/후보 목록 |
| `/api/auto-prep/run` | POST | `AutoPrepRequest` | `AutoPrepResponse` | 동기 실행. 다 끝난 뒤 한 번에 결과 |
| `/api/auto-prep/run/stream` | POST(SSE) | `AutoPrepRequest` | SSE 이벤트 스트림 | 실시간 진행 표시(실제 화면이 쓰는 것) |
| `/api/file/upload` | POST | `multipart`(kind=ATTACHMENT) | 업로드 파일 정보 | 첨부 파일 업로드 → id 확보 |

`AutoPrepIntakeResponse` 주요 필드: `plan`, `ready`(boolean), `message`(사람용 문구), `nextAsk`(`"CASE"`|`"MODE"`|`null`), `candidates`(지원 건 후보), `modes`(모드 선택지).

**첨부 게이팅**: `AutoPrepAttachmentLoader`가 처리한다. 무료(FREE/BASIC) 첨부 1개, 유료 다수. 텍스트형(`text/*`)만 본문을 추출하고 최대 12,000자로 자른다. 한도 초과분·로드 실패분은 로그만 남기고 건너뛴다(역시 완주 우선).

---

## 9. AI 모델과 폴백 체인

### 9.1 폴백이란

폴백(fallback)은 **1순위가 실패하면 자동으로 2순위로 넘어가는** 안전망이다. 우리의 이상적 순서는:

```
자체 LLM(우리가 파인튜닝/서빙하는 모델)  →  1차 폴백: Claude Haiku  →  2차 폴백: OpenAI
```

자체 모델이 없거나 실패하면 Haiku로, Haiku도 안 되면 OpenAI로 떨어진다.

### 9.2 왜 1차 폴백을 Claude Haiku로 두나

- **Haiku는 소형·고속·저비용 모델**이다. 작은 작업(슬롯 추출, 짧은 생성 등)에서 큰 모델과 체감 품질 차이가 작으면서, 응답이 빠르고 호출 단가가 매우 싸다.
- 그래서 **자체 모델이 아직 없는 파트의 1차 폴백을 Haiku로 깔아두는 방향**을 잡는다(예정). 비용은 D(정원일)가 부담하기로 한다.
- 설정값(현재): `ANTHROPIC_MODEL = claude-haiku-4-5-20251001`, 재시도 최대 3회(408/429/5xx에서).

### 9.3 지금 실제로 어디까지 적용됐나 (중요 — 100%가 아님)

| 적용 수준 | 해당 기능 | 구현체 |
| --- | --- | --- |
| **완전(자체→Haiku→OpenAI)** | 면접 질문/꼬리질문/모범답안/리포트 (D) | `FallbackInterviewLlmGateway` |
| **부분(자체→OpenAI, Haiku 건너뜀)** | 답변 채점, 적합도 분석 (C·D) | `FallbackFitAnalysisAiService` 등 |
| **Mock 폴백만(OpenAI 전용)** | 회사/직무 분석, 프로필, 대시보드, 자소서 교정 | 각 도메인 OpenAI 서비스 |
| **폴백 없음** | 커뮤니티 챗봇 (F) | LangChain4j Ollama 직접 호출 |

**즉, 현재 Claude Haiku 폴백이 실제로 들어간 건 면접(D) 도메인뿐이다.** 두뇌(플래너)도 이 게이트웨이를 빌려 쓰므로 Haiku 폴백을 탄다.

> **할 일(예정)**: A(프로필)·C(회사분석)·E(자소서)·F(챗봇) 등 OpenAI 전용 경로에 **1차 폴백 Haiku를 추가**한다. 목표는 "자체 모델이 비거나 죽어도 Haiku가 받쳐주는" 일관된 안전망이다.

### 9.4 자체 모델은 왜 아직 거의 안 켜졌나

면접 생성용 자체 모델 활성화 화이트리스트(`FallbackInterviewLlmGateway`의 `OSS_GENERATION_TASKS`)가 **현재 비어 있다.** 질문생성(QGEN) 학습 데이터가 부족해서(시드당 1개, 형식 불안정) 품질이 안정될 때까지 꺼둔 것이다. 데이터를 보강·재학습한 뒤 task를 하나씩 화이트리스트에 추가해 단계적으로 켜는 계획이다. 채점(EVAL)용 자체 모델은 구현돼 있으나 기본값이 OpenAI다.

### 9.5 사용량 로깅

모든 AI 호출은 `ai_usage_log` 테이블에 남는다: `feature_type`, `status`(SUCCESS/FAILED/FALLBACK), `model`(실제 응답 모델), `input/output_tokens`, `credit_used`(1,000토큰=1크레딧). 단, 현재는 **최종 성공한 모델만 기록**하고 "폴백이 몇 번 일어났는지"까지는 안 남긴다(애플리케이션 로그에만 warn으로 남음).

### 9.6 모델/엔드포인트 한눈에

| 용도 | 모델(기본값) | 환경변수 |
| --- | --- | --- |
| 1차 폴백 | `claude-haiku-4-5-20251001` | `ANTHROPIC_MODEL`, `ANTHROPIC_API_KEY` |
| 2차 폴백 | `gpt-5` (면접 생성은 `gpt-5.4-mini`, 채점은 `gpt-5.4`) | `OPENAI_MODEL`, `OPENAI_API_KEY` |
| 챗봇 본답변(F) | `qwen3:8b` (Ollama) | `AI_AGENT_MODEL` |
| 챗봇 보조·검열·추천칩(F) | `gemma4` (Ollama) | `AI_OLLAMA_MODEL`, `AI_CHATBOT_CHAT_MODEL` |
| 챗봇 임베딩(F) | `bge-m3` | `AI_CHATBOT_EMBEDDING_MODEL` |
| Ollama 서버 | 원격 4090 `http://localhost:11434` | `AI_OLLAMA_BASE_URL` |

---

## 10. F의 인테이크 챗봇 — `ai/intake` (구현 완료)

### 10.1 현재 챗봇(ai/chat)이 이미 가진 것

F의 커뮤니티 챗봇은 최근 **LangChain4j 툴호출 에이전트**로 전환됐다. 인테이크 챗봇 확장에 그대로 재사용할 핵심 자산이 이미 있다.

| 자산 | 위치 | 재사용 가치 |
| --- | --- | --- |
| `@AiService` 에이전트 패턴 | `CommunityAgentConfig` / `CommunityChatAgent` | ★★★ 새 에이전트도 같은 빌더로 추가 |
| **세션 메모리(DB 영속)** | `MyBatisChatMemoryStore` + `chatbot_conversation_memory` 테이블 | ★★★ 11장 핵심 |
| 메시지 윈도우(최근 20개) | `ChatMemoryConfig` | ★★★ 대화 맥락 자동 관리 |
| 툴 호출(`@Tool`) | `CommunityTools` | ★★ 슬롯 조회용 툴 추가 패턴 |
| 빠른 우회 경로 | `FastPathService` | ★ 인테이크엔 조건이 달라 새로 짜야 함 |

> 참고: 현재 챗봇 **본답변 모델은 이미 `qwen3:8b`로 전환**됐다(`gemma4`는 검열·추천칩·임베딩 보조로 남음). "gemma 쓰는 중"이라고 알고 있었다면 메인은 바뀐 상태다. **모델 최종 선택은 F가 결정**한다.

### 10.2 구현된 구조 — `ai/intake` 패키지 (F)

위에서 "예정"으로 적었던 구조를 **F가 이미 구현해 dev에 머지했다.** (이 문서 작성 시점엔 미구현이었음)

**엔드포인트**: `POST /api/chatbot/intake/ask` — 한 턴씩 일반 POST(SSE 아님)
- 요청 `IntakeAskRequest(message, conversationId)`
- 응답 `IntakeAskResponse(conversationId, message, ready, nextAsk, autoPrepRequest)`

**구성요소** (`backend/.../ai/intake/`):

| 클래스 | 역할 |
| --- | --- |
| `IntakeChatAgent` | LangChain4j `@AiService`. `@SystemMessage(intake-chat-system.txt)` + `@MemoryId conversationId`. **String 반환**(qwen3 JSON 강제 시 툴 호출 건너뛰는 이슈 회피) |
| `IntakeAgentConfig` | 메모리 윈도우 **40**(커뮤니티 20과 분리, 되묻기 대비), `MyBatisChatMemoryStore` 공유, qwen3:8b, 툴 3연속 제한 |
| `IntakeTools` | `@Tool` 3종 — `listCases`(지원 건 목록), `chooseCase(caseId)`(확정), `chooseMode(code)`(모드 확정) |
| `IntakeSlotTrace` | **슬롯 추적** — 요청 컨텍스트는 ThreadLocal(userId·conversationId), 확정 슬롯은 `ConcurrentHashMap<conversationId, SlotState>`로 대화 단위 누적. `SlotState(caseId, mode, originalQuery, fetchedCases)` |

> 참고: 9·10장에서 "별도 상태 추적 객체가 필요하다"·"메모리 윈도우를 20보다 크게"라고 예측한 게 F의 `IntakeSlotTrace`·윈도우 40으로 실재한다.

**intake → autoprep 연결 (이미 됨)**:

```
ask(message, conversationId)
  → IntakeChatAgent.chat()              # LLM + 3종 툴로 슬롯 수집
  → trace.snapshot()                    # 확정 슬롯(caseId, mode, originalQuery)
  → new AutoPrepRequest(originalQuery, caseId, mode, null, null)
  → autoPrepIntakeService.intake()      # D의 되묻기 판정(5·8장)
  → IntakeAskResponse(ready, nextAsk, autoPrepRequest)
```

`ready=true`면 **클라이언트가 응답의 `autoPrepRequest`를 받아 `/api/auto-prep/run/stream`을 직접 연다.** F 컨트롤러는 SSE를 프록시하지 않는다 — 슬롯 수집까지가 F, 실행 스트림은 D.

### 10.3 남은 것 — 프론트 연결 + 슬롯 영속화

- **프론트 미연결**: `/api/chatbot/intake/ask`를 호출하는 화면이 아직 없다. 현재 `AutoPrepChatModal`은 D의 `/api/auto-prep/intake`(칩 방식)만 쓴다. **F의 대화형 인테이크를 화면에 붙이는 게 다음 작업**(→ 11.4).
- **슬롯 영속화 미구현**: `IntakeSlotTrace`의 슬롯은 현재 **JVM 인메모리**라 서버 재시작·다중 인스턴스에서 날아간다. 대화 메모리(`chatbot_conversation_memory`)는 DB에 있지만 슬롯은 아직 — 11장 세션 저장과 함께 D·C 합의 후 영속화.
- **슬롯 계약은 그대로**: 최종 산출물은 5장의 `AutoPrepRequest`. F·D 인터페이스는 이 하나로 고정.

---

## 11. 세션 자동저장 — ChatGPT식 디벨롭 (C팀장 구상)

### 11.1 무엇을 원하나

"~구글 면접 준비해줘"라고 만든 준비안을 사용자가 **나중에 다시 들어와 그대로 볼 수 있게** 하는 것. ChatGPT가 지난 대화를 왼쪽 목록에 쌓아두는 것과 같다. 세션 자동저장이 핵심이라고 본 이유다.

### 11.2 현재 상태

- **오케스트레이터(autoprep) 자체에는 세션 저장이 없다.** 프론트는 모달을 닫으면 대화·결과가 사라진다. autoprep 전용 세션 테이블도 없다.
- **하지만 F의 챗봇에는 이미 세션 영속화가 있다.** 이게 핵심 발견이다:

```
chatbot_conversation_memory  (마이그레이션: 20260623_f_chatbot_memory.sql)
  - conversation_id  BIGINT  PK(AUTO_INCREMENT)   ← 세션 ID
  - messages_json    JSON                          ← 대화 내역 전체
  - updated_at       DATETIME
```

`conversation_id`가 곧 세션 ID다. 응답에 실려 나가고, 클라이언트가 다음 턴에 다시 보내 **같은 세션을 이어간다.** ChatGPT의 "대화 하나"와 정확히 같은 개념이다.

### 11.3 방향 (예정)

세션 자동저장은 **새로 만들 필요가 거의 없다. 챗봇의 `conversation_id` 구조를 오케스트레이터로 끌어오면 된다.**

1. 인테이크 챗봇이 만든 `conversation_id`를 그 준비안의 세션 키로 쓴다.
2. 그 세션에 **확정된 슬롯 + 실행 결과 요약**을 같이 저장한다(기존 메모리 테이블 확장 또는 인접 테이블 추가).
3. 사용자가 다시 들어오면 `conversation_id`로 불러와 인테이크 대화와 준비 결과를 복원한다.
4. 홈/대시보드에 "최근 준비 세션" 목록(제목·시각)을 보여준다. (목록용 제목·타임스탬프는 별도 컬럼 필요)

> 정리하면 **C팀장의 ChatGPT식 디벨롭은 F 챗봇의 세션 메모리를 기반(backbone)으로 삼는 게 가장 빠른 길**이다. 인테이크 챗봇(F)·세션 저장(C)·오케스트레이터(D)가 `conversation_id`와 `AutoPrepRequest` 두 개의 계약으로 맞물린다.

### 11.4 앱 첫 화면 = 검색창 (목표 그림 vs 현재)

**목표 그림** — 모바일 앱을 켜면 첫 화면이 **"한 줄 검색창 + 최근 준비 세션 목록"**이다. "구글 면접 준비해줘" 치면 인테이크 챗봇(10장) → 오케스트레이터가 돌고, 과거 준비안은 11.3의 세션 목록에서 다시 연다. **단일 자연어 진입 = 모바일에 가장 맞는 형태.** (앱 = 검색창 하나로 다 되는 에이전트)

**현재(2026-06-24) 갭**:
- 랜딩페이지(`LandingPage.tsx`)의 검색창은 **데모 시뮬레이션**(`runDemo()`)이라 실제 호출이 아니다. "시작하기"는 `/home`으로 보낸다.
- 앱 첫 화면(`routes.ts`의 `/`)은 **랜딩이 아니라 `HomePage`**(C 담당 대시보드)다. `/landing` 경로조차 없다(랜딩은 레이아웃 밖 별도 렌더).
- 오케스트레이터 실제 진입은 `HomePage`에 임베드된 `AutoPrepPanel`(칩 방식)뿐. F의 대화형 인테이크는 프론트 미연결(10.3).

**그 그림이 되려면 (제안)**:
1. 모바일 첫 화면을 "검색창 + 최근 세션" 중심으로 (랜딩 데모를 실 호출로 전환하거나, 앱 전용 홈을 검색창 중심으로 재구성).
2. 그 검색창을 F의 인테이크 챗봇(`/api/chatbot/intake/ask`)에 연결 → `ready`면 `/run/stream`.
3. 11.3 세션 저장으로 "최근 준비안" 리스트.
→ 이 셋이 모이면 "검색창 하나로 다 되는 앱"이 완성된다. 웹 랜딩(마케팅 카피)과 앱 홈(기능)은 톤이 달라야 하고, 로그인은 "한 번 하면 다음부턴 바로 검색창" 흐름이 맞다.

---

## 12. 로컬 실행·API 키 공유·보안

### 12.1 키 주입 방식

백엔드는 `.env`를 안 쓰고 `application.yaml`이 모든 설정을 `${ENV_VAR:기본값}` 형태로 환경변수에서 읽는다. 즉 **실행 전에 환경변수로 API 키를 주입**해야 AI가 켜진다. 키가 없으면 해당 provider는 자동 비활성(폴백/Mock으로 떨어짐).

핵심 키: `ANTHROPIC_API_KEY`(Haiku), `OPENAI_API_KEY`(OpenAI). 그 외 모델명·엔드포인트는 9.6 표 참고.

### 12.2 run-local.bat 팀 공유 방안

- `run-local.bat`은 **API 키를 환경변수로 넣고 `bootRun`을 띄우는 로컬 실행 스크립트**다. 지금은 git 미추적(개인 OneDrive 공유)이다.
- C(팀장)와 F가 오케스트레이터·인테이크 챗봇을 **수시로 직접 띄워 확인·개발**해야 하므로, **같은 키가 든 실행 스크립트를 공유**하는 방안을 잡는다. AI 호출 비용은 D(정원일)가 부담한다.

### 12.3 보안 주의 (정확히 지킬 것)

키를 공유하면 편하지만 노출 위험이 커진다. 아래는 타협 불가다.

- **API 키를 git에 절대 커밋하지 않는다.** `run-local.bat`처럼 평문 키가 든 파일은 반드시 gitignore 상태를 유지한다. `application.yaml`은 이미 `${ENV_VAR}`라 안전하다.
- **공유는 비공개 채널로만.** OneDrive 공유폴더 등 접근이 통제된 곳으로만 전달하고, 공개 채팅·이슈·PR·스크린샷에 키를 노출하지 않는다.
- **키가 노출되면 즉시 폐기·재발급(rotate)한다.** Anthropic/OpenAI 콘솔에서 해당 키를 무효화하고 새 키로 교체한다.
- **사용량 한도를 건다.** 비용을 한 사람이 부담하므로, 콘솔에서 월 사용 한도(usage limit)와 알림을 설정해 폭주·오남용에 대비한다.
- 가능하면 **공유용 키와 개인 키를 분리**해, 문제가 생긴 키만 끊을 수 있게 한다.

---

## 13. 부록 — 용어집 & 파일 위치

### 용어집

| 용어 | 뜻 |
| --- | --- |
| 오케스트레이터 | 여러 AI 작업의 순서·동시성을 지휘하는 코드 (`AutoPrepOrchestrator`) |
| 두뇌/플래너 | 한 줄 요청을 해석해 실행계획을 짜는 코드 (`AutoPrepPlanner`) |
| 인테이크 | 실행 전에 부족한 정보를 사용자에게 묻는 단계 |
| 슬롯 | 작업에 필요한 빈칸(회사·직무·지원건·모드 등) |
| 파트/스텝 | 작업 단위 6개(PROFILE·JOB·FIT·WRITE·INTERVIEW·COMMUNITY) |
| 의존 그래프 | "A가 끝나야 B 시작" 같은 선후 관계 (FIT·INTERVIEW ← JOB) |
| SSE | 서버가 진행 상황을 실시간으로 밀어 보내는 단방향 스트림 |
| 폴백 | 1순위 실패 시 다음 순위로 자동 전환하는 안전망 |
| 게이팅 | 요금제 등 조건으로 기능·수량을 제한하는 것(첨부 개수 등) |

### 백엔드 파일 (`backend/src/main/java/com/careertuner/ai/autoprep/`)

| 파일 | 역할 |
| --- | --- |
| `AutoPrepController` | REST 엔드포인트(intake/run/run-stream) |
| `AutoPrepPlanner` | 두뇌 — 슬롯 추출 + 파트 선택 |
| `AutoPrepOrchestrator` | 의존 그래프 병렬 실행 + SSE |
| `AutoPrepIntakeService` | 되묻기(멀티턴) 판정 |
| `AutoPrepAttachmentLoader` | 첨부 로딩 + 게이팅 |
| `PrepSlots` / `PrepPlan` / `PrepStepResult` / `PrepStepContext` / `PrepProgress` / `PrepAttachment` | 데이터 구조 |
| `PrepStepHandler` + `handler/*PrepHandler` | 파트 인터페이스 + 6개 구현 |
| `dto/AutoPrepRequest` / `AutoPrepResponse` / `AutoPrepIntakeResponse` | 요청·응답 |

### 프론트 파일 (`frontend/src/features/autoprep/`)

| 파일 | 역할 |
| --- | --- |
| `components/AutoPrepPanel` | 홈 임베드 진입 패널 |
| `components/AutoPrepLauncher` | 한 줄 입력 + 파일 첨부 UI |
| `components/AutoPrepChatModal` | 인테이크 되묻기 채팅 모달 |
| `components/AutoPrepWorkView` | 6파트 실시간 진행 타임라인 |
| `hooks/useAutoPrepRun` | SSE 수신 → 화면 상태 변환(`reduce`) |
| `api/autoPrepApi` | intake/runStream/uploadAttachment 호출 |
| `types/autoPrep` | 타입 정의 + `PREP_PARTS` 메타 |

### 챗봇 파일 (`backend/src/main/java/com/careertuner/ai/chat/`)

| 파일 | 역할 |
| --- | --- |
| `CommunityChatAgent` / `CommunityAgentConfig` | LangChain4j 에이전트 |
| `CommunityTools` | `@Tool` 3종(글 검색·본문·FAQ) |
| `MyBatisChatMemoryStore` / `ChatMemoryMapper` / `ChatMemoryConfig` | 세션 메모리(11장) |
| `FastPathService` / `QuickReplyAgent` | 빠른 우회 / 추천칩 |

### 관련 문서

| 문서 | 연결점 |
| --- | --- |
| `docs/ARCHITECTURE.md` | 전체 아키텍처 안에서 오케스트레이터 위치 |
| `docs/planning/면접 자율 에이전트 로드맵.md` | 구현 히스토리·배포 토폴로지 |
| `docs/planning/prototypes/orchestrator-chat.html` | 인테이크 채팅 시안(→ `AutoPrepChatModal`) |
| `docs/planning/prototypes/orchestrator-screen.html` | 작업 과정 시안(→ `AutoPrepWorkView`) |
| `docs/planning/prototypes/orchestrator-flow.svg` | 전체 흐름도 |

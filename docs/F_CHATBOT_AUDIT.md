# 튜너봇(챗봇) F-영역 정적 감사

작성: 2026-07-03 · 코드 변경 0줄 · 대상 커밋: HEON-JEONG-SUK 최신(pull d34a7313 이후 + 넛지 패치 워킹트리)
태깅: **[사실]** = 코드에서 확정 · **[가설]** = 런타임 확인 필요(확인 방법 병기)

---

## 1. 파일 인벤토리 (감사 스코프)

### 백엔드 — F 소유
| 경로 (`backend/src/main/java/com/careertuner/`) | 역할 |
| --- | --- |
| `support/chatbot/ChatbotController.java` (1560L) | 통합 입구 `/api/chatbot/ask` — 라우팅·④온보딩 전 단계·이탈·가드·요약 |
| `support/chatbot/UnifiedChatRouter.java` | ①/③ 첫 턴 라우팅(3-임베딩 점수 + 화행분류) |
| `support/chatbot/ChatbotService.java` | FAQ 임베딩 검색/유사도(+미사용 레거시 `ask()`) |
| `support/chatbot/SpeechActClassifier.java` | 경계구역 QUESTION/COMMAND 이진 분류(qwen3) |
| `support/chatbot/IntakeModeStore.java` | ③ sticky 플래그 (인메모리) |
| `support/chatbot/RouteConfirmStore.java` | 라우팅 확인 1턴 플래그 (인메모리) |
| `support/chatbot/SideQuestionStore.java` | 이탈성 질문 보류 발화 (인메모리) |
| `support/chatbot/OnboardingRestartStore.java` | ④ 재시작 확인 플래그 (인메모리) |
| `support/chatbot/Ollama{Chat,Embedding}Client.java` | Ollama HTTP 클라이언트(챗은 레거시 경로) |
| `support/chatbot/SupportTextFallbackGenerator.java` | Ollama→Claude→목업 폴백(챗봇 본선 미사용) |
| `support/chatbot/{ResponseLog,UnansweredQuestion}Service·Mapper` | 응답/미스 로그 적재 |
| `support/chatbot/ChatbotProperties·CosineSimilarity·FaqHit·ChatbotFaqMapper` | 설정·유틸·FAQ 접근 |
| `ai/intake/IntakeAskService.java` | ③ 한 턴 코어(슬롯 접지·fork·영속·복원) |
| `ai/intake/IntakeChatAgent.java` + `prompts/intake-chat-system.txt` | ③ 대화 LLM |
| `ai/intake/IntakeTools.java` | listCases/chooseCase/chooseMode(무력화) 툴 |
| `ai/intake/IntakeSlotTrace.java` | ③슬롯+④온보딩 상태 (인메모리) |
| `ai/intake/IntakeController.java` | ③ 직행 보조 엔드포인트 `/api/chatbot/intake/ask` |
| `ai/intake/ChatbotIntakeSlotMapper.java` | `chatbot_intake_slot` 영속 |
| `ai/chat/CommunityChatAgent.java` + `prompts/community-chat-system.txt` | ① 에이전트 LLM |
| `ai/chat/CommunityTools.java` | 글검색/본문/FAQ 툴 |
| `ai/chat/QuickReplyAgent·QuickReplyParser` | 칩 생성·견고 파싱 |
| `ai/chat/SummaryAgent.java` | 추천 후기 묶음 요약 LLM |
| `ai/chat/MyBatisChatMemoryStore·ChatMemoryMapper·ChatMemoryConfig` | 대화 메모리 영속(윈도우 20) |
| `ai/chat/FastPathService.java` | 내비 즉답 |
| `ai/chat/MessageSanitizer.java` | LLM 출력 마크다운 제거 |
| `ai/chat/SearchTrace.java` | 툴 출처 접지(ThreadLocal) |

### 백엔드 — 계약 이해용 읽기 전용 (D/B 소유)
| 경로 | 역할 |
| --- | --- |
| `ai/autoprep/AutoPrepIntakeService.java` | ③ ready/nextAsk 권위 판정 + 봇 문장 생성 — **소유 경계 모호(§6 인계)** |
| `ai/autoprep/AutoPrepController.java` / `AutoPrepOrchestrator.java` | run/SSE 실행 (D) |
| `ai/autoprep/AutoPrepPlanner.java` + handlers | 계획/6파트 (D) |

### 프론트 — F 소유
| 경로 (`frontend/src/features/`) | 역할 |
| --- | --- |
| `support/components/ChatbotWidget.tsx` (1376L) | 위젯 전체(버블·패널·칩·④가이드 배선·넛지) |
| `support/components/OnboardingGuide.tsx` (872L) | 가이드 오버레이(role→skills→docs→jd→fit→interview) |
| `support/components/ChatbotFullScreen.tsx` + `pages/ChatbotPage.tsx` | `/support/chat` 구식 전체화면(제거 예정·라우트 잔존) |
| `support/hooks/useChatbot.ts` | 대화 상태·전송·복원·세션·run 인계 |
| `support/hooks/useOnboardingGuide.ts` | 가이드 상태·ensureCase·runReal·완료감지 |
| `support/api/onboardingApi.ts` | 케이스 생성/추출 상태/README API |
| `support/onboarding/guideData.ts` | 직군/역량/링크 필드 메타 |
| `support/types/chatbot.ts` | 메시지/인테이크 타입 |
| `autoprep/hooks/useAutoPrepRun.ts` + `api/autoPrepApi.ts` | SSE 구독/파싱(공용) |
| `autoprep/components/AutoPrepWorkView.tsx` + `lib/partCopy.ts` | 실행 진행/결과 UI(위젯·가이드가 소비) |

---

## 2. 캘리브레이션 — 실측 버그 2건 역추적

### 버그 1 — "기업명 확인 필요 직무명 확인 필요 / 기본 면접 기준으로…"

**(a) 문자열 출처 [사실]** — 프롬프트 유입이 아니라 **코드 템플릿 조립**이다.
- 문장 생성: `AutoPrepIntakeService.java:69` — `"좋아요 — " + describe(plan) + " 기준으로 지금 바로 준비를 시작할게요."`
- `describe()`(`:72-93`)가 `plan.slots().company()/jobTitle()`를 **무검증 보간**. 이 값의 원천은
  `application_case.company_name/job_title` — B 추출 워커가 파싱 실패 시 남기는 placeholder
  `"기업명 확인 필요"/"직무명 확인 필요"` (`ApplicationCaseExtractionWorker.java:40-41`).
- 같은 raw 값이 노출되는 다른 표면 [사실]:
  - 지원 건 후보 칩 — `ChatbotController.toCandidates():1162-1170` → 위젯 `ApplicationChip`
  - 세션 사이드바 제목 — `IntakeAskService.caseTitle():427-436` ("기업명 확인 필요 직무명 확인 필요"가 title로)
  - ③ 툴 출력 — `IntakeTools.listCases()` (`id=NN · 기업명 확인 필요 · …`)가 LLM 컨텍스트+메모리에 유입

**(b) 게이트 [사실]** — 코드에도 프롬프트에도 없다.
- ready 판정 `AutoPrepIntakeService.intake():50` 은 `applicationCaseId != null` 만 본다. placeholder 검사 0.
- 대조: **④ 온보딩은 같은 상황을 코드로 게이트한다** — `ChatbotController.onboardingResolveCase():564-588`
  (`ONB_DEFAULT_COMPANY` 정확일치 → AWAIT_COMPANY/JOBTITLE 되묻기). **③ 인테이크 직행 경로에만 이 게이트가 없다** — 비대칭이 원인.
- 유입 시나리오: 추출이 REVIEW_REQUIRED 또는 메타파싱 실패로 placeholder인 case(실측: case 65가 정확히 이 상태)를
  ③에서 칩/텍스트로 선택 → CASE 충족 → 모드 선택 → ready 문장에 placeholder 보간.
- 취약 결합 [사실]: placeholder **문자열이 3곳에 독립 정의**됨(`ApplicationCaseExtractionWorker:40`,
  `ApplicationCaseServiceImpl:54-55`(유니코드 이스케이프), `ChatbotController:80-81`). B가 문구를 바꾸면 ④ 게이트가 조용히 무력화된다.

### 버그 2 — 플로우 중 "네" → 일반 폴백, 약속한 질문 미출력

**H1/H2 판정: 둘 다 아님 — 제3원인** (단 H1의 라우터 메커니즘 절반은 성립).

- [사실] `"면접을 시작할게요! 아래에 질문이 나올 테니…"`는 **repo 전체에 코드 리터럴이 없다**(grep 0건). 즉 LLM 생성 문장.
- [사실] 봇 버블에 자유생성 LLM 텍스트가 실리는 표면은 **①`agentPath`(CommunityChatAgent) 단 하나**다:
  - ③은 봇 문장 = 코드 결정 메시지 우선(`IntakeAskService:152-153`)이고 `AutoPrepIntakeService.intake` 3분기 모두 non-blank → qwen3 원문이 화면에 갈 일 없음
  - ④는 전 분기 고정 리터럴, FAQ 즉답은 DB 답변 verbatim(`:1373`), 요약은 별도 엔드포인트
- ⇒ 그 발화는 **① 에이전트(qwen3)가 "인챗 모의면접"이라는 존재하지 않는 기능을 롤플레이(환각)로 약속**한 것.
  시스템 프롬프트(`community-chat-system.txt`)에 이런 롤플레이 금지 조항이 없고, 메모리에는
  온보딩 요약 AiMessage(`injectOnboardingSummaryIntoMemory:748-780`)·fork로 복사된 인테이크 구간이 남아
  "면접" 맥락을 계속 공급한다 — 롤플레이를 촉발하기 좋은 조건.
- [사실] 다음 턴 "네"의 낙하 경로: ① 대화엔 sticky/상태가 없어 매 턴 독립 라우팅(H1 메커니즘) →
  `UnifiedChatRouter.decide():111` 에서 "네"는 faq/intake/community 3점수 모두 weakGate 미만 →
  `FALLBACK` → `ChatbotController:1076-1087` 고정 되묻기 — 예시 "환불 어떻게 해요"가 하드코딩이라 맥락 밖 노출.
- [사실] H2(플로우가 조용히 죽음) 기각 — 죽을 플로우 자체가 코드에 없다. "질문 미출력"도 같은 원인(할 수 있는 척만 한 것).
- 참고 [사실]: `AFFIRMATIVE`(`:87-89`)에 "네"가 있지만 소비 지점은 확인 1턴 대기(`consumePending:1049`)·④확인들뿐 —
  ① 일반 대화 뒤에는 어떤 대기 플래그도 없어 화이트리스트가 개입할 수 없다.
- **해피패스 직후에도 같은 표면 [사실 체인]**: ③ ready 턴("좋아요—…시작할게요")에서 sticky 해제(`enterIntake:1143-1147`),
  ④ 면접인계에서 step=DONE(`:731`) — 직후 유저가 "네"/“고마워” 등 약신호를 치면 동일하게 FALLBACK 되묻기(환불 예시)가
  실행 화면 옆에 뜬다.

**런타임 확정 체크리스트 (10분)**
1. `chatbot_response_log` 해당 대화 조회 — "네" **직전** 턴 `response_path='AGENT'` 확인(=① 에이전트 턴이었음).
2. "네" 턴은 **행이 없음**을 확인 — FALLBACK 분기는 `record()` 미호출[사실 `:1076-1087`]. 행 부재가 곧 FALLBACK 경유 방증.
3. `chatbot_conversation_memory.messages_json` 마지막 AiMessage 에 "면접을 시작할게요…" 원문 존재 확인
   (메모리에 쓰는 건 에이전트뿐 → 존재하면 ① 생성 확정).
4. 재현: 같은 대화에 아무 잡담 → 에이전트 응답 후 "네" 전송 → route `되묻기` 반환 확인(curl 1회).

---

## 3. 감사 A — 상태 전이 전수표

phase 원천 [사실]: ④ = `IntakeSlotTrace.onboardingStep` ∈ {null, JOB, SKILLS, AWAIT_POSTING, EXTRACTING, AWAIT_COMPANY, AWAIT_JOBTITLE, AWAIT_MODE, DONE} (+대기플래그: 재시작확인·질문확인·declined) · ③ = sticky(인메모리)+slot(PENDING/READY/DONE), nextAsk CASE→MODE→ready · ① = 무상태(+확인반환 1턴) · 프론트 가이드 = role→skills→docs→jd→(waiting)→fit / 자체흐름 analyzing→fit→interview.

| 상태 \ 입력 | 칩 클릭 | 단답 긍정("네","ㅇㅇ") | 자유 텍스트 | 파일 첨부 | 새로고침·리마운트 | 취소어("그만") | 주제 이탈(질문) | 빈 입력 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ① 일반(에이전트 턴 뒤) | quickReply=텍스트 재전송(정의) | **라우터 독립판정→FALLBACK(버그2)** | 라우터 재판정(정의) | 경로 없음(위젯 파일버튼 없음[사실]) | 메모리 복원(정의) | 라우터행(대개 FALLBACK/AGENT — 의도 무해) | 정의(FAQ/AGENT) | 프론트 차단 |
| ① 확인반환 대기 | "시작"→③ / "그냥 질문이에요"→ **그 문구가 질문으로 ①행**(원 발화 유실 `:1049-1055`) | ③ 진입(정의) | ①행 — **원 발화 아닌 새 발화 필요** | — | 플래그 휘발(인메모리)→일반 라우팅 | 라우터행 | ①행 | 차단 |
| ③ sticky (CASE 대기) | selectedCaseId 결정적(정의) | qwen3 해석→매칭 0건→CASE 재질문(방어됨 `reconcileTextCaseAnswer:214-226`) | 회사명 매칭(정의) | intakeGuide 경유 업로드→`handleSlotFilled`(정의) | slot PENDING → 복원(정의) · route 미보존→칩/가이드 유실[사실] | 이탈 응답(정의 `:948-956`) | 질문확인 1턴(정의 `:973-989`) | 차단 |
| ③ MODE 대기 | selectedModeCode 결정적(정의) | 칩 재제시(정의 — mode는 칩만) | 칩 재제시(정의) | — | 복원(정의) | 이탈(정의) | 질문확인(정의) | 차단 |
| ③ ready 직후(sticky 해제) | 만료칩 안내(정의 `:1040-1046`) | **FALLBACK 되묻기(환불 예시) — run 화면 옆에서**[사실 체인] | 라우터행 | — | slot READY→복원 안 함(정의) | READY까지는 이탈 응답(정의 `hasOpenIntakeSlot`) | 라우터행 | 차단 |
| ④ JOB/SKILLS | (칩 없음) | **"네"가 직무/기술로 그대로 저장**[사실 — `:400-406`에 AFFIRMATIVE 필터 없음] → 프로필 오염 | 그대로 저장(설계) | 경로 없음 | **미정의 — §5 CL3**: ④턴은 메모리 미기록→복원 불가, 새 대화 발급→step 고아 | 온보딩이탈+declined(정의 `:938-947`) | 질문확인(정의, GUARDED) | 차단 |
| ④ AWAIT_POSTING | 가이드 제출(caseId 입양, 정의) | 20자 미만→재요청(정의 `:438-441`) | ≥20자→케이스 생성(정의) | 가이드 d′ 업로드→입양(정의) | 위와 동일(미정의) | 정의 | 질문확인(정의) | 차단 |
| ④ EXTRACTING | — | 폴링 소비(정의) | **모든 발화가 폴링에 삼켜짐**[사실 `:417-418` — GUARDED_STEPS 제외 `:108-109`] | — | 위와 동일 + 프론트 넛지예산은 sessionStorage 로 보존(수정됨) | 정의 | **가드 없음 — 질문도 "잠시 후" 응답에 삼켜짐** | 차단 |
| ④ AWAIT_COMPANY/JOBTITLE | 후보 칩=텍스트(정의) | 긍정=후보 수락(정의 `isAffirmative:538-545`) | 그대로 회사/직무 저장(정의 — 오기록은 질문가드가 부분 방어) | — | 위와 동일(미정의) | 정의 | 질문확인(정의) | 재질문(정의 `:526-528`) |
| ④ AWAIT_MODE | 모드칩 결정적(정의) | 칩 재제시(정의) | 칩 재제시(정의) — **질문도 삼켜짐**(GUARDED 제외) | — | 위와 동일(미정의) | 정의 | 가드 없음 | 차단 |
| ④ DONE(면접인계 후) | — | **FALLBACK 되묻기**[사실 — sticky 해제 상태] | 라우터행 | — | run 재개 경로 없음(§5 CL4) | 라우터행 | 라우터행 | 차단 |
| 가이드 waiting(프론트) | — | (자동 넛지가 대행) | 입력창은 가이드에 가려짐 — "질문하기"로 우회(정의) | — | 넛지예산 보존(수정됨) · route 미보존→waiting 재진입은 유저 발화 필요 | 가이드 밖 챗으로(정의) | "질문하기" 링크(정의) | — |

**버그 표면 요약**: ①-단답긍정(버그2), ③ready/④DONE-단답긍정(버그2 사촌), ④JOB/SKILLS-단답긍정(오기록),
④EXTRACTING·AWAIT_MODE-질문(삼킴), ④전단계-새로고침(복원 부재), 확인반환-비긍정(원 발화 유실).

## 3-B. 게이트 감사 — 입력이 라우터/LLM에 도달하는 진입점

| 진입점 | activeFlow/phase 게이트 | 판정 | 게이트 부재 시 증상 |
| --- | --- | --- | --- |
| `POST /api/chatbot/ask` (`:902`) | 이탈→③복원→③sticky→④진행중→declined복귀→NAV→④첫진입→만료칩→확인1턴→라우터 (10단 직렬) | **있음(코드)** [사실] | — (순서 민감: NAV가 ④sticky보다 뒤로 옮겨진 이력 있음 `:994-997`) |
| `POST /api/chatbot/intake/ask` (`IntakeController:36-57`) | 소유권 가드만. **라우터·이탈·sticky·질문가드·온보딩 전부 우회**, selected* 미지원(3-arg) | **없음** [사실] · FE 호출자 0[사실 grep] | 이 API로 진입한 대화는 "그만" 이탈 불가·질문 삼킴. 지금은 미사용이라 휴면 위험 |
| `POST /api/chatbot/summarize-posts` (`:1514-1548`) | conversationId 소유권 검사 없음(에코만), 상태 무접촉. 글 접근은 `getPostContent` PUBLISHED 게이트[사실] | 해당 없음(무상태) | 낮음 — postIds enum 공격해도 발행 글만 요약됨 |
| 위젯 `handleSend`/`sendMessage` | `botStatus==="thinking"` disabled(`:486`)만. phase 게이트는 서버 위임 | 프론트 무게이트(설계) | 서버 게이트가 전부 방어 — ① 무상태 구간만 버그2 표면 |
| 위젯 quickReply 클릭 | `lastBotId` 턴에만 활성(`:354`) | 있음 | — |
| ④ 자동 넛지(effect) | `phase==="waiting"`+예산+exhausted 래치 | 있음(수정됨) | — |
| ready→`run.start`(`useChatbot:307-337`) | `intake.ready && autoPrepRequest` + 로그인 검사 | 있음 | — |
| `/support/chat` ChatbotFullScreen | 인테이크/칩/가이드/오케 **0건**[사실 grep] — 구식 FAQ UI가 같은 `/ask` 를 침 | 라우트 잔존 | 깡통계정이 여기로 오면 ④ 텍스트 프로토콜이 칩·가이드 없이 생짜 노출 |

## 3-C. 프롬프트 위생

| 조립 지점 | 유입 문자열 출처 | 내부 상태 유입 경로 | 판정 |
| --- | --- | --- | --- |
| ① `agent.chat` (system 고정 + 메모리20 + 발화) | 메모리: 유저/봇 원문 + **fork 복사된 ③ tool-call/result**(listCases 원문 `id=NN · 기업명 확인 필요 · …`) + 온보딩 요약 AiMessage | **내부 id·placeholder가 LLM 컨텍스트에 유입**[사실 — 메모리에 저장됨]. 에이전트가 이를 발화에 에코 가능 [가설 — memory JSON에서 tool result 존재 확인] | 주의 |
| ① 시스템 프롬프트 | 주입 방어 조항 있음 ✓ / **롤플레이(없는 기능 약속) 금지 조항 없음** | — | 버그2 원인 축 |
| `QuickReplyAgent.suggest` (`buildChipContext:1451-1477`) | 프로필 raw(skills = JSON 배열 문자열 그대로) + 대화 8줄 | JSON 원문이 모델에 노출 — 칩 품질 저하 | C |
| ③ `IntakeChatAgent` | 메모리 + 툴 출력(placeholder 포함) | 화면 봇 문장은 코드 결정이 우선(`:152-153`)이라 노출 차단 ✓ | 양호 |
| `SummaryAgent.summarize` (`:1537-1539`) | **커뮤니티 글 본문 통짜** — system 에 주입 방어 조항 **없음**[사실] | 글 안의 지시문("이전 지시 무시하고 …")이 요약 출력을 탈취 가능 [가설 — 악성 글 1건 게시 후 요약 재현] | B |
| ④/되묻기/에러 | 전부 고정 리터럴 | — | 안전 |
| 버그1 템플릿(`AutoPrepIntakeService:69`) | case 컬럼 raw | **프롬프트가 아니라 코드 조립** — placeholder 무검증 보간 | §2 |
| 출력 새니타이즈 | `MessageSanitizer.stripMarkdown` — agentPath`:1219`·③`:96`·이력`:1348`·요약`:1539` 적용 ✓ | 마크다운만 제거. `<think>` 는 yaml `think:false + return-thinking:false`(`application.yaml:221-222`)로 차단 [사실 설정 — 실누출 여부는 런타임] | 양호 |

## 3-D. 실패 경로 (Tailscale 너머 홈 Ollama: `localhost:11434`)

| 호출 | 타임아웃 | 실패 시 사용자 화면 | 플로우 상태 |
| --- | --- | --- | --- |
| 임베딩(라우터·FAQ게이트·질문가드) | connect 10s / read 60s | 라우터: FAQ 폴백(`UnifiedChatRouter:101-105`) → 게이트 실패 → 에이전트행 → 그것도 죽으면 "지금은 답변을 생성하기 어렵습니다"(`:1240-1249`) | 무오염 ✓ · 단 직렬 재시도로 **최악 수십 초 thinking**(입력 disabled) |
| 화행분류 | 10s/30s | QUESTION 폴백(보수) | 무오염 ✓ |
| ①③ LangChain qwen3 | **PT120S**(`application.yaml:220`) | ①: 고정 에러문구 ✓ · ③: 고정 폴백+sticky 유지 → 다음 턴 재시도 가능 ✓(명시 선택은 LLM 실패 흡수 `:93-103`) | 무오염 ✓ · **최악 120초 스피너** — 클라 측 타임아웃/취소 없음 |
| ④ EXTRACTING 폴 | (자체 read) | 추출 row 부재(케이스 삭제·미큐잉)·조회 예외 → catch → **"추출대기" 영원**(`:493-497,:509-515`) — stale 판정 없음 | **림보** [사실] — 워커의 30분 stale 복구는 RUNNING row 에만 적용, row 부재엔 무효 |
| ④ DONE 마킹(`:731`) → 프론트 run.start | — | run 시작 실패(네트워크) 시 재실행 UI 없음. step=DONE·sticky 해제라 후속 발화는 일반 라우팅 | **재개 불가 림보** [사실] |
| SSE `/run/stream` | 서버 `SSE_TIMEOUT_MS=300_000`(`AutoPrepOrchestrator:42`) · **클라 무이벤트 타임아웃 없음**[사실] | 아래 3-E | 아래 |
| 요약(SummaryAgent) | PT120S | 고정 대체 문구 ✓(`:1540-1543`) | 무오염 ✓ |

## 3-E. SSE 계약 diff

| 이벤트 | FE 기대(`autoPrepApi.parseEvent`) | BE 발행(`AutoPrepOrchestrator:93-107`) | 판정 |
| --- | --- | --- | --- |
| plan / part-start / substep / part-done / done | ✓ | ✓ | 일치 |
| **error** | ✓ 파싱 준비됨(`useAutoPrepRun:92-93` reduce도 존재) | **미발행** — 예외 시 `completeWithError(ex)`(`:110`) = HTTP 비정상 종료일 뿐 | **계약 불일치 [사실]** — D 인계(§6) |

파생 증상(F 소유 표면):
- plan 이벤트 전 예외(planner) → 스트림 즉사 → `parts=[]` → `AutoPrepWorkView`는 **null 렌더**(`:24-26`) →
  위젯: "면접 준비를 시작할게요!" 뒤에 아무것도 안 나타남. `useChatbot`이 노출하는 `runError:481`를
  **위젯이 구조분해하지 않아 렌더 0**[사실 — ChatbotWidget에 runError 참조 없음].
- 파트 진행 중 스트림 단절/서버 5분 타임아웃 → 일부 파트가 `running`인 채 스트림 종료 →
  가이드 자체실행 완료감지(`useOnboardingGuide:277-285`)의 `allSettled=false` → **analyzing 화면 영구 림보** [사실 체인].
- 실패 카드의 "다시 시도"/"모두 다시 시도" 버튼 = **no-op** `onClick={() => {}}`(`AutoPrepWorkView:78,:174`) [사실].
- fetch 예외 시 `(err as Error).message` 원문이 가이드 오류로 노출(`OnboardingGuide:568-573`) — 영어 브라우저 문구 가능 [가설 — completeWithError 시 브라우저별 reader 동작; DevTools로 확인].

---

## 4. 발견 통합 리스트 (영향도: A=데모 경로 파괴 / B=창피한 노출 / C=품질)

| ID | 위치 | 원인 요약 | 예상 런타임 증상 | 영향 | 권장 방향(1-2줄) |
| --- | --- | --- | --- | --- | --- |
| F-01 | `AutoPrepIntakeService.java:50,69` | ③ ready가 placeholder 미검증(caseId만 봄) + 템플릿 무검증 보간 | 버그1 그대로 — 미확인 슬롯인데 "시작할게요" | **A** | ready 앞 placeholder 코드 게이트(④ `onboardingResolveCase` 게이트를 ③에 이식) |
| F-02 | `ChatbotController:1162-1170`, `IntakeAskService:427-436` | 후보 칩·세션 title에 placeholder raw 노출 | 칩/사이드바에 "기업명 확인 필요" | B | 표시 계층에서 placeholder→"(회사 확인 필요)" 라벨 치환 or 후보 제외 |
| F-03 | `community-chat-system.txt` + `agentPath:1219` | ① 에이전트가 존재하지 않는 인챗 면접을 롤플레이(환각 약속) | 버그2 선행 발화 | **A** | 프롬프트에 기능 경계 명시(면접은 페이지 인계만) + [가설 확정 후] 시스템 차원은 CL2 참조 |
| F-04 | `ChatbotController:1076-1087` | FALLBACK 고정 되묻기 — 맥락 무시 + 하드코딩 예시(환불) + `record()` 미호출 | 버그2 후행 턴 · 관측 불가 | **A** | 직전 턴이 AGENT면 FALLBACK 대신 에이전트로 이어주기(맥락 라우팅) + 로깅 추가 |
| F-05 | `:1049-1055` | 확인반환 1턴에서 비긍정 시 원 발화 대신 확인 응답 문구가 질문으로 감(RouteConfirmStore가 플래그만 보관) | "그냥 질문이에요"에 봇이 동문서답 | B | SideQuestionStore처럼 원 발화 보관 → 비긍정 시 원 발화로 faqPath |
| F-06 | `onboardingTurn` 전반(메모리 미기록) + `useChatbot.restoreRecent:101-124` | ④턴은 LangChain 메모리에 안 씀 → `messages_json=[]`(실측 9000194) → 복원 스킵 → **새 대화 발급** → 인메모리 step 고아 | 새로고침 후 온보딩 대화 통째 증발 + 진행 재개 불가 | **A** | ④턴도 memoryStore에 user/AI 텍스트 기록(요약주입과 같은 경로) — 복원·연속성 일원화 |
| F-07 | `ChatHistoryResponse`(route 미보존) + `restoreRecent` 매핑 | 복원 메시지에 route/intake 없음 → 가이드·칩 상태 유실 | 새로고침 후 칩 사라짐·가이드 안 뜸 | B | 히스토리에 route(최소 마지막 턴) 포함 or 복원 후 상태 재조회 API |
| F-08 | `AutoPrepOrchestrator:108-110` (D) | 예외 시 `error` SSE 미발행(completeWithError만) | 터미널 이벤트 유실 | **A** | §6 D 인계 — catch에서 `send(emitter,"error",…)` 후 complete |
| F-09 | `ChatbotWidget:112-119` | `runError` 미구조분해 → 위젯 run 실패 무표시(+parts 0이면 WorkView null) | 인계 후 화면 공백/침묵 | **A** | 위젯에 runError 배너 + parts empty&!running 시 실패 안내·재시도 CTA |
| F-10 | `AutoPrepWorkView:78,174` | 재시도 버튼 onClick 빈 함수 | 실패 후 버튼 눌러도 무반응 | **A** | onRetry prop 배선(run.start 재호출 — failedOnly는 2차) |
| F-11 | `autoPrepApi.runStream` + `useOnboardingGuide:277-285` | 클라 무이벤트 타임아웃 없음 + allSettled 조건 → 스트림 단절 시 analyzing 림보 | 가이드가 분석 화면에 영원 | **A** | 무이벤트 N초 워치독 → running 파트 failed 처리 후 finalize |
| F-12 | `ChatbotController:731` | DONE 마킹이 run 시작 성공보다 먼저 — 실패 시 재개 경로 없음 | 인계 문구 후 아무 일 없음 + 대화는 일반 모드 | B | "면접 준비 다시 시작" 재진입 칩(restart 화이트리스트 이미 있음) 노출 |
| F-13 | `:493-497,509-515` | EXTRACTING 폴의 silent catch + row부재/케이스삭제 시 stale 판정 없음 | "잠시 후 다시" 영원(어제 넛지 패치는 프론트 예산만 해결) | **A** | N회/N분 초과 시 AWAIT_POSTING 리셋+재요청 안내(코드 게이트) |
| F-14 | `:400-406` | JOB/SKILLS에 AFFIRMATIVE·무의미 답 필터 없음 | 직무="네"로 프로필 저장 | B | 긍정 화이트리스트/1자 답변은 재질문(AWAIT_COMPANY의 `:538` 패턴 재사용) |
| F-15 | `:108-109` GUARDED_STEPS | EXTRACTING·AWAIT_MODE 미포함 → 질문 삼킴 | 대기 중 "얼마나 걸려요?" → "잠시 후 다시 보내주세요" | B | 두 단계를 GUARDED에 추가(오기록 없음 단계라 부작용 없음 — 검토 후) |
| F-16 | `SummaryAgent` system | 주입 방어 조항 없음(글 본문 통짜 입력) | 악성 글로 요약 출력 탈취 | B | community-chat과 동일한 주입 방어 문단 추가 |
| F-17 | fork(`IntakeAskService:400-425`)+메모리 | ③ tool-call/result 원문이 ① 컨텍스트로 유입(내부 id·placeholder) | 에이전트가 "id=65…" 같은 내부 표현 에코 [가설] | C | fork 복사 시 tool 메시지 제외(텍스트만) 검토 |
| F-18 | `useChatbot:411-434` | 음성 입력이 목업(SpeechRecognition 미구현, interimTranscript 무갱신) | 마이크 → "확인…"만 뜨고 무한 대기 | B | 데모에서 마이크 버튼 숨김 or disabled 툴팁 |
| F-19 | `app/pages/Support.tsx:15` | 제거 예정 ChatbotFullScreen이 `/support/chat` 라우트로 잔존(인테이크·칩·가이드 0) | 그 화면에선 ④가 생짜 텍스트로 노출 | B | 라우트 제거 or 위젯 열기로 리다이렉트 |
| F-20 | `OnboardingGuide:568-573`, `useAutoPrepRun:45` | fetch 예외 원문 노출(영문 가능) | "Failed to fetch" 류 노출 [가설] | C | 사용자 문구로 매핑 |
| F-21 | record 호출 5곳뿐(`:318,388,1017,1234,1371`) | ③ 인테이크·FALLBACK·확인턴 미로깅 + 라우터 점수 로그 없음 | 사고 시 재구성 불가(이번 감사도 이 공백에 걸림) | C | ③/FALLBACK에 record 추가, decide에 점수 debug 로그 |

## 5. 원인 클러스터 & 수정 배치

| 클러스터 | 포함 | 근본 원인 |
| --- | --- | --- |
| **CL1 placeholder 게이트 비대칭** | F-01, F-02 (+B 3중 문자열 정의) | ④에만 있는 placeholder 코드 게이트가 ③·표시 계층에 없음 |
| **CL2 ① 무상태 구간의 환각·낙하** | F-03, F-04, F-05 | ①에는 "직전 대화 맥락" 개념이 라우터에 없음 + LLM이 기능 경계를 모름 |
| **CL3 ④ 영속·복원 부재** | F-06, F-07 | ④가 LangChain 메모리를 우회(전용 인메모리 슬롯만) — 설계상 MVP 제외였던 것이 데모 재진입과 충돌 |
| **CL4 실행(SSE) 실패 경로** | F-08(D), F-09~F-12 | 성공 경로만 있는 계약 — 에러 터미널 이벤트·재시도·워치독 전무 |
| **CL5 EXTRACTING 좀비(잔여)** | F-13 | silent catch + stale 판정 부재(어제 수정은 프론트 예산·플래핑만) |
| **CL6 수집 입력 위생** | F-14, F-15 | 수집 단계 입력 클래스별 필터 불균일 |
| **CL7 노출·관측 잡감** | F-16~F-21 | 개별 |

**기수립 구조 수정 3종 커버리지 판정**
1. *라우터 앞 상태 게이트* → CL2 **부분만**: ③④는 이미 sticky/step 게이트가 있고, 버그2가 난 ① 구간은 "상태"
   자체가 없어서 이 수정으로는 안 잡힌다. "직전 턴 route=AGENT면 약신호를 에이전트로 잇는" **맥락 폴백**(F-04)이 별도 필요.
2. *고정 스텝 메시지 템플릿화* → ③④ 봇 문장은 이미 사실상 전부 코드 고정[사실]. CL1은 템플릿화가 아니라
   **템플릿에 넣는 값의 게이트** 문제라 미커버 — F-01 게이트가 별도 필요.
3. *예·아니오 지점 칩* → F-05, F-14는 커버. 버그2의 "준비 되셨나요?"는 **시스템이 던진 질문이 아니라서
   칩을 붙일 지점이 존재하지 않음** — 미커버(F-03/F-04로만 해결).
**3종이 못 덮는 클러스터: CL1(게이트), CL2 핵심(환각+맥락 폴백), CL3(영속), CL4(SSE), CL5(좀비).**

수정 배치 제안(데모 역산): ① CL4 F-09/F-10/F-11(실행 실패 가시화 — 데모 중 최악 그림 방지) → ② CL1 F-01(버그1 소멸)
→ ③ CL2 F-04+F-03(버그2 소멸) → ④ CL5 F-13 → ⑤ CL3(재진입) → ⑥ CL6/CL7.

## 6. D/B 소유 영역 인계 노트 (초안)

**D (AutoPrepOrchestrator·가상면접)**
- SSE 에러 계약: FE는 `error` 이벤트를 이미 파싱한다(`autoPrepApi.parseEvent`). `runStream` catch(`:108-110`)에서
  `completeWithError` 대신(또는 직전에) `send(emitter,"error",{message})` 발행을 요청. planner 예외처럼 `plan` 이전에
  죽는 경우가 특히 문제(FE에 아무 정보도 안 남음).
- 파트 핸들러 내부 예외가 항상 `part-done(status=FAILED)`로 감싸져 나오는지 확인 요청(F 관측으로는 runPart 계층 —
  코드상 확인 못함). 안 감싸지면 그 파트는 FE에서 영원히 running.
- `AutoPrepPlanner.resolveCase`의 "최근 건 자동 디폴트"는 F가 b3 가드(`IntakeAskService:127-141`)로 방어 중 —
  Planner 쪽에서 명시 선택 없는 자동 바인딩을 옵션화해 주면 가드 제거 가능.
- InterviewPage `?caseId` 소비 여부 — 위젯 인계 주석(`ChatbotWidget:557` "⚠️ D 확인 대상") 그대로 미해결이면
  면접 인계 후 지원 건 자동 선택이 안 된다.
- `AutoPrepIntakeService`(ai/autoprep 패키지)의 ready 메시지·게이트(F-01)는 챗봇 인테이크의 사용자 문장을 만든다 —
  수정 주체(F가 게이트를 밖에서 걸지 / D가 안에서 걸지) 합의 필요.

**B (공고 추출·OCR)**
- placeholder 문자열 `"기업명 확인 필요"/"직무명 확인 필요"`가 3곳에 독립 정의(`ApplicationCaseExtractionWorker:40-41`,
  `ApplicationCaseServiceImpl:54-55`, `ChatbotController:80-81`). 문구 변경 시 ④ 게이트·요약 스킵이 조용히 깨진다 —
  공용 상수(또는 case에 "메타 확정 여부" 플래그 컬럼) 제안.
- 추출 트랜잭션 분리 요청은 기존 문서 참조: `docs/F_B인계_공고추출_트랜잭션분리.md`.
- 품질 게이트가 SPA 셸 텍스트(삼성커리어스 메뉴/네비)를 PASS(89점)시켜 placeholder 케이스를 양산 — 게이트 강화 검토.

## 7. 확인 필요 (가정 없이 판단 불가)

1. **버그2 실물 대화 확정** — §2 체크리스트 1~3(response_log·memory JSON). 코드상 ① 에이전트 외 표면이 없다는
   증명은 했으나, 실제 그 대화의 직전 턴이 AGENT였는지는 DB를 봐야 확정.
2. `completeWithError` 시 브라우저 fetch reader 동작(throw vs 정상 종료) — F-20/F-11의 정확한 증상 분기.
   DevTools 네트워크 탭에서 스트림 강제 종료로 10분 내 확인 가능.
3. qwen3 `<think>` 실누출 여부 — yaml 이중 차단 설정은 있으나(`:221-222`) LangChain 버전 조합 실측 미확인.
4. fork 메모리의 tool-call/result 실제 포함 여부(F-17) — `chatbot_conversation_memory.messages_json`에서
   `TOOL_EXECUTION_RESULT` 타입 존재 확인 1쿼리.
5. `/api/chatbot/summarize-posts`의 SecurityConfig 인증 요구 여부(permitAll이면 비로그인 LLM 호출 표면) —
   SecurityConfig는 공통 영역이라 이번 스코프에서 미열람.
6. `AutoPrepIntakeService`의 팀 소유 판정 — `docs/FEATURE_OWNERSHIP.md` 기준 확인 후 F-01 수정 주체 확정.

# 커뮤니티 · 신고 · 챗봇

CareerTuner의 커뮤니티는 면접 후기·합격 전략·자기소개서·포트폴리오 피드백을 사용자끼리 주고받는 공간입니다. 단순 게시판을 넘어, 올라온 글을 AI가 자동으로 검열·태깅하고, 면접 후기에서 질문을 구조화 추출해 면접 RAG 지식으로 재활용하며, FAQ와 커뮤니티 글을 넘나드는 사이트 챗봇 에이전트가 이용 문의와 글 검색을 동시에 처리합니다. 담당 파트는 F입니다.

핵심은 "사람이 올린 콘텐츠를 로컬 LLM이 파이프라인으로 후처리한다"는 점입니다. 게시 트랜잭션이 커밋되면 검열·태깅·후기 추출이 비동기로 실행되고, 관리자에게는 판정 결과와 미응답 질문이 대시보드로 모입니다.

## 주요 기능

- 게시글·댓글 CRUD, 7종 카테고리 분류, 반응(좋아요 등)과 신고
- AI 자동 검열(유해성 판정 → 조건부 자동 숨김)과 검열 누적 시 사용자 단위 자동 제재
- AI 자동 태깅(신뢰도 임계 이상일 때만 태그 자동 적용)
- 면접 후기 → 질문 구조화 추출 → 면접 RAG 지식(`interview_knowledge`)으로 적재
- 신고 게시글 AI 분류(자동 숨김 없이 관리자 참고용 판정만 저장)
- FAQ·커뮤니티 통합 사이트 챗봇: 임베딩 기반 FAQ 게이트 + LangChain4j 에이전트(qwen3:8b) + 인테이크 라우팅
- 커뮤니티 글 임베딩 기반 2단계 검색(SQL 후보 좁히기 → bge-m3 코사인)
- 관리자 모더레이션 콘솔, FAQ 관리, 챗봇 미응답·임계 대시보드

## 핵심 구현

### 1. 게시글·댓글·신고 API

사용자 진입점은 `CommunityPostController`(`/api/community/posts`)로, 목록·핫글(`GET /hot`)·상세·작성·수정·삭제·AI 태그 조회(`GET /{postId}/ai-tags`)를 제공합니다. 반응은 `ReactionController`(`/api/community/reactions`), 신고는 `ReportController`(`/api/community/reports`)가 담당하며 신고 사유는 `ReportReason` enum(`SPAM`, `ABUSE`, `FALSE_INFO`, `PRIVACY`, `OTHER`)으로 고정합니다. 게시글 카테고리는 `PostCategory` enum 7종(`JOB_REVIEW` 취업후기, `INTERVIEW_REVIEW` 면접후기, `JOB_QUESTION` 직무질문, `SUCCESS_STRATEGY` 합격전략, `PORTFOLIO_FEEDBACK` 포트폴리오, `CERTIFICATE_REVIEW` 자격증후기, `FREE` 자유게시판)로 정의됩니다. 매퍼는 `CommunityPostMapper`, `CommunityCommentMapper`, `CommunityTagMapper`, `ReactionMapper`, `ReportMapper`이며 XML은 `resources/mapper/community/**`에 있습니다.

### 2. 트랜잭션 커밋 후 비동기 AI 파이프라인

게시글/댓글 저장이 커밋되면 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("moderationExecutor")` 리스너들이 각 작업을 비동기로 실행합니다. 리스너는 `PostModerationListener`, `CommentModerationListener`, `PostTagListener`, `InterviewExtractListener`, `ReportClassifyListener`로 나뉘고, 실제 판정은 모두 `PostModerationService`가 수행합니다. AFTER_COMMIT을 쓰기 때문에 롤백된 글은 검열되지 않습니다.

`PostModerationService`는 의도적으로 `@Transactional`을 피합니다. Ollama 호출(최대 30초)을 트랜잭션으로 묶으면 DB 커넥션을 오래 점유해 풀이 고갈되기 때문입니다. 대신 순수 DB 단계인 `applyAiTags()`만 별도 `@Transactional`로 묶고, self-invocation 함정을 피하기 위해 `@Lazy`로 자기 프록시(`self`)를 주입해 프록시 경유로 호출합니다.

작업 상태는 `PostAiResultMapper`/`CommentAiResultMapper`가 `upsertPending → complete/fail`로 기록하며(`AiTaskType`: `MODERATION`, `SUMMARY`, `EMBEDDING`, `REPORT`, `TAG`, `INTERVIEW_EXTRACT`), 재시도 시 `attempt_count`가 증가합니다. 누락·실패한 작업은 `ModerationRetryScheduler`가 `@Scheduled(initialDelay = 90s, fixedDelay = 300s)`로 재실행합니다.

### 3. AI 검열과 자동 제재

`moderate(postId)`는 UPSERT → 게시글 조회 → 설정 스냅샷 → 판정 → 결과 저장 → 조건부 숨김 순으로 진행합니다. 판정은 순수 함수 `judge(title, content)`가 담당하는데, Ollama에 structured output 스키마(`MODERATION_SCHEMA`: `toxic`, `category`∈{normal, abuse, spam, ad}, `confidence`)를 주고 JSON을 받습니다. 관리자 설정의 엄격도(`Strictness`: `STRICT`/`NORMAL`/`LENIENT`)에 따라 시스템 프롬프트가 조립되며(`prompts/moderation-system.txt` + `prompts/strictness/<STRICT|NORMAL|LENIENT>.txt`), 판정 결과 JSON에는 적용된 엄격도·숨김 임계(`applied` 스냅샷)가 병합되어 감사 근거로 남습니다.

숨김은 `result.toxic() && confidence >= hideThreshold`일 때 `postMapper.hideIfPublished()`로 조건부 flip 합니다(댓글은 `hideCommentIfPublished()`). affected-rows가 있을 때만 카운트를 감소시키고 작성자에게 `POST_HIDDEN` 알림을 보냅니다. 숨김이 성립하면 `UserSanctionService.sanctionIfNeeded()`가 호출되어, 해당 사용자의 누적 숨김 글 수가 제재 임계 이상이면 A 도메인 인프라(`AdminUserMapper`, `AuthMapper`)를 재사용해 `BLOCKED`로 자동 차단하고 세션을 폐기합니다. 게시글 단위 숨김 임계(신뢰도)와 사용자 단위 제재 임계는 분리돼 있습니다.

신고 게시글은 `classify(postId)`가 별도로 처리합니다. 같은 `judge()` 두뇌를 쓰되 자동 숨김·알림 없이 판정 결과(`AiTaskType.REPORT`)만 저장해, 관리자가 신고를 처리할 때 참고 정보로만 씁니다.

### 4. 면접 후기 → 질문 추출 → 면접 RAG 적재

`extractInterviewQuestions(postId)`는 `INTERVIEW_REVIEW` 카테고리 글에서 면접 질문을 구조화 추출합니다. `EXTRACT_SCHEMA`(회사·직무·질문 배열, 질문별 `questionType`∈{TECH, PERSONALITY, SITUATION, EXPECTED, FOLLOW_UP}·context·followUps)를 Ollama에 주고, 결과를 `community_interview_review.ai_extracted_questions`에 저장한 뒤, 각 질문을 `InterviewKnowledge`(kind=`QUESTION_BANK`, source=`CareerTuner 커뮤니티 #{postId}`)로 만들어 `interviewKnowledgeMapper.insert()`로 면접 RAG 인덱스에 적재합니다. 즉, 커뮤니티가 D 파트 면접 도메인의 질문 은행을 자동으로 채우는 구조입니다.

이 과정에는 LLM 출력을 신뢰하지 않는 코드 방어가 촘촘합니다. 사용자가 직접 입력한 질문은 `mergeUserAndAiQuestions()`가 verbatim으로 시딩하고 AI 추출분 중 중복만 제거하며(정규화 비교), AI가 `"null"` 문자열이나 백틱/인사말을 섞어 반환해도 `sanitize*`·`extractJsonObject()`가 정규화합니다. 추출 질문이 비면 기존 지식을 삭제하지 않아(delete-후-insert의 비원자성 대비) RAG 지식이 사라지는 사고를 막습니다.

### 5. 통합 사이트 챗봇 — FAQ 게이트 + 에이전트 + 인테이크 라우팅

챗봇 입구는 `ChatbotController`의 `POST /api/chatbot/ask` 하나입니다. 첫 턴은 `UnifiedChatRouter.decide()`가 세 신호(faqScore·intakeScore·communityScore)를 임베딩 코사인으로 계산해 라우팅합니다.

- **FAQ 경로(`faqPath`)**: `ChatbotService.searchFaqHits()`가 질문 임베딩과 발행 FAQ 임베딩의 코사인 top-1을 구하고, `faqGateThreshold` 이상이면 에이전트·모델을 우회한 결정적 즉답을 돌려줍니다(`FAQ_FAST`). 미달이면 에이전트로 넘깁니다.
- **커뮤니티 에이전트 경로(`agentPath`)**: `CommunityChatAgent`(LangChain4j `AiServices`)가 `CommunityTools`의 read-only 툴을 스스로 판단해 호출합니다. 툴은 `searchCommunityPosts`(2단계 검색), `getPostContent`(글 본문), `searchFaq`(운영 FAQ)이며 write/파괴 액션은 절대 노출하지 않습니다.
- **인테이크 경로**: "면접 준비해줘" 같은 행위 의도가 커뮤니티 조회·FAQ를 `routeBoundary` 이상 앞서면 AI 오케스트레이터(인테이크) 입구로 직행하고, 경계 구역에서는 화행 분류(`SpeechActClassifier`) 1회로 확인 턴을 띄웁니다.

라우팅 튜닝은 `UnifiedChatRouter` 주석에 실측 근거(진짜 인테이크 vs 커뮤니티 조회 의도의 분리축)가 기록돼 있습니다. `INTAKE_SEEDS`(행위)와 `COMMUNITY_SEEDS`(조회) 시드를 두어, 임베딩상 겹치던 "면접 후기 보여줘"(조회)가 인테이크로 새던 회귀를 veto로 막습니다.

설계상 두 개의 챗봇 자산이 분리돼 있습니다. `support/chatbot` 패키지는 FAQ 임베딩·라우팅·운영 로깅을, `ai/chat` 패키지는 LangChain4j 에이전트·툴·메모리를 담당합니다. 모델도 검열/FAQ 답변용(`ai.ollama.model` 기본 `gemma4`)과 에이전트용(`langchain4j.ollama.chat-model.model-name` = `qwen3:8b`)을 env(`AI_OLLAMA_MODEL`·`AI_AGENT_MODEL`)로 물리 분리합니다.

### 6. 에이전트 가드레일과 응답 접지

로컬 8B 모델의 폭주·환각을 막는 장치가 코드 곳곳에 있습니다.

- **tool-call 캡**: `CommunityAgentConfig`가 `@AiService` 자동등록 대신 `AiServices.builder(...).maxSequentialToolsInvocations(3)`으로 연속 툴 호출 상한을 겁니다.
- **structured output 미사용**: `langchain4j.ollama` 설정에서 `RESPONSE_FORMAT_JSON_SCHEMA`를 켜지 않습니다. Ollama는 format이 걸리면 tool_call을 생략하고 JSON만 반환해, 검색 의도에도 `searchCommunityPosts`를 안 부르는 충돌이 실측됐기 때문입니다. 그래서 에이전트는 String을 반환합니다.
- **링크 접지(anti-hallucination)**: 응답 링크는 모델 JSON이 아니라 이번 턴에 툴이 실제로 돌려준 출처(`SearchTrace`)에서만 만듭니다. 커뮤니티 글 링크는 `^/community/posts/\d+$` 화이트리스트를 통과한 것만 노출합니다.
- **quickReplies·요약 칩**: 후속 추천 칩은 별도 `QuickReplyAgent`가 생성하고, 이번 턴에 글이 실제 제시됐을 때만 글 관련 칩을 허용하는 게이트를 둡니다. 추천 후기 2건 이상이면 묶음 요약 칩을 붙이고, 클릭 시 `POST /api/chatbot/summarize-posts`가 `SummaryAgent`로 압축 요약합니다.

미달·미응답 질문은 `agentPath`에서 `ChatbotService.analyzeMiss()`로 top-1 유사도를 뽑아 `UnansweredQuestionService.record()`에 적재합니다. 이는 관리자 대시보드(`/api/admin/chatbot/unanswered`)에서 군집화·FAQ 초안 생성(`/unanswered/{id}/draft`)·FAQ 전환(`/unanswered/{id}/convert`)으로 이어지는 개선 루프의 시작점입니다.

### 7. 커뮤니티 글 2단계 검색과 임베딩

`CommunityPostSearchService.search()`는 글 수가 늘어도 코사인 비용이 전체가 아니라 후보 수에 비례하도록 2단계로 나눕니다. 1단계에서 SQL(`findSearchCandidates`, 키워드 LIKE + 카테고리)로 후보를 좁히고, 후보가 최소치 미만이면 카테고리·키워드를 단계적으로 완화해 재현율을 지킵니다. 2단계에서 후보 안에서만 bge-m3 코사인을 계산하고, 통과 결과가 0이면 완화 임계로 딱 한 번 재선별합니다(루프 아님).

임베딩은 `CommunityEmbeddingService.embedAllPosts()`가 미임베딩 발행 글을 배치로 적재해 `community_post_embedding`에 upsert 하며, 관리자 `POST /api/admin/community/embed-all`로 트리거합니다. FAQ 임베딩은 `ChatbotService.embedAll()` + `POST /api/admin/faq/embed-all`이 담당합니다. 임베딩 생성·코사인 계산은 `OllamaEmbeddingClient`(bge-m3, 1024차원)와 `CosineSimilarity`를 두 도메인이 공유합니다.

### 8. 관리자 콘솔

- **모더레이션**: `AdminModerationController`(`/api/admin/ai`)가 목록·상세·복원(`/moderation/{postId}/restore`)·삭제·통계·댓글 모더레이션·설정 조회/수정(`PATCH /moderation/settings`: 엄격도·숨김 임계·제재 임계·차단 일수)과 백필(`/moderation/backfill`)·단건 재실행(`/moderation/{postId}/run`)·테스트(`/moderation-test`)를 제공합니다.
- **FAQ**: `AdminFaqController`(`/api/admin/faq`)가 FAQ CRUD를 제공하고, 프런트는 `admin/features/faqs`(작성·목록)에 대응합니다.
- **챗봇 운영**: `AdminChatbotMetricsController`(`/api/admin/chatbot/metrics`), `AdminUnansweredController`(미응답 질문·군집·초안·전환), `AdminChatbotThresholdController`(`/threshold/preview`)로 응답 품질·임계를 모니터링합니다.
- **가이드라인**: `GuidelineController`(`GET /api/community/guidelines/published`)가 사용자에게 커뮤니티 가이드라인을 노출하며, 검열 숨김 알림 링크(`/community?view=guidelines`)와 연결됩니다.

### 9. 개인화 피드와 대화 생명주기

피드는 프로필 직무·스킬과 최근 반응 카테고리를 신호로 사용해 개인화 후보 70%, 신선·인기 후보 30%를 섞습니다. 신호가 부족하면 기본 피드로 돌아가며 사용자-사용자 협업 필터링을 구현했다고 과장하지 않습니다.

챗봇 인테이크 slot은 JVM 메모리가 아니라 `chatbot_intake_slot`에 계정 범위로 저장합니다. 사용자는 대화 목록을 조회하고 다시 불러오거나 명시적으로 삭제할 수 있습니다. 자동 TTL 청소는 별도 정책이 확인되기 전까지 완료로 표현하지 않습니다.

브라우저 마이크는 실제 Web Speech STT를 시작하고 미지원 환경을 안내합니다. 고객센터 문의 글과 답글은 소유권을 확인한 첨부 파일을 지원하며, 챗봇 추천 글은 모아보기와 원문 deep link로 연결됩니다. 전용 후기 요약 파이프라인은 아직 확인되지 않은 한계로 남깁니다.

## 설계 결정과 트레이드오프

- **로컬 LLM 파이프라인을 트랜잭션 밖으로**: 검열·태깅·추출은 모두 게시 트랜잭션 커밋 후 비동기로 돌립니다. 사용자 체감 지연을 없애고 DB 커넥션 점유를 피하는 대신, 게시 직후 잠깐 검열 전 상태가 노출될 수 있습니다. 이를 UPSERT-PENDING + 스케줄러 재시도로 보완합니다.
- **프롬프트를 신뢰하지 않는다**: 사용자 질문 verbatim 보존, 링크 화이트리스트, `"null"` 문자열 정규화, 카테고리명 태그 제거 등은 전부 프롬프트 지시가 아니라 코드가 보증합니다. 8B 로컬 모델의 비결정성을 구조로 흡수합니다.
- **structured output vs tool_call**: Ollama에서 둘을 동시에 얻을 수 없어(format을 켜면 tool 호출을 건너뜀), 에이전트는 String 반환 + 툴 출력 접지 방식을 택했습니다. FAQ 즉답 경로만 임베딩 게이트로 결정성을 확보합니다.
- **두 챗봇 자산 분리**: FAQ 검색과 에이전트 대화를 별 패키지·별 모델로 나눠, 검열용 모델 교체가 에이전트 라우팅에 영향을 주지 않도록 격리했습니다.
- **자동 제재의 보수성**: 이미 `BLOCKED`/`DORMANT`/`DELETED`인 사용자는 자동 제재를 건너뛰어 관리자 수동 조치와 충돌하지 않게 했습니다. 시스템 변경은 actor=null로 기록합니다.

## 데이터 · 연동

| 구분 | 항목 |
| --- | --- |
| 주요 테이블 | `community_post`, `community_comment`, `community_tag`(+post_tag), `post_report`/`comment_report`, `post_reaction`/`comment_reaction`, `community_interview_review`, `post_ai_result`/`comment_ai_result`, `community_post_embedding`, `faq`(임베딩 컬럼), `moderation_setting`, `unanswered_question`, `chatbot_response_log`, `chat_memory` |
| 연동 도메인 | 면접(D) `interview_knowledge` 적재, 사용자(A) 상태/제재, 인테이크(AI 오케스트레이터) 라우팅, 알림(`notification`) |
| 로컬 LLM | Ollama — 임베딩 `bge-m3`(1024차원), 검열/FAQ 답변 `gemma4`(`ai.ollama.model`), 에이전트 `qwen3:8b`(`langchain4j.ollama`) |
| 외부 규약 | 응답은 전부 `ApiResponse<T>` envelope, 영속성은 MyBatis(`@Mapper` + XML) |

## 사용 기술

- **백엔드**: Spring Boot 4 / Java 21, MyBatis, MySQL 8
- **AI/에이전트**: LangChain4j(`AiServices`, `@Tool`, `ChatMemory`), Ollama(qwen3:8b·gemma4·bge-m3), 임베딩 코사인 검색(2단계 후보 좁히기)
- **비동기·스케줄**: Spring `@TransactionalEventListener(AFTER_COMMIT)`, `@Async` 전용 스레드 풀, `@Scheduled` 재시도
- **프런트엔드**: React 19 + Vite 8 + TypeScript(사용자 `features/community`, 관리자 `admin/features/{moderation,faqs}`)

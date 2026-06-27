package com.careertuner.support.chatbot;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

import com.careertuner.ai.chat.ChatAskRequest;
import com.careertuner.ai.chat.ChatAskResponse;
import com.careertuner.ai.chat.ChatHistoryResponse;
import com.careertuner.ai.chat.ChatHistoryResponse.ChatHistoryMessage;
import com.careertuner.ai.chat.ChatSessionSummary;
import com.careertuner.ai.chat.ChatResponse;
import com.careertuner.ai.chat.ChatResponse.SiteLink;
import com.careertuner.ai.chat.ChatSummarizeRequest;
import com.careertuner.ai.chat.CommunityChatAgent;
import com.careertuner.ai.chat.CommunityTools;
import com.careertuner.ai.chat.FastPathService;
import com.careertuner.ai.chat.MessageSanitizer;
import com.careertuner.ai.chat.MyBatisChatMemoryStore;
import com.careertuner.ai.chat.QuickReplyAgent;
import com.careertuner.ai.chat.SearchTrace;
import com.careertuner.ai.chat.SummaryAgent;
import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.intake.IntakeAskService;
import com.careertuner.ai.intake.dto.IntakeAskResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.search.PostHit;
import com.careertuner.profile.mapper.ProfileMapper;

@RestController
@RequestMapping("/api")
public class ChatbotController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotController.class);

    /** 환각 링크 차단: 실제 커뮤니티 글 경로만 통과 (모델이 만든 임의 url 제거). */
    private static final Pattern LINK_WHITELIST = Pattern.compile("^/community/posts/\\d+$");

    /** 확인 응답에서 "시작" 쪽으로 본다(그 외는 안전하게 ① 로). */
    private static final List<String> AFFIRMATIVE = List.of(
            "시작", "네", "예", "응", "ㅇㅇ", "그래", "좋아", "할래", "해줘", "ok", "오케이", "yes");

    /** 오케스트레이터 모드 이탈 신호(모드 활성 중에만 판정). 배너 ⏏ 도 이 키워드를 보낸다. */
    private static final List<String> EXIT_COMMANDS = List.of(
            "그만", "취소", "종료", "일반상담", "나가기", "중단", "그만할래");

    private final CommunityChatAgent agent;
    private final QuickReplyAgent quickReplyAgent;
    private final SearchTrace searchTrace;
    private final MyBatisChatMemoryStore memoryStore;
    private final ChatbotService chatbotService;
    private final FastPathService fastPathService;
    private final UnansweredQuestionService unansweredQuestionService;
    private final ResponseLogService responseLogService;
    private final ChatbotProperties chatbotProperties;
    private final UnifiedChatRouter router;
    private final RouteConfirmStore routeConfirmStore;
    private final IntakeAskService intakeAskService;
    private final IntakeModeStore intakeModeStore;
    private final CommunityTools communityTools;
    private final SummaryAgent summaryAgent;
    // (a) 깡통계정 온보딩 게이트용 read 의존(호출만, A·B 도메인 무수정).
    private final ProfileMapper profileMapper;
    private final ApplicationCaseService applicationCaseService;

    public ChatbotController(CommunityChatAgent agent,
                            QuickReplyAgent quickReplyAgent,
                            SearchTrace searchTrace,
                            MyBatisChatMemoryStore memoryStore,
                            ChatbotService chatbotService,
                            FastPathService fastPathService,
                            UnansweredQuestionService unansweredQuestionService,
                            ResponseLogService responseLogService,
                            ChatbotProperties chatbotProperties,
                            UnifiedChatRouter router,
                            RouteConfirmStore routeConfirmStore,
                            IntakeAskService intakeAskService,
                            IntakeModeStore intakeModeStore,
                            CommunityTools communityTools,
                            SummaryAgent summaryAgent,
                            ProfileMapper profileMapper,
                            ApplicationCaseService applicationCaseService) {
        this.agent = agent;
        this.quickReplyAgent = quickReplyAgent;
        this.searchTrace = searchTrace;
        this.memoryStore = memoryStore;
        this.chatbotService = chatbotService;
        this.fastPathService = fastPathService;
        this.unansweredQuestionService = unansweredQuestionService;
        this.responseLogService = responseLogService;
        this.chatbotProperties = chatbotProperties;
        this.router = router;
        this.routeConfirmStore = routeConfirmStore;
        this.intakeAskService = intakeAskService;
        this.intakeModeStore = intakeModeStore;
        this.communityTools = communityTools;
        this.summaryAgent = summaryAgent;
        this.profileMapper = profileMapper;
        this.applicationCaseService = applicationCaseService;
    }

    /**
     * (a) 깡통계정 온보딩 게이트 판정: 프로필 행 없음 + 지원 건 0건. 코드(DB 조회)로 결정 — 모델(qwen3)에 안 물음(§6-2).
     * 비로그인(userId==null)은 대상 아님(프로필 저장에 사용자 필요 + 챗봇은 인증 필수). 조회 실패 시 false(보수적: 온보딩 미진입, 기존 흐름).
     * A(ProfileMapper)·B(ApplicationCaseService)의 기존 read 메서드 호출만 — 그쪽 코드 무수정.
     */
    private boolean isBlankAccountForOnboarding(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return profileMapper.findByUserId(userId) == null
                    && applicationCaseService.list(userId, null, false).isEmpty();
        } catch (RuntimeException ex) {
            log.warn("온보딩 게이트 판정 실패(온보딩 미진입으로 처리): {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 사용자 질문 → 통합 라우팅 → ① 커뮤니티 FAQ/에이전트 또는 ③ 인테이크 입구.
     * POST /api/chatbot/ask  body: { question, conversationId? }
     *
     * <p><b>라우팅(첫 턴 판정 전용):</b> nav fast-path 유지 → faqScore vs intakeScore 비교 →
     * 명확구역(|diff|≥0.1) argmax 로 결정적 라우팅, 경계구역만 화행분류 1회. COMMAND 는 비대칭 안전판으로
     * 명확구역이면 ③ 즉시 진입, 경계구역이면 확인 1턴을 띄운다. 인테이크 진입 후 sticky 유지는 다음 PR.</p>
     */
    @PostMapping("/chatbot/ask")
    public ApiResponse<ChatAskResponse> ask(@RequestBody ChatAskRequest req,
                                            @AuthenticationPrincipal AuthUser authUser) {
        if (req == null || req.question() == null || req.question().isBlank()) {
            return ApiResponse.error("BAD_REQUEST", "질문을 입력해 주세요.");
        }

        // 로그인 시 user_id 기록 → 나중에 "이전 대화 복원" 대상이 된다. 비로그인은 null(익명).
        Long userId = authUser != null ? authUser.id() : null;
        Long conversationId = req.conversationId() != null
                ? req.conversationId()
                : memoryStore.createConversation(userId);
        String question = req.question();

        // 이탈 신호("그만"/⏏): 메모리 sticky(활성) 또는 DB 영속(PENDING/READY) 인테이크면 라우터 전에 즉시 복귀.
        // 슬롯이 있으면 DONE 으로 닫아(재복원 차단) 재시작된 PENDING 세션도 깔끔히 중단되고, READY 세션의 "그만"이
        // 라우터 FALLBACK 으로 새는 것(버그2)도 막는다. (status 3단계 — sticky 와 영속 양쪽을 한 핸들러로 통합)
        if (req.conversationId() != null && isExitCommand(question)
                && (intakeModeStore.isActive(conversationId)
                        || intakeAskService.hasOpenIntakeSlot(conversationId))) {
            intakeModeStore.exit(conversationId);
            intakeAskService.closeIntakeSession(conversationId);
            return ApiResponse.ok(new ChatAskResponse(
                    conversationId,
                    "일반 상담 모드로 돌아왔어요. 무엇이든 물어보세요.",
                    List.of(), List.of(), "이탈", null, false, null));
        }

        // sticky 모드(오케스트레이터 유지): 이미 ③ 에 머무는 대화는 라우팅·FAQ·NAV 를 전부 건너뛰고 ③ 직행한다.
        // (이탈은 위에서 이미 처리됨 — 여기 도달하면 이탈 신호 아님.)
        if (intakeModeStore.isActive(conversationId)) {
            return ApiResponse.ok(enterIntake(conversationId, question, userId, "③(유지)",
                    req.selectedCaseId(), req.selectedModeCode()));
        }

        // 영속 세션 복원: 재시작/재방문으로 메모리 sticky 는 없지만 DB 에 PENDING 인테이크(지원건) 세션이면
        // 되살려 ③ 로 잇는다(슬롯은 IntakeAskService 가 DB 에서 복원). READY/DONE 은 isPersistedIntakeSession=false.
        if (req.conversationId() != null
                && intakeAskService.isPersistedIntakeSession(conversationId)) {
            intakeModeStore.enter(conversationId);
            return ApiResponse.ok(enterIntake(conversationId, question, userId, "③(복원)",
                    req.selectedCaseId(), req.selectedModeCode()));
        }

        // Fast-path: 순수 내비 질의는 LLM·검색 우회 즉답 (서버 신뢰 링크라 화이트리스트 검증 생략).
        Optional<ChatResponse> fast = fastPathService.tryFastPath(question);
        if (fast.isPresent()) {
            ChatResponse fr = fast.get();
            responseLogService.record(conversationId, userId, question,
                    "NAV_FAST", false, null, null, false);
            return ApiResponse.ok(new ChatAskResponse(
                    conversationId, fr.message(), fr.links(), fr.quickReplies(), "NAV", null, false, null));
        }

        // (a) 깡통계정 온보딩 게이트: 프로필 행 없음 + 지원 건 0건 = 순수 깡통 → 온보딩 분기.
        //   여기까지 온 건 exit/sticky/DB복원/fastPath 가 아닌 신규 라우팅 턴 — 비-깡통은 false 로 통과해 아래 기존 흐름.
        //   스텁(분기 확인용): sticky(intakeModeStore=인테이크 case 흐름) 안 건드림(깡통엔 case 없어 부적합). 매 턴 재판정.
        if (isBlankAccountForOnboarding(userId)) {
            responseLogService.record(conversationId, userId, question, "ONBOARDING", false, null, null, false);
            return ApiResponse.ok(new ChatAskResponse(
                    conversationId,
                    "온보딩에 진입했어요. (프로필·지원 건이 아직 없어 온보딩 대상이에요 — 대화 수집은 다음 단계에서 붙습니다.)",
                    List.of(), List.of(), "④온보딩", null, false, null));
        }

        // 확인 대기(1턴) 소비: 이 턴은 라우팅을 돌리지 않는다. (오분류 안전판 A)
        if (routeConfirmStore.consumePending(conversationId)) {
            if (isAffirmative(question)) {
                return ApiResponse.ok(enterIntake(conversationId, question, userId, "③(확인후)",
                        req.selectedCaseId(), req.selectedModeCode()));
            }
            return ApiResponse.ok(faqPath(conversationId, question, userId, "①(확인후)"));
        }

        // 통합 라우팅 판정.
        UnifiedChatRouter.Decision d = router.decide(question);
        switch (d.target()) {
            case INTAKE_DIRECT -> {
                return ApiResponse.ok(enterIntake(conversationId, question, userId, "③",
                        req.selectedCaseId(), req.selectedModeCode()));
            }
            case INTAKE_CONFIRM -> {
                routeConfirmStore.markPending(conversationId);
                return ApiResponse.ok(new ChatAskResponse(
                        conversationId,
                        "면접 준비를 도와드릴까요?",
                        List.of(),
                        List.of("시작", "그냥 질문이에요"),
                        "확인반환",
                        null,
                        false,
                        null));
            }
            case FALLBACK -> {
                // 약신호(FAQ도 의도도 불명확) → 에이전트로 보내지 않고 정중한 되묻기로 끊는다.
                return ApiResponse.ok(new ChatAskResponse(
                        conversationId,
                        "질문을 정확히 이해하지 못했어요. 좀 더 구체적으로 말씀해 주시겠어요? "
                                + "(예: \"환불 어떻게 해요\" 같은 이용 문의, 또는 \"네이버 백엔드 면접 준비해줘\" 같은 작업 요청)",
                        List.of(),
                        List.of(),
                        "되묻기",
                        null,
                        false,
                        null));
            }
            case AGENT -> {
                // catch-all: 커뮤니티 글 검색·인사·잡담 → FAQ 게이트 우회하고 에이전트 직행.
                return ApiResponse.ok(agentPath(conversationId, question, userId, "①에이전트"));
            }
            default -> {
                // FAQ Target → faqPath 게이트(즉답/미달 시 에이전트).
                return ApiResponse.ok(faqPath(conversationId, question, userId, "①"));
            }
        }
    }

    private boolean isAffirmative(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        return AFFIRMATIVE.stream().anyMatch(norm::contains);
    }

    /** 모드 활성 중 이탈 신호 판정("그만"/"취소"/⏏ 등). */
    private boolean isExitCommand(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        return EXIT_COMMANDS.stream().anyMatch(norm::contains);
    }

    /**
     * ③ 인테이크 입구로 데려다주고(한 턴) sticky 모드를 갱신한다.
     * ready 면 RUN 을 프런트 SSE 가 이어받으므로 sticky 종료, 아니면 모드 유지(다음 턴도 ③ 직행).
     * inOrchestration 은 항상 true — ready 전환 턴도 위젯이 배너를 유지한 채 실행 화면으로 넘어가야 하므로.
     */
    private ChatAskResponse enterIntake(Long conversationId, String question, Long userId, String route,
                                        Long selectedCaseId, String selectedModeCode) {
        IntakeAskResponse r = intakeAskService.ask(userId, question, conversationId, selectedCaseId, selectedModeCode);
        // 지원건 세션 fork 가 일어나면 응답 conversationId 가 새 id 로 바뀐다 — sticky 도 새 id 기준으로 옮긴다.
        Long effectiveId = r.conversationId();
        if (effectiveId != null && !effectiveId.equals(conversationId)) {
            intakeModeStore.exit(conversationId); // 원(잡담) 대화의 sticky 정리
        }
        if (r.ready()) {
            intakeModeStore.exit(effectiveId);
        } else {
            intakeModeStore.enter(effectiveId);
        }
        return new ChatAskResponse(
                effectiveId,
                r.message(),
                List.of(),
                List.of(),
                route,
                new ChatAskResponse.IntakeStep(
                        r.ready(), r.nextAsk(), r.autoPrepRequest(),
                        toCandidates(r.candidates()), toModes(r.modes())),
                true,
                null);
    }

    /** 지원 건 후보 → 칩 렌더용 최소 필드로 축약. */
    private List<ChatAskResponse.CaseCandidate> toCandidates(List<ApplicationCaseResponse> cases) {
        if (cases == null) {
            return List.of();
        }
        return cases.stream()
                .map(c -> new ChatAskResponse.CaseCandidate(
                        c.id(), c.companyName(), c.jobTitle(), c.status()))
                .collect(Collectors.toList());
    }

    /** 면접 모드 선택지 → 칩 렌더용으로 변환. */
    private List<ChatAskResponse.ModeOption> toModes(List<AutoPrepIntakeResponse.ModeOption> modes) {
        if (modes == null) {
            return List.of();
        }
        return modes.stream()
                .map(m -> new ChatAskResponse.ModeOption(m.code(), m.label()))
                .collect(Collectors.toList());
    }

    /**
     * ① 커뮤니티 FAQ/에이전트 경로(기존 동작 보존).
     * FAQ 임베딩 게이트가 top-1 코사인 ≥ 임계를 통과하면 결정적 FAQ 즉답, 미달이면 에이전트(툴 호출).
     */
    private ChatAskResponse faqPath(Long conversationId, String question, Long userId, String route) {
        // FAQ 임베딩 게이트: 질문 원문 임베딩 1회로 top-1 FAQ 코사인 점수를 보고 결정적으로 분기한다.
        // (키워드 isFaqIntent 대체 — LLM 을 FAQ 판정 경로에서 빼서 검색어 재생성 flip 비결정성을 구조적으로 제거.)
        List<FaqHit> faqHits = chatbotService.searchFaqHits(question);
        double faqGate = chatbotProperties.getFaqGateThreshold();
        if (!faqHits.isEmpty() && faqHits.get(0).score() >= faqGate) {
            return faqAnswerFrom(conversationId, question, userId, faqHits, route);
        }
        // 게이트 미달 → 커뮤니티 에이전트(통합 라우터 AGENT catch-all 과 동일 경로).
        return agentPath(conversationId, question, userId, route);
    }

    /**
     * ① 커뮤니티 에이전트 직행(FAQ 게이트 우회). {@link #faqPath} 게이트 미달과 통합 라우터 AGENT(catch-all)가 공유.
     * 글 검색(searchCommunityPosts)·인사/잡담 자연응답은 에이전트가 스스로 판단해 처리한다(시스템 프롬프트).
     * 원문 raw top-1 로 운영 FAQ공백 수집을 보존한다.
     */
    private ChatAskResponse agentPath(Long conversationId, String question, Long userId, String route) {
        searchTrace.clear();
        try {
            ChatbotService.FaqMiss miss = null;
            try {
                miss = chatbotService.analyzeMiss(question);
            } catch (Exception e) {
                // 임베딩/Ollama 이상 → FAQ 공백 수집만 스킵(챗봇 응답엔 무관).
                log.debug("미스 분석 스킵(임베딩/Ollama 이상): {}", e.getMessage());
            }
            if (miss != null) {
                unansweredQuestionService.record(question, miss.topSimilarity(),
                        miss.embeddingJson(), miss.bestFaqId(), userId, conversationId);
            }

            // 에이전트는 String 반환(툴 정상 동작). 메시지는 자유 생성 → 마크다운 잔재 평문화.
            String message = MessageSanitizer.stripMarkdown(agent.chat(conversationId, question));

            // links 접지: 모델 JSON 이 아니라 이번 턴에 툴이 실제로 돌려준 출처(커뮤니티 글 + FAQ 링크)에서만 생성.
            List<SiteLink> links = collectLinks();

            // quickReplies: 보조 기능 → 2차 호출이 실패해도 핵심(message+links)은 정상 반환.
            // 글 제시 여부를 finally clear 전에 읽어 게이트로 넘긴다(이번 턴 검색 툴이 글을 돌려줬을 때만 글 칩 허용).
            boolean postsPresented = !searchTrace.snapshot().isEmpty();
            List<String> quickReplies = suggestQuickReplies(question, message, postsPresented);

            // 글이 2개 이상 제시된 턴에서만 묶음 요약 칩 주입(snapshot 은 finally clear 전에 읽는다).
            ChatAskResponse.SummaryChip summaryChip = buildSummaryChip(searchTrace.snapshot());

            // best-effort 적재(AGENT). FAQ 근거 여부 = 이번 턴 searchFaq 링크 존재(finally clear 전에 읽음).
            // 유사도 = 게이트 산출 원문 raw top-1(없으면 null). 전환 없음.
            responseLogService.record(conversationId, userId, question,
                    "AGENT", !searchTrace.faqLinks().isEmpty(),
                    miss != null ? miss.topSimilarity() : null, null, false);

            return new ChatAskResponse(conversationId, message, links, quickReplies, route, null, false, summaryChip);
        } catch (Exception e) {
            log.error("챗봇 에이전트 응답 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
            return new ChatAskResponse(
                    conversationId,
                    "지금은 답변을 생성하기 어렵습니다. 잠시 후 다시 시도해 주세요.",
                    List.of(),
                    List.of(),
                    route,
                    null,
                    false,
                    null);
        } finally {
            searchTrace.clear();
        }
    }

    /**
     * 로그인 유저의 가장 최근 대화를 복원한다(이어보기/이어가기).
     * GET /api/chatbot/conversations/recent  (인증 필요 — SecurityConfig permitAll 미포함)
     */
    @GetMapping("/chatbot/conversations/recent")
    public ApiResponse<ChatHistoryResponse> recentConversation(@AuthenticationPrincipal AuthUser authUser) {
        if (authUser == null) {
            return ApiResponse.ok(null); // 비로그인: 복원 없음(실제론 인증 필터에서 차단)
        }
        Long conversationId = memoryStore.findRecentConversation(authUser.id());
        if (conversationId == null) {
            return ApiResponse.ok(null); // 이전 대화 없음 → 빈 채팅으로 시작
        }
        List<ChatHistoryMessage> messages = memoryStore.getMessages(conversationId).stream()
                .map(this::toHistoryMessage)
                .filter(m -> m != null)
                .collect(Collectors.toList());
        return ApiResponse.ok(new ChatHistoryResponse(conversationId, messages));
    }

    /**
     * 세션 목록(사이드바): 로그인 유저의 인테이크(지원건) 세션 최대 5건(application_case_id 있는 것만, 최근순).
     * 잡담/FAQ 대화는 application_case_id NULL 이라 자연 제외된다.
     * GET /api/chatbot/conversations
     */
    @GetMapping("/chatbot/conversations")
    public ApiResponse<List<ChatSessionSummary>> listConversations(@AuthenticationPrincipal AuthUser authUser) {
        if (authUser == null) {
            return ApiResponse.ok(List.of());
        }
        List<ChatSessionSummary> sessions = memoryStore.listIntakeSessions(authUser.id()).stream()
                .map(r -> new ChatSessionSummary(
                        ((Number) r.get("conversationId")).longValue(),
                        (String) r.get("title"),
                        (String) r.get("mode"),
                        toEpochMillis(r.get("updatedAt"))))
                .collect(Collectors.toList());
        return ApiResponse.ok(sessions);
    }

    /**
     * MyBatis Map 의 DATETIME 값(드라이버/버전별 {@link java.sql.Timestamp} 또는 {@link java.time.LocalDateTime})을
     * epoch millis 로 정규화한다. LocalDateTime 은 저장이 Asia/Seoul 벽시계이므로 같은 zone 으로 해석해
     * 프런트 {@code Date.now()}(UTC epoch)와의 상대시각 차이가 정확하다.
     */
    private static Long toEpochMillis(Object v) {
        if (v instanceof java.sql.Timestamp ts) {
            return ts.getTime();
        }
        if (v instanceof java.time.LocalDateTime ldt) {
            return ldt.atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /**
     * 세션 클릭 시 그 대화의 메시지 로드(이어보기). 본인 대화만 접근 가능.
     * GET /api/chatbot/conversations/{conversationId}/messages
     */
    @GetMapping("/chatbot/conversations/{conversationId}/messages")
    public ApiResponse<ChatHistoryResponse> conversationMessages(@PathVariable Long conversationId,
                                                                 @AuthenticationPrincipal AuthUser authUser) {
        if (authUser == null) {
            return ApiResponse.error("UNAUTHORIZED", "로그인이 필요합니다.");
        }
        Long owner = memoryStore.findOwnerUserId(conversationId);
        if (owner == null || !owner.equals(authUser.id())) {
            return ApiResponse.error("FORBIDDEN", "접근할 수 없는 대화입니다.");
        }
        List<ChatHistoryMessage> messages = memoryStore.getMessages(conversationId).stream()
                .map(this::toHistoryMessage)
                .filter(m -> m != null)
                .collect(Collectors.toList());
        return ApiResponse.ok(new ChatHistoryResponse(conversationId, messages));
    }

    /**
     * LangChain4j 메모리 메시지 → UI 표시용 {role,text}.
     * user/assistant 텍스트만 노출하고 System·툴호출·툴결과 메시지는 버린다(UI 비표시).
     */
    private ChatHistoryMessage toHistoryMessage(dev.langchain4j.data.message.ChatMessage m) {
        if (m instanceof UserMessage u) {
            String t = u.singleText();
            return (t == null || t.isBlank()) ? null : new ChatHistoryMessage("user", t);
        }
        if (m instanceof AiMessage a) {
            String t = a.text();
            if (t == null || t.isBlank()) {
                return null; // 툴 호출만 있는 Ai 메시지(최종 답변 아님) → 스킵
            }
            return new ChatHistoryMessage("bot", MessageSanitizer.stripMarkdown(t));
        }
        return null;
    }

    /**
     * 임베딩 게이트를 통과한 FAQ 매칭으로 즉답을 구성한다(에이전트·모델 우회 = 결정적 FAQ 경로).
     * 트리거만 임베딩 게이트로 바뀌고, 답변/링크 접지 포맷은 기존 FAQ fast-path 로직을 그대로 재사용한다.
     * hits 는 호출부가 게이트 판정에 쓴 것을 재사용 — 재검색(2회 임베딩) 없이 같은 임베딩 결과를 공유한다.
     * 질문과 직접 관련된 최상위 1건의 링크만 노출(연관 FAQ 링크 나열 금지). 이 턴은 대화 메모리에 기록하지 않는다.
     */
    private ChatAskResponse faqAnswerFrom(Long conversationId, String question, Long userId,
                                          List<FaqHit> hits, String route) {
        // 유사도 desc 정렬이므로 linkUrl 있는 첫 hit 1건만.
        List<SiteLink> links = hits.stream()
                .filter(h -> h.linkUrl() != null && !h.linkUrl().isBlank())
                .limit(1)
                .map(h -> new SiteLink(
                        h.linkLabel() != null && !h.linkLabel().isBlank() ? h.linkLabel() : h.question(),
                        h.linkUrl()))
                .collect(Collectors.toList());
        // best-effort 적재(답함=자동 해결). 영속 response_path 는 대시보드 연속성 위해 기존 FAQ_FAST 라벨 유지.
        // 유사도 = 게이트가 본 원문 top-1(같은 임베딩). 전환 없음.
        responseLogService.record(conversationId, userId, question,
                "FAQ_FAST", true, hits.get(0).score(), null, false);
        return new ChatAskResponse(conversationId, hits.get(0).answer(), links, List.of(), route, null, false, null);
    }

    /**
     * 응답 링크 = 이번 턴에 툴이 실제로 돌려준 출처(SearchTrace)에서만 생성.
     */
    private List<SiteLink> collectLinks() {
        List<SiteLink> links = searchTrace.snapshot().stream()
                .filter(h -> h != null && h.url() != null && LINK_WHITELIST.matcher(h.url()).matches())
                .map(h -> new SiteLink(h.title(), h.url()))
                .collect(Collectors.toList());
        searchTrace.faqLinks().stream()
                .filter(l -> l.url() != null && (l.url().startsWith("/") || l.url().startsWith("http")))
                .forEach(links::add);
        return links;
    }

    /**
     * 글(커뮤니티 후기/게시글)을 가리키는 칩을 식별하는 결정적 패턴.
     * qwen3 가 시스템 프롬프트 예시("이 글 요약해줘")를 맥락 무관하게 베끼는 것을 게이트로 차단하기 위함.
     * 이번 턴에 글이 실제로 제시되지 않았으면 이 패턴에 걸리는 칩은 버린다.
     */
    private static final Pattern POST_CHIP = Pattern.compile("글|후기|게시|본문");

    /** "요약" free chip 은 이제 summaryChip 이 소유 → postsPresented 와 무관하게 항상 제거. */
    private static final Pattern SUMMARY_CHIP = Pattern.compile("요약");

    /** 묶음 요약 칩 최대 글 수(top-k). */
    private static final int SUMMARY_TOP_K = 3;

    /**
     * 이번 턴 검색 스냅샷으로 묶음 요약 칩을 만든다.
     * 글이 2개 이상일 때만 앞에서부터 최대 3개 postId 를 담아 칩을 만들고, 1개 이하면 null.
     */
    private ChatAskResponse.SummaryChip buildSummaryChip(List<PostHit> snapshot) {
        if (snapshot == null || snapshot.size() < 2) {
            return null;
        }
        List<Long> postIds = snapshot.stream()
                .limit(SUMMARY_TOP_K)
                .map(PostHit::postId)
                .collect(Collectors.toList());
        String label = "추천 후기 " + postIds.size() + "개 요약";
        return new ChatAskResponse.SummaryChip(label, postIds);
    }

    /**
     * quickReplies 2차 호출. 실패는 보조 기능이므로 삼켜서 빈 리스트로(graceful degradation).
     * <p><b>글 칩 게이트:</b> 모델은 응답 맥락(글이 검색·제시됐는지)을 모른 채 칩을 자유 생성하므로,
     * 이번 턴에 글이 실제로 제시됐을 때({@code postsPresented})만 글 관련 칩을 허용하고 아니면 결정적으로 필터링한다.
     * 글과 무관한 칩(예: "다른 직무", "신입 위주로")은 그대로 유지한다.
     */
    private List<String> suggestQuickReplies(String question, String answer, boolean postsPresented) {
        try {
            String context = "사용자: " + question + "\n챗봇: " + answer;
            List<String> chips = quickReplyAgent.suggest(context);
            if (chips == null) {
                return List.of();
            }
            // "요약" 칩은 summaryChip 이 소유 → postsPresented 무관하게 항상 제거.
            return chips.stream()
                    .filter(c -> c != null && !SUMMARY_CHIP.matcher(c).find())
                    // 그 외 글 관련 칩(후기/글/게시/본문)은 글 미제시 턴에서만 결정적으로 제거.
                    .filter(c -> postsPresented || !POST_CHIP.matcher(c).find())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("quickReplies 생성 실패(무시): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 추천했던 후기들을 묶어 핵심만 평문으로 압축 요약한다(summaryChip 클릭 → 이 엔드포인트).
     * POST /api/chatbot/summarize-posts  body: { conversationId?, postIds }
     *
     * <p>postIds 는 dedup 후 최대 3개만 사용한다. 비공개/삭제(getPostContent 가 "찾을 수 없"으로 시작)는 제외하고,
     * 남은 본문을 이어 요약 에이전트에 넘긴다. 응답은 묶음 요약 평문(summaryChip=null, links/quickReplies=[]).
     */
    @PostMapping("/chatbot/summarize-posts")
    public ApiResponse<ChatAskResponse> summarizePosts(@RequestBody ChatSummarizeRequest req) {
        if (req == null || req.postIds() == null || req.postIds().isEmpty()) {
            return ApiResponse.error("BAD_REQUEST", "요약할 글을 선택해 주세요.");
        }

        // dedup(순서 보존) 후 최대 3개.
        List<Long> ids = req.postIds().stream()
                .filter(id -> id != null)
                .distinct()
                .limit(SUMMARY_TOP_K)
                .collect(Collectors.toList());

        // 각 글 본문 수집 — 비공개/삭제("찾을 수 없"으로 시작)는 제외.
        List<String> bodies = ids.stream()
                .map(communityTools::getPostContent)
                .filter(c -> c != null && !c.startsWith("해당 글을 찾을 수 없"))
                .collect(Collectors.toList());

        String message;
        if (bodies.isEmpty()) {
            message = "추천했던 글을 더 이상 볼 수 없어요.";
        } else {
            try {
                String postsBlock = String.join("\n\n---\n\n", bodies);
                message = MessageSanitizer.stripMarkdown(summaryAgent.summarize(postsBlock));
            } catch (Exception e) {
                log.error("후기 묶음 요약 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
                message = "지금은 요약을 만들기 어려워요. 잠시 후 다시 시도해 주세요.";
            }
        }

        return ApiResponse.ok(new ChatAskResponse(
                req.conversationId(), message, List.of(), List.of(), "요약", null, false, null));
    }

    /**
     * 관리자: FAQ 일괄 임베딩
     * POST /api/admin/faq/embed-all
     */
    @PostMapping("/admin/faq/embed-all")
    public ApiResponse<Map<String, Object>> embedAll(
            @RequestParam(defaultValue = "false") boolean forceAll) {
        int count = chatbotService.embedAll(forceAll);
        return ApiResponse.ok(Map.of("embeddedCount", count));
    }
}

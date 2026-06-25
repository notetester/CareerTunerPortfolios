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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

import com.careertuner.ai.chat.ChatAskRequest;
import com.careertuner.ai.chat.ChatAskResponse;
import com.careertuner.ai.chat.ChatHistoryResponse;
import com.careertuner.ai.chat.ChatHistoryResponse.ChatHistoryMessage;
import com.careertuner.ai.chat.ChatResponse;
import com.careertuner.ai.chat.ChatResponse.SiteLink;
import com.careertuner.ai.chat.CommunityChatAgent;
import com.careertuner.ai.chat.FastPathService;
import com.careertuner.ai.chat.MessageSanitizer;
import com.careertuner.ai.chat.MyBatisChatMemoryStore;
import com.careertuner.ai.chat.QuickReplyAgent;
import com.careertuner.ai.chat.SearchTrace;
import com.careertuner.ai.intake.IntakeAskService;
import com.careertuner.ai.intake.dto.IntakeAskResponse;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.search.PostHit;

@RestController
@RequestMapping("/api")
public class ChatbotController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotController.class);

    /** 환각 링크 차단: 실제 커뮤니티 글 경로만 통과 (모델이 만든 임의 url 제거). */
    private static final Pattern LINK_WHITELIST = Pattern.compile("^/community/posts/\\d+$");

    /** 확인 응답에서 "시작" 쪽으로 본다(그 외는 안전하게 ① 로). */
    private static final List<String> AFFIRMATIVE = List.of(
            "시작", "네", "예", "응", "ㅇㅇ", "그래", "좋아", "할래", "해줘", "ok", "오케이", "yes");

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
                            IntakeAskService intakeAskService) {
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

        // Fast-path: 순수 내비 질의는 LLM·검색 우회 즉답 (서버 신뢰 링크라 화이트리스트 검증 생략).
        Optional<ChatResponse> fast = fastPathService.tryFastPath(question);
        if (fast.isPresent()) {
            ChatResponse fr = fast.get();
            responseLogService.record(conversationId, userId, question,
                    "NAV_FAST", false, null, null, false);
            return ApiResponse.ok(new ChatAskResponse(
                    conversationId, fr.message(), fr.links(), fr.quickReplies(), "NAV", null));
        }

        // 확인 대기(1턴) 소비: 이 턴은 라우팅을 돌리지 않는다. (오분류 안전판 A)
        if (routeConfirmStore.consumePending(conversationId)) {
            if (isAffirmative(question)) {
                log.info("//TODO[diag-unified-route] confirmPending=true 긍정 -> route=③(확인후)");
                return ApiResponse.ok(enterIntake(conversationId, question, userId, "③(확인후)"));
            }
            log.info("//TODO[diag-unified-route] confirmPending=true 부정 -> route=①(확인후)");
            return ApiResponse.ok(faqPath(conversationId, question, userId, "①(확인후)"));
        }

        // 통합 라우팅 판정.
        UnifiedChatRouter.Decision d = router.decide(question);
        switch (d.target()) {
            case INTAKE_DIRECT -> {
                logDiag(d, "③");
                return ApiResponse.ok(enterIntake(conversationId, question, userId, "③"));
            }
            case INTAKE_CONFIRM -> {
                routeConfirmStore.markPending(conversationId);
                logDiag(d, "확인반환");
                return ApiResponse.ok(new ChatAskResponse(
                        conversationId,
                        "면접 준비를 도와드릴까요?",
                        List.of(),
                        List.of("시작", "그냥 질문이에요"),
                        "확인반환",
                        null));
            }
            default -> {
                logDiag(d, "①");
                return ApiResponse.ok(faqPath(conversationId, question, userId, "①"));
            }
        }
    }

    /** //TODO[diag-unified-route] 요청당 진단 로그(라우팅 점수·구역·화행·경로). */
    private void logDiag(UnifiedChatRouter.Decision d, String route) {
        log.info("//TODO[diag-unified-route] faqScore={} intakeScore={} diff={} zone={} speechAct={} route={}",
                String.format("%.3f", d.faqScore()),
                String.format("%.3f", d.intakeScore()),
                String.format("%+.3f", d.diff()),
                d.boundary() ? "경계" : "명확",
                d.speechAct(),
                route);
    }

    private boolean isAffirmative(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        return AFFIRMATIVE.stream().anyMatch(norm::contains);
    }

    /** ③ 인테이크 입구로 데려다준다(한 턴). sticky 유지는 다음 PR — 여기서 모드 표시를 set 하지 않는다. */
    private ChatAskResponse enterIntake(Long conversationId, String question, Long userId, String route) {
        IntakeAskResponse r = intakeAskService.ask(userId, question, conversationId);
        return new ChatAskResponse(
                r.conversationId(),
                r.message(),
                List.of(),
                List.of(),
                route,
                new ChatAskResponse.IntakeStep(r.ready(), r.nextAsk(), r.autoPrepRequest()));
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

        searchTrace.clear();
        try {
            // 게이트 미달 → 에이전트(커뮤니티/복합). 원문 raw top-1 로 운영 FAQ공백 수집을 보존한다.
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
            List<String> quickReplies = suggestQuickReplies(question, message);

            // best-effort 적재(AGENT). FAQ 근거 여부 = 이번 턴 searchFaq 링크 존재(finally clear 전에 읽음).
            // 유사도 = 게이트 산출 원문 raw top-1(없으면 null). 전환 없음.
            responseLogService.record(conversationId, userId, question,
                    "AGENT", !searchTrace.faqLinks().isEmpty(),
                    miss != null ? miss.topSimilarity() : null, null, false);

            return new ChatAskResponse(conversationId, message, links, quickReplies, route, null);
        } catch (Exception e) {
            log.error("챗봇 에이전트 응답 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
            return new ChatAskResponse(
                    conversationId,
                    "지금은 답변을 생성하기 어렵습니다. 잠시 후 다시 시도해 주세요.",
                    List.of(),
                    List.of(),
                    route,
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
        return new ChatAskResponse(conversationId, hits.get(0).answer(), links, List.of(), route, null);
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

    /** quickReplies 2차 호출. 실패는 보조 기능이므로 삼켜서 빈 리스트로(graceful degradation). */
    private List<String> suggestQuickReplies(String question, String answer) {
        try {
            String context = "사용자: " + question + "\n챗봇: " + answer;
            List<String> chips = quickReplyAgent.suggest(context);
            return chips == null ? List.of() : chips;
        } catch (Exception e) {
            log.warn("quickReplies 생성 실패(무시): {}", e.getMessage());
            return List.of();
        }
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

package com.careertuner.support.chatbot;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import com.careertuner.ai.chat.ChatAskRequest;
import com.careertuner.ai.chat.ChatAskResponse;
import com.careertuner.ai.chat.ChatResponse;
import com.careertuner.ai.chat.ChatResponse.SiteLink;
import com.careertuner.ai.chat.CommunityChatAgent;
import com.careertuner.ai.chat.FastPathService;
import com.careertuner.ai.chat.MyBatisChatMemoryStore;
import com.careertuner.ai.chat.QuickReplyAgent;
import com.careertuner.ai.chat.SearchTrace;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.search.PostHit;

@RestController
@RequestMapping("/api")
public class ChatbotController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotController.class);

    /** 환각 링크 차단: 실제 커뮤니티 글 경로만 통과 (모델이 만든 임의 url 제거). */
    private static final Pattern LINK_WHITELIST = Pattern.compile("^/community/posts/\\d+$");

    private final CommunityChatAgent agent;
    private final QuickReplyAgent quickReplyAgent;
    private final SearchTrace searchTrace;
    private final MyBatisChatMemoryStore memoryStore;
    private final ChatbotService chatbotService;
    private final FastPathService fastPathService;

    public ChatbotController(CommunityChatAgent agent,
                            QuickReplyAgent quickReplyAgent,
                            SearchTrace searchTrace,
                            MyBatisChatMemoryStore memoryStore,
                            ChatbotService chatbotService,
                            FastPathService fastPathService) {
        this.agent = agent;
        this.quickReplyAgent = quickReplyAgent;
        this.searchTrace = searchTrace;
        this.memoryStore = memoryStore;
        this.chatbotService = chatbotService;
        this.fastPathService = fastPathService;
    }

    /**
     * 사용자 질문 → 커뮤니티 챗봇 에이전트(툴 호출).
     * POST /api/chatbot/ask  body: { question, conversationId? }
     */
    @PostMapping("/chatbot/ask")
    public ApiResponse<ChatAskResponse> ask(@RequestBody ChatAskRequest req) {
        if (req == null || req.question() == null || req.question().isBlank()) {
            return ApiResponse.error("BAD_REQUEST", "질문을 입력해 주세요.");
        }

        Long conversationId = req.conversationId() != null
                ? req.conversationId()
                : memoryStore.createConversation();

        // Fast-path: 순수 내비 질의는 LLM·검색 우회 즉답 (서버 신뢰 링크라 화이트리스트 검증 생략).
        Optional<ChatResponse> fast = fastPathService.tryFastPath(req.question());
        if (fast.isPresent()) {
            ChatResponse fr = fast.get();
            return ApiResponse.ok(new ChatAskResponse(
                    conversationId, fr.message(), fr.links(), fr.quickReplies()));
        }

        // FAQ fast-path: 명백한 FAQ 키워드는 모델 판단을 거치지 않고 코드가 직접 searchFaq 를 태운다.
        if (fastPathService.isFaqIntent(req.question())) {
            return ApiResponse.ok(faqFastPath(conversationId, req.question()));
        }

        searchTrace.clear();
        try {
            // 에이전트는 String 반환(툴 정상 동작). 메시지는 자유 생성.
            String message = agent.chat(conversationId, req.question());

            // links 접지: 모델 JSON 이 아니라 이번 턴에 툴이 실제로 돌려준 출처(커뮤니티 글 + FAQ 링크)에서만 생성.
            List<SiteLink> links = collectLinks();

            // quickReplies: 보조 기능 → 2차 호출이 실패해도 핵심(message+links)은 정상 반환.
            List<String> quickReplies = suggestQuickReplies(req.question(), message);

            return ApiResponse.ok(new ChatAskResponse(conversationId, message, links, quickReplies));
        } catch (Exception e) {
            log.error("챗봇 에이전트 응답 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
            return ApiResponse.ok(new ChatAskResponse(
                    conversationId,
                    "지금은 답변을 생성하기 어렵습니다. 잠시 후 다시 시도해 주세요.",
                    List.of(),
                    List.of()));
        } finally {
            searchTrace.clear();
        }
    }

    /**
     * FAQ fast-path: 모델·툴판단을 우회하고 코드가 직접 searchFaqHits 를 태워 응답을 구성한다.
     * 매칭 있으면 최상위 FAQ 답 + FAQ 링크(DB 출처라 신뢰), 없으면 정중히 1:1 문의 안내.
     * 이 턴은 대화 메모리에 기록하지 않는다(FAQ 답은 자기완결적).
     */
    private ChatAskResponse faqFastPath(Long conversationId, String question) {
        try {
            List<FaqHit> hits = chatbotService.searchFaqHits(question);
            if (!hits.isEmpty()) {
                List<SiteLink> links = hits.stream()
                        .filter(h -> h.linkUrl() != null && !h.linkUrl().isBlank())
                        .map(h -> new SiteLink(
                                h.linkLabel() != null && !h.linkLabel().isBlank() ? h.linkLabel() : h.question(),
                                h.linkUrl()))
                        .collect(Collectors.toList());
                return new ChatAskResponse(conversationId, hits.get(0).answer(), links, List.of());
            }
        } catch (Exception e) {
            log.error("FAQ fast-path 검색 실패: {}", e.getMessage());
        }
        // 결과 없음/실패 → 정중한 문의 안내
        return new ChatAskResponse(conversationId,
                "관련 안내를 찾지 못했어요. 1:1 문의를 남겨주시면 정확히 도와드릴게요.",
                List.of(new SiteLink("1:1 문의", "/support/contact")),
                List.of());
    }

    /**
     * 응답 링크 = 이번 턴에 툴이 실제로 돌려준 출처(SearchTrace)에서만 생성.
     * ① 커뮤니티 글: /community/posts/{id} 정규식 검증.
     * ② FAQ 링크: faq.link_url(DB 출처)이라 신뢰 — 내부(/) 또는 외부(http) url 만 통과.
     * 모델이 message 등에 만든 링크는 SearchTrace 에 없으므로 애초에 들어올 수 없다(환각 0).
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

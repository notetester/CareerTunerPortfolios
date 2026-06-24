package com.careertuner.admin.chatbot.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.UserMessage;

import com.careertuner.admin.chatbot.ai.FaqDraftAiClient;
import com.careertuner.admin.chatbot.dto.AdminUnansweredQuestionResponse;
import com.careertuner.admin.chatbot.dto.ChatbotConversationDrillResponse;
import com.careertuner.admin.chatbot.dto.ChatbotConversationDrillResponse.DrillTurn;
import com.careertuner.admin.chatbot.dto.FaqBrief;
import com.careertuner.admin.chatbot.dto.FaqDraftResponse;
import com.careertuner.admin.chatbot.dto.QuestionVariant;
import com.careertuner.admin.chatbot.dto.UnansweredMeta;
import com.careertuner.admin.chatbot.dto.UnansweredRow;
import com.careertuner.ai.chat.MessageSanitizer;
import com.careertuner.admin.chatbot.mapper.AdminUnansweredMapper;
import com.careertuner.admin.faq.dto.AdminFaqRequest;
import com.careertuner.admin.faq.dto.AdminFaqResponse;
import com.careertuner.admin.faq.service.AdminFaqService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.support.chatbot.ChatbotService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUnansweredServiceImpl implements AdminUnansweredService {

    private static final Logger log = LoggerFactory.getLogger(AdminUnansweredServiceImpl.class);

    /**
     * 공백 응답 폴백 문구(ChatbotController.faqFastPath 와 동일 문자열).
     * 미스 턴은 메모리에 없을 수 있어 화면엔 이 문구를 질문 원문과 함께 고정 노출한다.
     */
    private static final String FALLBACK_MESSAGE =
            "관련 안내를 찾지 못했어요. 1:1 문의를 남겨주시면 정확히 도와드릴게요.";

    /** 조회 가능한 상태값(전 상태). */
    private static final Set<String> QUERYABLE = Set.of("NEW", "REVIEWED", "CONVERTED", "DISMISSED");
    /** 운영자가 PATCH 로 옮길 수 있는 상태(전환 CONVERTED 는 2단계 전용 → 제외). */
    private static final Set<String> PATCHABLE = Set.of("REVIEWED", "DISMISSED");

    private final AdminUnansweredMapper mapper;
    private final QuestionClusterer clusterer;
    private final FaqDraftAiClient faqDraftAiClient;
    private final AdminFaqService adminFaqService;
    private final ChatbotService chatbotService;

    @Override
    public List<AdminUnansweredQuestionResponse> getUnanswered(AuthUser authUser, String status, int page, int size) {
        requireAdmin(authUser);
        String normStatus = (status == null || status.isBlank()) ? "NEW" : status.trim().toUpperCase();
        if (!QUERYABLE.contains(normStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회할 수 없는 상태입니다: " + status);
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);

        List<UnansweredRow> rows = mapper.findRowsByStatus(normStatus);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 1) 의미 군집화 → 군집별 집계.
        List<Cluster> clusters = clusterer.cluster(rows).stream()
                .map(this::aggregate)
                .filter(c -> c != null)
                .collect(Collectors.toList());

        // 2) best FAQ(질문·카테고리) 일괄 조회.
        List<Long> faqIds = clusters.stream()
                .map(c -> c.bestFaqId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, FaqBrief> faqMap = faqIds.isEmpty()
                ? Map.of()
                : mapper.findFaqBriefByIds(faqIds).stream()
                        .collect(Collectors.toMap(FaqBrief::getId, f -> f, (a, b) -> a));

        // 3) 정렬: 빈도 desc, 최신 desc.
        clusters.sort(Comparator
                .comparingLong((Cluster c) -> c.frequency).reversed()
                .thenComparing(c -> c.lastSeen, Comparator.nullsLast(Comparator.reverseOrder())));

        // 4) 페이지 슬라이스.
        int from = Math.min(safePage * safeSize, clusters.size());
        int to = Math.min(from + safeSize, clusters.size());

        return clusters.subList(from, to).stream()
                .map(c -> toResponse(c, normStatus, faqMap))
                .collect(Collectors.toList());
    }

    /** 군집 집계 중간형(내부). */
    private static final class Cluster {
        Long repId;
        String repQuestion;
        long frequency;
        Double topSimilarity;
        Long bestFaqId;
        java.time.LocalDateTime lastSeen;
        List<QuestionVariant> variants;
    }

    /** 군집 멤버들을 대표질문·빈도·최고유사도·변형목록으로 집계. 멤버는 최신순 가정. */
    private Cluster aggregate(List<UnansweredRow> members) {
        if (members == null || members.isEmpty()) {
            return null;
        }
        // norm 별 묶기(입력 최신순 → 각 norm 의 첫 멤버가 최신 = 대표 표현).
        Map<String, List<UnansweredRow>> byNorm = new LinkedHashMap<>();
        for (UnansweredRow r : members) {
            String key = r.getQuestionNorm() == null ? "" : r.getQuestionNorm();
            byNorm.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // 변형 목록: norm 별 [대표(최신)질문, 건수], 건수 desc.
        List<QuestionVariant> variants = byNorm.values().stream()
                .map(g -> new QuestionVariant(g.get(0).getQuestion(), g.size()))
                .sorted(Comparator.comparingLong(QuestionVariant::count).reversed())
                .collect(Collectors.toList());

        // 대표 norm = 건수 최다, 동률이면 최신(첫 멤버 createdAt) 우선.
        List<UnansweredRow> repGroup = byNorm.values().stream()
                .max(Comparator.<List<UnansweredRow>>comparingInt(List::size)
                        .thenComparing(g -> g.get(0).getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(members);
        UnansweredRow rep = repGroup.get(0);

        Cluster c = new Cluster();
        c.repId = rep.getId();
        c.repQuestion = rep.getQuestion();
        c.frequency = members.size();
        c.variants = variants;
        c.lastSeen = members.stream()
                .map(UnansweredRow::getCreatedAt)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        // 최고 유사도 멤버 → topSimilarity / bestFaqId.
        UnansweredRow bestMember = members.stream()
                .filter(r -> r.getTopSimilarity() != null)
                .max(Comparator.comparingDouble(UnansweredRow::getTopSimilarity))
                .orElse(null);
        if (bestMember != null) {
            c.topSimilarity = bestMember.getTopSimilarity();
            c.bestFaqId = bestMember.getBestFaqId();
        }
        return c;
    }

    private AdminUnansweredQuestionResponse toResponse(Cluster c, String status, Map<Long, FaqBrief> faqMap) {
        FaqBrief best = c.bestFaqId == null ? null : faqMap.get(c.bestFaqId);
        return AdminUnansweredQuestionResponse.builder()
                .id(c.repId)
                .category(best != null ? best.getCategory() : null)
                .question(c.repQuestion)
                .frequency(c.frequency)
                .topSimilarity(c.topSimilarity)
                .bestFaqQuestion(best != null ? best.getQuestion() : null)
                .status(status)
                .lastSeen(c.lastSeen)
                .variants(c.variants)
                .build();
    }

    @Override
    @Transactional
    public void updateStatus(AuthUser authUser, Long id, String status) {
        requireAdmin(authUser);
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대상 id 가 필요합니다.");
        }
        String normStatus = (status == null) ? "" : status.trim().toUpperCase();
        if (!PATCHABLE.contains(normStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경 가능한 상태는 REVIEWED/DISMISSED 입니다.");
        }
        List<Long> memberIds = clusterMemberIds(id);
        mapper.updateStatusByIds(memberIds, normStatus);
    }

    @Override
    public FaqDraftResponse generateDraft(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        Cluster cluster = aggregate(clusterOf(id));
        if (cluster == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대상 질문을 찾을 수 없습니다.");
        }
        String question = cluster.repQuestion;

        // 참고 FAQ: 톤 정렬용. Ollama 임베딩 장애 시 빈 문자열로 graceful degradation(초안 생성은 진행).
        String similarFaq = chatbotService.searchFaqContext(question);

        StringBuilder ctx = new StringBuilder();
        ctx.append("[사용자 질문]\n").append(question).append('\n');
        if (cluster.variants != null && cluster.variants.size() > 1) {
            // 같은 의미의 다른 표현들도 컨텍스트로 — 초안이 변형들을 아우르게.
            ctx.append("\n[같은 의미의 다른 질문들]\n");
            cluster.variants.forEach(v -> ctx.append("- ").append(v.question()).append('\n'));
        }
        if (!similarFaq.isBlank()) {
            ctx.append("\n[참고 FAQ]\n").append(similarFaq).append('\n');
        }

        String answer = faqDraftAiClient.generateDraft(ctx.toString());
        return new FaqDraftResponse(question, answer, cluster.frequency);
    }

    @Override
    @Transactional
    public AdminFaqResponse convert(AuthUser authUser, Long id, AdminFaqRequest request) {
        requireAdmin(authUser);
        List<Long> memberIds = clusterMemberIds(id);
        // 기존 FAQ 생성 흐름 재사용(새 INSERT 중복 구현 금지).
        AdminFaqResponse created = adminFaqService.createFaq(authUser, request);
        // 원 질문 군집을 CONVERTED 로 표시(이미 CONVERTED 인 행은 보존).
        mapper.updateStatusByIds(memberIds, "CONVERTED");
        return created;
    }

    @Override
    public ChatbotConversationDrillResponse getConversation(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대상 id 가 필요합니다.");
        }
        UnansweredMeta meta = mapper.findMetaById(id);
        if (meta == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대상 질문을 찾을 수 없습니다.");
        }
        List<DrillTurn> contextTurns = loadContextTurns(meta.getConversationId());
        return new ChatbotConversationDrillResponse(
                meta.getConversationId(), meta.getQuestion(), FALLBACK_MESSAGE, contextTurns);
    }

    /**
     * 발생 대화 메모리(messages_json)를 방어적으로 파싱해 표시용 턴 목록으로 변환.
     * ChatbotController.toHistoryMessage 미러: UserMessage→{user, 원문}, AiMessage(비공백)→{bot, 마크다운제거}.
     * conversationId 없음/대화 없음/'[]'/null/역직렬화 실패 어느 경우든 빈 리스트(화면은 안 깨짐).
     */
    private List<DrillTurn> loadContextTurns(Long conversationId) {
        if (conversationId == null) {
            return List.of();
        }
        String json = mapper.findConversationMessages(conversationId);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(json);
            if (messages == null || messages.isEmpty()) {
                return List.of();
            }
            return messages.stream()
                    .map(this::toDrillTurn)
                    .filter(t -> t != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // 깨진 JSON/스키마 변화 등 — 맥락 보조는 best-effort 라 삼키고 빈 리스트.
            log.debug("발생 대화 메모리 파싱 실패(맥락 생략): convId={}, {}", conversationId, e.getMessage());
            return List.of();
        }
    }

    /** 메모리 메시지 한 건 → 표시용 턴. user/assistant 텍스트만 노출, System·툴호출/결과는 스킵. */
    private DrillTurn toDrillTurn(ChatMessage m) {
        if (m instanceof UserMessage u) {
            String t = u.singleText();
            return (t == null || t.isBlank()) ? null : new DrillTurn("user", t);
        }
        if (m instanceof AiMessage a) {
            String t = a.text();
            if (t == null || t.isBlank()) {
                return null; // 툴 호출만 있는 Ai 메시지(최종 답변 아님) → 스킵
            }
            return new DrillTurn("bot", MessageSanitizer.stripMarkdown(t));
        }
        return null;
    }

    /** 대표 id 가 속한 군집의 멤버 id 목록. 없으면 NOT_FOUND. */
    private List<Long> clusterMemberIds(Long id) {
        List<Long> ids = clusterOf(id).stream().map(UnansweredRow::getId).collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대상 질문을 찾을 수 없습니다.");
        }
        return ids;
    }

    /**
     * 대표 id 가 속한 군집(멤버 행들)을 같은 상태 풀에서 재구성해 반환.
     * 목록과 동일한 군집 기준으로 상태변경·전환·초안이 동작하도록 보장.
     */
    private List<UnansweredRow> clusterOf(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대상 id 가 필요합니다.");
        }
        String status = mapper.findStatusById(id);
        if (status == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대상 질문을 찾을 수 없습니다.");
        }
        List<UnansweredRow> rows = mapper.findRowsByStatus(status);
        for (List<UnansweredRow> members : clusterer.cluster(rows)) {
            if (members.stream().anyMatch(r -> id.equals(r.getId()))) {
                return members;
            }
        }
        // 방어: 군집에 못 들어가면 단일 행으로.
        return rows.stream().filter(r -> id.equals(r.getId())).collect(Collectors.toList());
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}

package com.careertuner.admin.chatbot.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.chatbot.ai.FaqDraftAiClient;
import com.careertuner.admin.chatbot.dto.AdminUnansweredQuestionResponse;
import com.careertuner.admin.chatbot.dto.FaqDraftResponse;
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

    /** 조회 가능한 상태값(전 상태). */
    private static final Set<String> QUERYABLE = Set.of("NEW", "REVIEWED", "CONVERTED", "DISMISSED");
    /** 운영자가 PATCH 로 옮길 수 있는 상태(전환 CONVERTED 는 2단계 전용 → 제외). */
    private static final Set<String> PATCHABLE = Set.of("REVIEWED", "DISMISSED");

    private final AdminUnansweredMapper mapper;
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
        return mapper.findGrouped(normStatus, safeSize, safePage * safeSize);
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
        int changed = mapper.updateStatusByGroup(id, normStatus);
        if (changed == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대상 질문을 찾을 수 없습니다.");
        }
    }

    @Override
    public FaqDraftResponse generateDraft(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        String question = mapper.findQuestionById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대상 질문을 찾을 수 없습니다.");
        }
        long frequency = mapper.countByGroup(id);

        // 참고 FAQ: 톤 정렬용. Ollama 임베딩 장애 시 빈 문자열로 graceful degradation(초안 생성은 진행).
        String similarFaq = chatbotService.searchFaqContext(question);

        StringBuilder ctx = new StringBuilder();
        ctx.append("[사용자 질문]\n").append(question).append('\n');
        if (!similarFaq.isBlank()) {
            ctx.append("\n[참고 FAQ]\n").append(similarFaq).append('\n');
        }

        String answer = faqDraftAiClient.generateDraft(ctx.toString());
        return new FaqDraftResponse(question, answer, frequency);
    }

    @Override
    @Transactional
    public AdminFaqResponse convert(AuthUser authUser, Long id, AdminFaqRequest request) {
        requireAdmin(authUser);
        if (mapper.findQuestionById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대상 질문을 찾을 수 없습니다.");
        }
        // 기존 FAQ 생성 흐름 재사용(새 INSERT 중복 구현 금지).
        AdminFaqResponse created = adminFaqService.createFaq(authUser, request);
        // 원 질문 그룹을 CONVERTED 로 표시(이미 CONVERTED 인 행은 보존).
        mapper.updateStatusByGroup(id, "CONVERTED");
        return created;
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}

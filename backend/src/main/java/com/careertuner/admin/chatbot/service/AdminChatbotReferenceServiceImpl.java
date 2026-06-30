package com.careertuner.admin.chatbot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.chatbot.dto.ChatbotReferencePage;
import com.careertuner.admin.chatbot.dto.ChatbotReferenceResponse;
import com.careertuner.admin.chatbot.dto.ChatbotReferenceRow;
import com.careertuner.admin.chatbot.mapper.AdminChatbotResponseLogMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * 참조 대화 표(F3-A) 조회 구현. 읽기 전용 — chatbot_response_log(faq_referenced=1) 페이지 + faq join.
 * 기간 기본 규칙은 메트릭 밴드와 동일(미지정 시 to=오늘, from=to-6일).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminChatbotReferenceServiceImpl implements AdminChatbotReferenceService {

    /** 기간 미지정 시 기본 창(일). 메트릭과 동일. */
    private static final int DEFAULT_WINDOW_DAYS = 7;
    /** 페이지 크기 상한. */
    private static final int MAX_SIZE = 100;

    private final AdminChatbotResponseLogMapper responseLogMapper;

    @Override
    public ChatbotReferencePage getReferences(AuthUser authUser, LocalDate from, LocalDate to, int page, int size) {
        requireAdmin(authUser);
        LocalDate end = (to != null) ? to : LocalDate.now();
        LocalDate start = (from != null) ? from : end.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 시작일이 종료일보다 늦습니다.");
        }
        int safeSize = Math.max(1, Math.min(size, MAX_SIZE));
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;

        LocalDateTime fromTs = start.atStartOfDay();
        LocalDateTime toExclusive = end.plusDays(1).atStartOfDay();

        long total = responseLogMapper.countReferences(fromTs, toExclusive);
        List<ChatbotReferenceResponse> content = responseLogMapper
                .findReferences(fromTs, toExclusive, safeSize, offset).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new ChatbotReferencePage(content, total, safePage, safeSize);
    }

    private ChatbotReferenceResponse toResponse(ChatbotReferenceRow row) {
        String result = (row.getHandoff() != null && row.getHandoff() == 1) ? "상담 전환" : "해결";
        return new ChatbotReferenceResponse(
                row.getCreatedAt(), row.getQuestion(), row.getFaqQuestion(), row.getSimilarity(), result);
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !com.careertuner.admin.common.AdminAccess.isAdmin(authUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}

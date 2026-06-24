package com.careertuner.admin.chatbot.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.chatbot.dto.ChatbotMetricCard;
import com.careertuner.admin.chatbot.dto.ChatbotMetricPoint;
import com.careertuner.admin.chatbot.dto.ChatbotMetricsResponse;
import com.careertuner.admin.chatbot.dto.UnansweredRow;
import com.careertuner.admin.chatbot.mapper.AdminChatbotMetricsMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * 메트릭 밴드 집계(읽기 전용). S1 범위는 FAQ 공백 카드만 — 기존 chatbot_unanswered_question +
 * QuestionClusterer 만으로 동작(턴 응답 로그 불필요). 나머지 3카드는 null 로 둔다(가짜 숫자 금지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminChatbotMetricsServiceImpl implements AdminChatbotMetricsService {

    /** FAQ 공백 군집 집계 대상 상태(미해결 큐). */
    private static final String GAP_STATUS = "NEW";
    /** 기간 미지정 시 기본 창(일). */
    private static final int DEFAULT_WINDOW_DAYS = 7;

    private final AdminChatbotMetricsMapper metricsMapper;
    private final QuestionClusterer clusterer;

    @Override
    public ChatbotMetricsResponse getMetrics(AuthUser authUser, LocalDate from, LocalDate to) {
        requireAdmin(authUser);
        LocalDate end = (to != null) ? to : LocalDate.now();
        LocalDate start = (from != null) ? from : end.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 시작일이 종료일보다 늦습니다.");
        }

        ChatbotMetricCard faqGap = buildGapCard(start, end);

        // S1 범위: 자동해결률·FAQ참조수·전환율은 chatbot_response_log 집계(후속)가 선행이라 아직 null.
        // 프론트는 null 카드를 "수집 중"/"—"로 표시한다(디자인 목업 숫자 하드코딩 금지).
        return new ChatbotMetricsResponse(null, null, faqGap, null);
    }

    /** FAQ 공백 카드: 기간 내 NEW 군집 수 + 직전 동일길이 기간 대비 델타 + 일자 시계열. */
    private ChatbotMetricCard buildGapCard(LocalDate start, LocalDate end) {
        long windowDays = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(windowDays - 1);

        long current = countGapClusters(start, end);
        long previous = countGapClusters(prevStart, prevEnd);
        List<ChatbotMetricPoint> series = fillDailySeries(
                metricsMapper.countDailyDistinctNorm(GAP_STATUS, start.atStartOfDay(), end.plusDays(1).atStartOfDay()),
                start, end);
        return new ChatbotMetricCard((double) current, (double) (current - previous), series);
    }

    /** 기간 내 NEW 행을 의미 군집화(임베딩 NULL 이면 question_norm 폴백)해 군집 수 = "FAQ 공백 건수". */
    private long countGapClusters(LocalDate start, LocalDate end) {
        List<UnansweredRow> rows = metricsMapper.findRowsByStatusAndRange(
                GAP_STATUS, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        return rows.isEmpty() ? 0L : clusterer.cluster(rows).size();
    }

    /** 쿼리 결과(데이터 있는 날만)를 [start,end] 전 일자로 펴서 빈 날은 0 — 연속 스파크라인. */
    private List<ChatbotMetricPoint> fillDailySeries(List<ChatbotMetricPoint> raw, LocalDate start, LocalDate end) {
        Map<LocalDate, Long> byDate = raw.stream()
                .collect(Collectors.toMap(ChatbotMetricPoint::getDate, ChatbotMetricPoint::getCount, (a, b) -> a));
        List<ChatbotMetricPoint> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            out.add(new ChatbotMetricPoint(d, byDate.getOrDefault(d, 0L)));
        }
        return out;
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}

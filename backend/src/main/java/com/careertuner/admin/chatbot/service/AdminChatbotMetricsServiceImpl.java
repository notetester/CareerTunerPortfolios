package com.careertuner.admin.chatbot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.careertuner.admin.chatbot.dto.ResponseLogAggregate;
import com.careertuner.admin.chatbot.dto.ResponseLogDailyPoint;
import com.careertuner.admin.chatbot.dto.UnansweredRow;
import com.careertuner.admin.chatbot.mapper.AdminChatbotMetricsMapper;
import com.careertuner.admin.chatbot.mapper.AdminChatbotResponseLogMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * 메트릭 밴드 집계(읽기 전용). FAQ 공백 카드(faqGap)는 chatbot_unanswered_question +
 * QuestionClusterer 군집 수로(S1), 자동해결률·FAQ참조수·전환율은 chatbot_response_log 집계로 낸다(S2).
 * <p>response_log 가 비면(기간 내 턴 0) 세 카드는 통째로 null → 프론트는 "수집 중"/"—"로 표시(가짜 숫자 금지).
 * 헤드라인 value/delta 는 정확한 비율/건수이고, 스파크라인 series 는 일자별 "건수" 추이다(비율 아님).
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
    private final AdminChatbotResponseLogMapper responseLogMapper;
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

        // 자동해결률·FAQ참조수·전환율: chatbot_response_log 집계(S2). 기간은 [from, toExclusive) 반열린 구간.
        LocalDateTime curFrom = start.atStartOfDay();
        LocalDateTime curTo = end.plusDays(1).atStartOfDay();
        long windowDays = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(windowDays - 1);
        LocalDateTime prevFrom = prevStart.atStartOfDay();
        LocalDateTime prevTo = prevEnd.plusDays(1).atStartOfDay();

        ResponseLogAggregate cur = responseLogMapper.aggregate(curFrom, curTo);
        ResponseLogAggregate prev = responseLogMapper.aggregate(prevFrom, prevTo);
        List<ResponseLogDailyPoint> dailies = responseLogMapper.daily(curFrom, curTo);

        ChatbotMetricCard autoResolveRate = buildAutoResolveCard(cur, prev, dailies, start, end);
        ChatbotMetricCard faqReferenceCount = buildFaqReferenceCard(cur, prev, dailies, start, end);
        ChatbotMetricCard handoffRate = buildHandoffCard(cur, prev, dailies, start, end);

        return new ChatbotMetricsResponse(autoResolveRate, faqReferenceCount, faqGap, handoffRate);
    }

    /**
     * 자동 해결률 카드: value=answered/total(0~1), delta=직전 기간 비율 대비(직전 턴 없으면 null),
     * series=일자별 answered 건수. 기간 내 턴이 0이면 카드 통째 null(가짜 숫자 금지).
     */
    private ChatbotMetricCard buildAutoResolveCard(ResponseLogAggregate cur, ResponseLogAggregate prev,
                                                   List<ResponseLogDailyPoint> dailies, LocalDate start, LocalDate end) {
        if (cur.getTotal() == 0) {
            return null;
        }
        double value = (double) cur.getAnswered() / cur.getTotal();
        Double delta = (prev.getTotal() > 0)
                ? value - (double) prev.getAnswered() / prev.getTotal()
                : null;
        return new ChatbotMetricCard(value, delta,
                fillFromDaily(dailies, ResponseLogDailyPoint::getAnswered, start, end));
    }

    /**
     * FAQ 참조 응답 수 카드: value=answered(건수), delta=직전 기간 건수 대비(직전 턴 없으면 null),
     * series=일자별 answered 건수. 기간 내 턴이 0이면 카드 통째 null.
     */
    private ChatbotMetricCard buildFaqReferenceCard(ResponseLogAggregate cur, ResponseLogAggregate prev,
                                                    List<ResponseLogDailyPoint> dailies, LocalDate start, LocalDate end) {
        if (cur.getTotal() == 0) {
            return null;
        }
        double value = (double) cur.getAnswered();
        Double delta = (prev.getTotal() > 0)
                ? (double) (cur.getAnswered() - prev.getAnswered())
                : null;
        return new ChatbotMetricCard(value, delta,
                fillFromDaily(dailies, ResponseLogDailyPoint::getAnswered, start, end));
    }

    /**
     * 상담사 전환율 카드: value=handoffs/total(0~1), delta=직전 기간 비율 대비(직전 턴 없으면 null),
     * series=일자별 handoff 건수. 기간 내 턴이 0이면 카드 통째 null.
     */
    private ChatbotMetricCard buildHandoffCard(ResponseLogAggregate cur, ResponseLogAggregate prev,
                                               List<ResponseLogDailyPoint> dailies, LocalDate start, LocalDate end) {
        if (cur.getTotal() == 0) {
            return null;
        }
        double value = (double) cur.getHandoffs() / cur.getTotal();
        Double delta = (prev.getTotal() > 0)
                ? value - (double) prev.getHandoffs() / prev.getTotal()
                : null;
        return new ChatbotMetricCard(value, delta,
                fillFromDaily(dailies, ResponseLogDailyPoint::getHandoffs, start, end));
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

    /**
     * response_log 일자별 집계(데이터 있는 날만)에서 카드별 값(answered/handoffs)을 뽑아 [start,end] 전
     * 일자로 펴고 빈 날은 0 — 연속 스파크라인. count 의미는 "일자별 건수"(비율 아님 — 헤드라인만 비율).
     */
    private List<ChatbotMetricPoint> fillFromDaily(List<ResponseLogDailyPoint> raw,
                                                   java.util.function.ToLongFunction<ResponseLogDailyPoint> extractor,
                                                   LocalDate start, LocalDate end) {
        Map<LocalDate, Long> byDate = raw.stream()
                .collect(Collectors.toMap(ResponseLogDailyPoint::getDate, extractor::applyAsLong, (a, b) -> a));
        List<ChatbotMetricPoint> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            out.add(new ChatbotMetricPoint(d, byDate.getOrDefault(d, 0L)));
        }
        return out;
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
        if (authUser == null || !com.careertuner.admin.common.AdminAccess.isAdmin(authUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}

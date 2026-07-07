package com.careertuner.admin.pending.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.pending.dto.AdminPendingCountsResponse;
import com.careertuner.admin.pending.dto.AdminPendingCountsResponse.QueueBadge;
import com.careertuner.admin.pending.mapper.AdminPendingCountMapper;
import com.careertuner.community.mapper.CommunityPostMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminPendingCountServiceImpl implements AdminPendingCountService {

    /** 방치 판정 임계(일). 이 기간 넘게 미처리면 RED. */
    private static final int STALE_DAYS = 3;
    /** 고신뢰 toxic 판정 임계. 이 이상 신뢰도의 toxic 신고가 있으면 RED. */
    private static final double HIGH_CONF_TOXIC = 0.80;

    private final AdminPendingCountMapper pendingMapper;
    private final CommunityPostMapper communityPostMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminPendingCountsResponse getPendingCounts() {
        // 신고: 방치 3일↑ 또는 고신뢰 toxic 존재 시 RED
        int reportCount = pendingMapper.countPendingReports();
        boolean reportRed = reportCount > 0
                && (pendingMapper.existsStaleReport(STALE_DAYS)
                    || pendingMapper.existsHighConfidenceToxicReport(HIGH_CONF_TOXIC));

        // 자동숨김: hidden_at 부재 + 이미 선차단된 검토 큐 → never RED (YELLOW/NONE)
        // 게시글/댓글을 분리해 콘텐츠 관리 탭별 배지에 쓴다(사이드바는 프론트에서 합산).
        int hiddenPostCount = communityPostMapper.countAll(null, "HIDDEN", null, null); // 관리자 집계 — 뷰어 차단 필터 없음
        int hiddenCommentCount = pendingMapper.countHiddenComments();

        // 티켓: 방치 3일↑(24h SLA 초과) 시 RED
        int ticketCount = pendingMapper.countReceivedTickets();
        boolean ticketRed = ticketCount > 0 && pendingMapper.existsStaleReceivedTicket(STALE_DAYS);

        return new AdminPendingCountsResponse(
                QueueBadge.of(reportCount, reportRed),
                QueueBadge.of(hiddenPostCount, false),
                QueueBadge.of(hiddenCommentCount, false),
                QueueBadge.of(ticketCount, ticketRed));
    }
}

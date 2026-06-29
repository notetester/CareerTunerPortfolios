package com.careertuner.admin.notification.dto;

import java.util.List;

/**
 * 관리자 알림 모니터링 통계 — 전체 테이블 집계(목록 캡과 무관).
 * 모든 집계(총계·읽음률·카테고리별 읽음률·7일 추세)는 BE 에서 끝낸다. 프런트는 받은 숫자를 표시만 한다.
 */
public record AdminNotificationStatsResponse(
        long totalSent,
        long readCount,
        long unreadCount,
        int readRate,
        long todaySent,
        List<CategoryStat> categories,
        List<TrendPoint> trend
) {
    /** 카테고리별 발송/읽음/읽음률(전체). low=읽음률 50% 미만. category 키는 프런트 표시 카테고리와 동일. */
    public record CategoryStat(String category, long sent, long read, int rate, boolean low) {}

    /** 최근 7일 일자별 발송 수(과거→오늘 순, 빈 날 0 포함). */
    public record TrendPoint(String date, long count, boolean today) {}
}

package com.careertuner.admin.notification.service;

import java.util.Map;

/**
 * 관리자 알림 모니터링용 type → 표시 카테고리 매핑.
 *
 * <p>사용자 푸시 수신설정 분류({@code NotificationCategories}, 6종)와는 목적이 다르다.
 * 모니터링 화면은 시스템·운영 알림을 'admin' 버킷으로 따로 보여주므로 별도 매핑을 둔다.
 * 프런트의 {@code TYPE_CAT}(행 라벨용)과 같은 그룹핑을 유지한다 — 미매핑 type 은 'admin'.
 */
final class AdminNotificationCategories {

    private AdminNotificationCategories() {
    }

    private static final Map<String, String> TYPE_TO_CATEGORY = Map.ofEntries(
            Map.entry("PROFILE_ANALYZED", "ai"),
            Map.entry("JOB_ANALYSIS_COMPLETE", "ai"),
            Map.entry("COMPANY_ANALYSIS_COMPLETE", "ai"),
            Map.entry("FIT_ANALYSIS_COMPLETE", "ai"),
            Map.entry("CAREER_TREND_COMPLETE", "ai"),
            Map.entry("JOB_POSTING_EXTRACTION_SUCCEEDED", "ai"),
            Map.entry("JOB_POSTING_EXTRACTION_FAILED", "ai"),
            Map.entry("QUESTIONS_GENERATED", "interview"),
            Map.entry("INTERVIEW_REPORT_READY", "interview"),
            Map.entry("CORRECTION_COMPLETE", "correction"),
            Map.entry("COMMENT", "community"),
            Map.entry("COMMENT_REPLY", "community"),
            Map.entry("COMMENT_HIDDEN", "community"),
            Map.entry("COMMENT_RESTORED", "community"),
            Map.entry("COMMENT_REMOVED", "community"),
            Map.entry("LIKE", "community"),
            Map.entry("POST_HIDDEN", "community"),
            Map.entry("POST_REMOVED", "community"),
            Map.entry("POST_RESTORED", "community"),
            Map.entry("POST_SUMMARY_READY", "community"),
            Map.entry("CREDIT_LOW", "billing"),
            Map.entry("PAYMENT_COMPLETE", "billing"),
            Map.entry("PAYMENT_SCHEDULED", "billing"),
            Map.entry("CREDIT_RECHARGED", "billing"),
            Map.entry("NOTICE", "notice"),
            Map.entry("TICKET_ANSWERED", "notice"));

    /** 미매핑(시스템·운영) type 은 'admin' 버킷. */
    static String of(String type) {
        return TYPE_TO_CATEGORY.getOrDefault(type, "admin");
    }
}

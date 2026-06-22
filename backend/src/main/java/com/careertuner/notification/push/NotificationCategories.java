package com.careertuner.notification.push;

import java.util.List;
import java.util.Map;

/** 알림 type → 사용자 카테고리 매핑(프런트 notification.ts 의 TYPE_TO_CATEGORY 와 동일 기준). */
public final class NotificationCategories {

    /** 사용자가 on/off 할 수 있는 카테고리(관리자 전용 제외). */
    public static final List<String> USER_CATEGORIES =
            List.of("ai_analysis", "interview", "correction", "community", "billing", "notice");

    private static final Map<String, String> TYPE_TO_CATEGORY = Map.ofEntries(
            Map.entry("PROFILE_ANALYZED", "ai_analysis"),
            Map.entry("JOB_ANALYSIS_COMPLETE", "ai_analysis"),
            Map.entry("COMPANY_ANALYSIS_COMPLETE", "ai_analysis"),
            Map.entry("FIT_ANALYSIS_COMPLETE", "ai_analysis"),
            Map.entry("CAREER_TREND_COMPLETE", "ai_analysis"),
            Map.entry("JOB_POSTING_EXTRACTION_SUCCEEDED", "ai_analysis"),
            Map.entry("JOB_POSTING_EXTRACTION_FAILED", "ai_analysis"),
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

    private NotificationCategories() {
    }

    public static String of(String type) {
        return TYPE_TO_CATEGORY.getOrDefault(type, "notice");
    }
}

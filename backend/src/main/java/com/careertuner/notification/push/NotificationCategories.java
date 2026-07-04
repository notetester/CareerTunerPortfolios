package com.careertuner.notification.push;

import java.util.List;
import java.util.Map;

/** 알림 type → 사용자 카테고리 매핑(프런트 notification.ts 의 TYPE_TO_CATEGORY 와 동일 기준). */
public final class NotificationCategories {

    /** 사용자가 on/off 할 수 있는 카테고리(관리자 전용 제외). */
    public static final List<String> USER_CATEGORIES =
            List.of("ai_analysis", "interview", "correction", "community", "messenger",
                    "recommendation", "billing", "notice", "marketing");

    /** 사용자가 세부 채널을 조정할 수 있는 알림 type 목록. */
    public static final List<String> USER_RULE_TYPES = List.of(
            "PROFILE_ANALYZED",
            "JOB_ANALYSIS_COMPLETE",
            "COMPANY_ANALYSIS_COMPLETE",
            "FIT_ANALYSIS_COMPLETE",
            "CAREER_TREND_COMPLETE",
            "JOB_POSTING_EXTRACTION_SUCCEEDED",
            "JOB_POSTING_EXTRACTION_REVIEW_REQUIRED",
            "JOB_POSTING_EXTRACTION_FAILED",
            "QUESTIONS_GENERATED",
            "INTERVIEW_REPORT_READY",
            "CORRECTION_COMPLETE",
            "COMMENT",
            "COMMENT_REPLY",
            "COMMENT_HIDDEN",
            "COMMENT_RESTORED",
            "COMMENT_REMOVED",
            "LIKE",
            "POST_HIDDEN",
            "POST_REMOVED",
            "POST_RESTORED",
            "POST_SUMMARY_READY",
            "FRIEND_REQUEST",
            "FRIEND_ACCEPTED",
            "ROOM_INVITE",
            "ROOM_MESSAGE",
            "NOTE_MESSAGE",
            "ROOM_MENTION",
            "INTERVIEW_DISPATCH",
            "RECOMMENDED_JOB",
            "RECOMMENDED_POST",
            "CREDIT_LOW",
            "PAYMENT_COMPLETE",
            "PAYMENT_SCHEDULED",
            "SUBSCRIPTION_CANCELED",
            "CREDIT_RECHARGED",
            "REFUND_RESULT",
            "NOTICE",
            "TICKET_ANSWERED",
            "ACCOUNT_BLOCKED",
            "MARKETING_AD");

    /**
     * 발신자 관계(모르는 사람/친구/기업/운영자)별 세부 on/off 를 지원하는 알림 type.
     * 이 목록의 알림은 생성 시 sender_relation 이 기록되고, 설정의 rules[type].senders 로 걸러진다.
     */
    public static final List<String> RELATION_AWARE_TYPES = List.of(
            "COMMENT",
            "COMMENT_REPLY",
            "ROOM_MESSAGE",
            "NOTE_MESSAGE",
            "ROOM_MENTION",
            "ROOM_INVITE");

    private static final Map<String, String> TYPE_TO_CATEGORY = Map.ofEntries(
            Map.entry("PROFILE_ANALYZED", "ai_analysis"),
            Map.entry("JOB_ANALYSIS_COMPLETE", "ai_analysis"),
            Map.entry("COMPANY_ANALYSIS_COMPLETE", "ai_analysis"),
            Map.entry("FIT_ANALYSIS_COMPLETE", "ai_analysis"),
            Map.entry("CAREER_TREND_COMPLETE", "ai_analysis"),
            Map.entry("JOB_POSTING_EXTRACTION_SUCCEEDED", "ai_analysis"),
            Map.entry("JOB_POSTING_EXTRACTION_REVIEW_REQUIRED", "ai_analysis"),
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
            Map.entry("FRIEND_REQUEST", "messenger"),
            Map.entry("FRIEND_ACCEPTED", "messenger"),
            Map.entry("ROOM_INVITE", "messenger"),
            Map.entry("ROOM_MESSAGE", "messenger"),
            Map.entry("NOTE_MESSAGE", "messenger"),
            Map.entry("ROOM_MENTION", "messenger"),
            Map.entry("INTERVIEW_DISPATCH", "interview"),
            Map.entry("RECOMMENDED_JOB", "recommendation"),
            Map.entry("RECOMMENDED_POST", "recommendation"),
            Map.entry("CREDIT_LOW", "billing"),
            Map.entry("PAYMENT_COMPLETE", "billing"),
            Map.entry("PAYMENT_SCHEDULED", "billing"),
            Map.entry("SUBSCRIPTION_CANCELED", "billing"),
            Map.entry("CREDIT_RECHARGED", "billing"),
            Map.entry("REFUND_RESULT", "billing"),
            Map.entry("NOTICE", "notice"),
            Map.entry("TICKET_ANSWERED", "notice"),
            Map.entry("ACCOUNT_BLOCKED", "notice"),
            Map.entry("MARKETING_AD", "marketing"),
            // 관리자 전용(USER_CATEGORIES 에는 넣지 않아 사용자가 토글할 수 없다)
            Map.entry("NEW_REPORT", "admin"),
            Map.entry("NEW_TICKET", "admin"),
            Map.entry("NEW_USER", "admin"),
            Map.entry("LOW_CONFIDENCE_REPORT", "admin"),
            Map.entry("TICKET_DRAFT_READY", "admin"));

    private NotificationCategories() {
    }

    public static String of(String type) {
        return TYPE_TO_CATEGORY.getOrDefault(type, "notice");
    }
}

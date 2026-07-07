package com.careertuner.admin.notification.dto;

/**
 * 관리자 캠페인(공지/광고/추천) 전체 발송 요청.
 * type 은 NOTICE / MARKETING_AD / RECOMMENDED_JOB / RECOMMENDED_POST 만 허용한다.
 */
public record AdminCampaignRequest(
        String type,
        String title,
        String message,
        // 알림 클릭 시 이동할 경로(선택). 예: "/community/posts/1", "/billing"
        String link
) {}

package com.careertuner.admin.notification.dto;

/** 관리자 캠페인 발송 결과 — 발송한 type 과 대상 사용자 수. */
public record AdminCampaignResponse(
        String type,
        int recipients
) {}

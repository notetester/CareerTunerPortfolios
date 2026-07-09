package com.careertuner.admin.permission.dto;

/**
 * 관리자 알림 카테고리 수신 설정 변경 요청.
 *
 * @param type    관리자 알림 type (NEW_REPORT/NEW_TICKET/NEW_USER/NEW_COMPANY_APPLICATION/NEW_JOB_POSTING_REVIEW)
 * @param enabled true=수신, false=수신 안 함(opt-out)
 */
public record AdminNotificationOptOutUpdateRequest(String type, Boolean enabled) {
}

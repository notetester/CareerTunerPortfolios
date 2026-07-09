package com.careertuner.notification.constant;

/**
 * 관리자 대상 알림 type 상수.
 * <p>사용자 알림과 달리 role(ADMIN/SUPER_ADMIN) 대상으로 팬아웃된다.
 * 네이밍은 NOTIFICATION_SYSTEM.md 확정 값을 따른다.
 */
public final class AdminNotificationType {

    /** 새 신고 접수 (즉시). */
    public static final String NEW_REPORT = "NEW_REPORT";
    /** 새 문의(티켓) 접수 (몰아보기). */
    public static final String NEW_TICKET = "NEW_TICKET";
    /** 새 회원가입 (몰아보기) — 상수만 정의, 발행 훅은 후속 단계. */
    public static final String NEW_USER = "NEW_USER";

    private AdminNotificationType() {
    }
}

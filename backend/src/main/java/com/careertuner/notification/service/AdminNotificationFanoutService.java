package com.careertuner.notification.service;

/**
 * 관리자 대상 알림 팬아웃.
 * <p>단일 이벤트를 활성 관리자 전원에게 개별 알림(row)으로 복제 발송한다.
 * 발송 자체는 {@link NotificationService#notify}를 재사용한다(코어 신설 금지).
 * <p>★ 반드시 원본 트랜잭션 커밋 후(AFTER_COMMIT 리스너)에서 호출한다.
 * 신고/티켓 저장 트랜잭션 안에서 부르면 락 점유 중 별도 발송이 되어 self-deadlock 위험.
 * <p>이름에 Fanout 을 붙인 이유: {@code admin.notification.service.AdminNotificationService}
 * (관리자 알림 모니터링 조회용)와 역할·빈 이름이 충돌하지 않도록 구분한다.
 */
public interface AdminNotificationFanoutService {

    /**
     * 활성 관리자 전원에게 동일 알림을 팬아웃한다.
     *
     * @param type       알림 type (AdminNotificationType 상수)
     * @param targetType 딥링크 대상 유형 (POST/TICKET 등)
     * @param targetId   딥링크 대상 id
     * @param title      알림 제목
     * @param message    알림 본문
     * @param link       클릭 시 이동할 관리자 경로
     */
    void fanout(String type, String targetType, Long targetId,
                String title, String message, String link);
}

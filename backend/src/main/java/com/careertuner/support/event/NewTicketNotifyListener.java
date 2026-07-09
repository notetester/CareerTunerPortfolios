package com.careertuner.support.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.notification.constant.AdminNotificationType;
import com.careertuner.notification.service.AdminNotificationFanoutService;

/**
 * 문의 접수 트랜잭션 커밋 후, 비동기로 관리자에게 새 문의 알림을 팬아웃하는 리스너.
 * <p>NEW_TICKET 은 몰아보기 알림 — 프론트에서 토스트 없이 뱃지 카운트만 증가한다
 * (백엔드 발송/Web Push 는 사용자 알림과 동일하게 처리).
 */
@Component
public class NewTicketNotifyListener {

    private static final Logger log = LoggerFactory.getLogger(NewTicketNotifyListener.class);

    private final AdminNotificationFanoutService adminNotificationService;

    public NewTicketNotifyListener(AdminNotificationFanoutService adminNotificationService) {
        this.adminNotificationService = adminNotificationService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NewTicketEvent event) {
        try {
            String subject = event.subject() == null ? "" : event.subject();
            adminNotificationService.fanout(
                    AdminNotificationType.NEW_TICKET,
                    "TICKET",
                    event.ticketId(),
                    "새 문의 접수",
                    subject.isBlank() ? "새 고객센터 문의가 접수되었습니다." : subject,
                    "/admin/inquiries");
        } catch (Exception ex) {
            log.error("새 문의 관리자 알림 실패 ticketId={}", event.ticketId(), ex);
        }
    }
}

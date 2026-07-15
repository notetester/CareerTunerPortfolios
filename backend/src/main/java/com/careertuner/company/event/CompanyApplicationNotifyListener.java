package com.careertuner.company.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.AdminNotificationFanoutService;
import com.careertuner.notification.service.NotificationService;

/**
 * 기업 신청 알림 리스너 — NewReportNotifyListener 패턴(AFTER_COMMIT + @Async + best-effort).
 * <ul>
 *   <li>접수 → 활성 관리자 전원에게 NEW_COMPANY_APPLICATION 팬아웃</li>
 *   <li>승인/반려 → 신청자에게 COMPANY_APPLY_RESULT 알림(개인 설정 필터는 notify 가 처리)</li>
 * </ul>
 * 알림 실패는 신청/승인 흐름을 깨지 않는다(로그만 남긴다).
 */
@Component
public class CompanyApplicationNotifyListener {

    private static final Logger log = LoggerFactory.getLogger(CompanyApplicationNotifyListener.class);

    private final AdminNotificationFanoutService adminNotificationFanoutService;
    private final NotificationService notificationService;

    public CompanyApplicationNotifyListener(AdminNotificationFanoutService adminNotificationFanoutService,
                                            NotificationService notificationService) {
        this.adminNotificationFanoutService = adminNotificationFanoutService;
        this.notificationService = notificationService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(CompanyApplicationSubmittedEvent event) {
        try {
            adminNotificationFanoutService.fanout(
                    "NEW_COMPANY_APPLICATION",
                    "COMPANY_APPLICATION",
                    event.applicationId(),
                    "새 기업 계정 신청",
                    event.companyName() + " 기업 계정 전환 신청이 접수되었습니다.",
                    "/admin/company/applications");
        } catch (Exception ex) {
            log.error("기업 신청 관리자 알림 실패 applicationId={}", event.applicationId(), ex);
        }
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewed(CompanyApplicationReviewedEvent event) {
        try {
            String title = event.approved() ? "기업 계정 신청이 승인되었습니다" : "기업 계정 신청이 반려되었습니다";
            String message = event.approved()
                    ? event.companyName() + " 기업 계정이 활성화되었습니다. 다시 로그인하면 기업 기능이 적용됩니다."
                    : "반려 사유: " + event.rejectReason();
            notificationService.notify(Notification.builder()
                    .userId(event.applicantUserId())
                    .type("COMPANY_APPLY_RESULT")
                    .targetType("COMPANY_APPLICATION")
                    .targetId(event.applicationId())
                    .title(title)
                    .message(message)
                    .link("/company/manage")
                    .build());
        } catch (Exception ex) {
            log.error("기업 신청 결과 알림 실패 applicationId={}", event.applicationId(), ex);
        }
    }
}

package com.careertuner.companyjobposting.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.companyjobposting.service.JobPostingRecommendationService;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.AdminNotificationFanoutService;
import com.careertuner.notification.service.NotificationService;

/**
 * 공고 알림 리스너 — NewReportNotifyListener 패턴(AFTER_COMMIT + @Async + best-effort).
 * <ul>
 *   <li>검토 제출 → 관리자 NEW_JOB_POSTING_REVIEW 팬아웃</li>
 *   <li>검토 확정 → 기업에 JOB_POSTING_REVIEW_RESULT 알림</li>
 *   <li>게시 확정 → RECOMMENDED_JOB 자동 발행(상한 100명)</li>
 * </ul>
 */
@Component
public class JobPostingNotifyListener {

    private static final Logger log = LoggerFactory.getLogger(JobPostingNotifyListener.class);

    private final AdminNotificationFanoutService adminNotificationFanoutService;
    private final NotificationService notificationService;
    private final JobPostingRecommendationService recommendationService;

    public JobPostingNotifyListener(AdminNotificationFanoutService adminNotificationFanoutService,
                                    NotificationService notificationService,
                                    JobPostingRecommendationService recommendationService) {
        this.adminNotificationFanoutService = adminNotificationFanoutService;
        this.notificationService = notificationService;
        this.recommendationService = recommendationService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(JobPostingSubmittedEvent event) {
        try {
            boolean revision = event.revisionId() != null;
            adminNotificationFanoutService.fanout(
                    "NEW_JOB_POSTING_REVIEW",
                    "JOB_POSTING",
                    event.postingId(),
                    revision ? "공고 수정 검토 요청" : "새 공고 검토 요청",
                    "'" + event.title() + "' 공고가 검토 대기 중입니다.",
                    "/admin/company/job-postings");
        } catch (Exception ex) {
            log.error("공고 검토 관리자 알림 실패 postingId={}", event.postingId(), ex);
        }
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewed(JobPostingReviewedEvent event) {
        try {
            String what = event.revisionReview() ? "공고 수정" : "공고";
            String title = event.approved()
                    ? what + " 검토가 승인되었습니다"
                    : what + " 검토가 반려되었습니다";
            String message = event.approved()
                    ? "'" + event.title() + "' " + (event.revisionReview() ? "변경 사항이 반영되었습니다." : "공고가 게시되었습니다.")
                    : "'" + event.title() + "' 반려 사유: " + event.rejectReason();
            notificationService.notify(Notification.builder()
                    .userId(event.companyUserId())
                    .type("JOB_POSTING_REVIEW_RESULT")
                    .targetType("JOB_POSTING")
                    .targetId(event.postingId())
                    .title(title)
                    .message(message)
                    .link("/company/manage")
                    .build());
        } catch (Exception ex) {
            log.error("공고 검토 결과 알림 실패 postingId={}", event.postingId(), ex);
        }
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublished(JobPostingPublishedEvent event) {
        try {
            recommendationService.recommendToMatchedUsers(event.postingId());
        } catch (Exception ex) {
            log.error("추천 공고 알림 발행 실패 postingId={}", event.postingId(), ex);
        }
    }
}

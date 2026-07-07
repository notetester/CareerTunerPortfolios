package com.careertuner.community.moderation.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.community.moderation.domain.AiResultStatus;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.PostAiResult;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.notification.constant.AdminNotificationType;
import com.careertuner.notification.service.AdminNotificationFanoutService;

import tools.jackson.databind.ObjectMapper;

/**
 * 게시글 신고 트랜잭션 커밋 후, 비동기로 관리자에게 새 신고 알림을 팬아웃하는 리스너.
 * <p>{@code ReportClassifyListener} 패턴을 그대로 복제한다(AFTER_COMMIT + @Async).
 * 신고 저장 트랜잭션과 알림 발송을 완전히 분리해 self-deadlock 을 피한다.
 */
@Component
public class NewReportNotifyListener {

    private static final Logger log = LoggerFactory.getLogger(NewReportNotifyListener.class);

    private final AdminNotificationFanoutService adminNotificationService;
    private final PostAiResultMapper aiResultMapper;
    private final ObjectMapper objectMapper;

    public NewReportNotifyListener(AdminNotificationFanoutService adminNotificationService,
                                   PostAiResultMapper aiResultMapper,
                                   ObjectMapper objectMapper) {
        this.adminNotificationService = adminNotificationService;
        this.aiResultMapper = aiResultMapper;
        this.objectMapper = objectMapper;
    }

    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NewReportEvent event) {
        try {
            adminNotificationService.fanout(
                    AdminNotificationType.NEW_REPORT,
                    "POST",
                    event.postId(),
                    "새 신고 접수",
                    buildMessage(event.postId()),
                    "/admin/community");
        } catch (Exception ex) {
            log.error("새 신고 관리자 알림 실패 postId={}", event.postId(), ex);
        }
    }

    /**
     * AI 분류(post_ai_result, task_type=REPORT)가 이미 완료돼 있으면 신뢰도를 덧붙인다.
     * <p>분류 리스너도 AFTER_COMMIT 비동기라 이 시점엔 아직 PENDING 일 수 있다 → best-effort.
     */
    private String buildMessage(Long postId) {
        try {
            PostAiResult ar = aiResultMapper.findByPostIdAndTaskType(postId, AiTaskType.REPORT);
            if (ar != null && ar.getStatus() == AiResultStatus.COMPLETED
                    && ar.getResultJson() != null) {
                ModerationResult r = objectMapper.readValue(ar.getResultJson(), ModerationResult.class);
                if (r.confidence() != null) { // confidence 는 nullable — 누락이면 기본 메시지
                    return "게시글 신고가 접수되었습니다. (AI 판정 신뢰도 "
                            + Math.round(r.confidence() * 100) + "%)";
                }
            }
        } catch (Exception ignore) {
            // 신뢰도 부가정보 실패는 무시하고 기본 메시지 사용
        }
        return "게시글 신고가 접수되었습니다.";
    }
}

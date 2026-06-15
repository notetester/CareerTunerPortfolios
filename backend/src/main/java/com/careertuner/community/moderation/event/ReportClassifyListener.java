package com.careertuner.community.moderation.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.community.moderation.service.PostModerationService;

/**
 * 게시글 신고 트랜잭션 커밋 후, 비동기로 AI 분류를 실행하는 리스너.
 */
@Component
public class ReportClassifyListener {

    private static final Logger log = LoggerFactory.getLogger(ReportClassifyListener.class);

    private final PostModerationService moderationService;

    public ReportClassifyListener(PostModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReportClassifyRequiredEvent event) {
        try {
            moderationService.classify(event.postId());
        } catch (Exception ex) {
            log.error("신고 분류 리스너 실패 postId={}", event.postId(), ex);
        }
    }
}

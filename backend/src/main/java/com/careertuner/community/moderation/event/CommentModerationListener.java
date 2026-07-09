package com.careertuner.community.moderation.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.community.moderation.service.PostModerationService;

/**
 * 댓글 저장 트랜잭션 커밋 후, 비동기로 검열을 실행하는 리스너.
 * PostModerationListener 복제 — 대상만 댓글.
 *
 * @Async("moderationExecutor"): 게시글 검열과 동일 풀 공유
 * @TransactionalEventListener(AFTER_COMMIT): 롤백된 댓글은 검열하지 않음
 */
@Component
public class CommentModerationListener {

    private static final Logger log = LoggerFactory.getLogger(CommentModerationListener.class);

    private final PostModerationService moderationService;

    public CommentModerationListener(PostModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CommentModerationRequiredEvent event) {
        try {
            moderationService.moderateComment(event.commentId());
        } catch (Exception ex) {
            log.error("댓글 검열 리스너 실패 commentId={}", event.commentId(), ex);
        }
    }
}

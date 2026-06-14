package com.careertuner.community.moderation.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.community.moderation.service.PostModerationService;

/**
 * 게시글 저장 트랜잭션 커밋 후, 비동기로 검열을 실행하는 리스너.
 *
 * @Async("moderationExecutor"): ModerationAsyncConfig의 전용 스레드 풀에서 실행
 * @TransactionalEventListener(AFTER_COMMIT): 트랜잭션 커밋 후에만 실행
 *   → 롤백된 글은 검열하지 않음
 */
@Component
public class PostModerationListener {

    private static final Logger log = LoggerFactory.getLogger(PostModerationListener.class);

    private final PostModerationService moderationService;

    public PostModerationListener(PostModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostModerationRequiredEvent event) {
        try {
            moderationService.moderate(event.postId());
        } catch (Exception ex) {
            log.error("검열 리스너 실패 postId={}", event.postId(), ex);
        }
    }
}

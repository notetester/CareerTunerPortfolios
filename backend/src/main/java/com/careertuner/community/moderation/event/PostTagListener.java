package com.careertuner.community.moderation.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.community.moderation.service.PostModerationService;

/**
 * 게시글 작성/수정 트랜잭션 커밋 후, 비동기로 AI 태깅을 실행하는 리스너.
 */
@Component
public class PostTagListener {

    private static final Logger log = LoggerFactory.getLogger(PostTagListener.class);

    private final PostModerationService moderationService;

    public PostTagListener(PostModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostTagRequiredEvent event) {
        try {
            moderationService.tag(event.postId());
        } catch (Exception ex) {
            log.error("태깅 리스너 실패 postId={}", event.postId(), ex);
        }
    }
}

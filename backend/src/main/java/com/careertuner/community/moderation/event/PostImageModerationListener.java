package com.careertuner.community.moderation.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.community.moderation.service.PostImageModerationService;

/**
 * 게시글 저장 트랜잭션 커밋 후, 비동기로 본문 이미지 검열을 실행하는 리스너.
 * 텍스트 검열({@link PostModerationListener})과 동일한 실행 정책(전용 풀 + AFTER_COMMIT)을 따른다.
 */
@Component
public class PostImageModerationListener {

    private static final Logger log = LoggerFactory.getLogger(PostImageModerationListener.class);

    private final PostImageModerationService imageModerationService;

    public PostImageModerationListener(PostImageModerationService imageModerationService) {
        this.imageModerationService = imageModerationService;
    }

    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostImageModerationRequiredEvent event) {
        try {
            imageModerationService.moderate(event.postId());
        } catch (Exception ex) {
            log.error("이미지 검열 리스너 실패 postId={}", event.postId(), ex);
        }
    }
}

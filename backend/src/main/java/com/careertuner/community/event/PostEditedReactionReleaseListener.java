package com.careertuner.community.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.community.mapper.ReactionMapper;

/**
 * 게시글 수정 트랜잭션 커밋 후, reactionRetention 이 release 인 사용자의 해당 글 리액션을 삭제한다.
 * <p>{@code NewReportNotifyListener} 패턴(AFTER_COMMIT + @Async)을 따르며,
 * 수정 저장 트랜잭션과 완전히 분리해 수정 자체를 깨지 않는다(best-effort).
 * 삭제 후에는 카운트 캐시를 실제 행 수로 대사한다.
 */
@Component
public class PostEditedReactionReleaseListener {

    private static final Logger log = LoggerFactory.getLogger(PostEditedReactionReleaseListener.class);

    private final ReactionMapper reactionMapper;

    public PostEditedReactionReleaseListener(ReactionMapper reactionMapper) {
        this.reactionMapper = reactionMapper;
    }

    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PostEditedEvent event) {
        try {
            int released = reactionMapper.deleteReleasedPostReactions(event.postId());
            if (released > 0) {
                reactionMapper.reconcilePostReactionCounts(event.postId());
                log.info("게시글 수정 → release 리액션 해지 postId={} count={}", event.postId(), released);
            }
        } catch (Exception ex) {
            log.error("게시글 수정 리액션 해지 실패 postId={}", event.postId(), ex);
        }
    }
}

package com.careertuner.community.moderation.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.community.moderation.service.PostModerationService;

/**
 * 면접후기 작성/수정 트랜잭션 커밋 후, 비동기로 AI 질문 추출을 실행하는 리스너.
 */
@Component
public class InterviewExtractListener {

    private static final Logger log = LoggerFactory.getLogger(InterviewExtractListener.class);

    private final PostModerationService moderationService;

    public InterviewExtractListener(PostModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(InterviewExtractRequiredEvent event) {
        try {
            moderationService.extractInterviewQuestions(event.postId());
        } catch (Exception ex) {
            log.error("면접 질문 추출 리스너 실패 postId={}", event.postId(), ex);
        }
    }
}

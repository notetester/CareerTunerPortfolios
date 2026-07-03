package com.careertuner.community.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.community.service.PostRecommendationService;

/**
 * 글 생성 트랜잭션 커밋 후, 비동기로 관심 사용자에게 추천 알림을 발행하는 리스너.
 * <p>{@code NewReportNotifyListener} 패턴을 그대로 복제한다(AFTER_COMMIT + @Async).
 * 글 저장 트랜잭션과 추천 팬아웃을 완전히 분리해 글 작성 API 응답 시간에 영향을 주지 않고,
 * 추천 엔진의 어떤 실패도 글 작성 흐름을 깨지 않는다(로그만 남기고 조용히 종료).
 */
@Component
public class RecommendedPostNotifyListener {

    private static final Logger log = LoggerFactory.getLogger(RecommendedPostNotifyListener.class);

    private final PostRecommendationService postRecommendationService;

    public RecommendedPostNotifyListener(PostRecommendationService postRecommendationService) {
        this.postRecommendationService = postRecommendationService;
    }

    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostPublishedEvent event) {
        try {
            postRecommendationService.recommendToMatchedUsers(event.postId());
        } catch (Exception ex) {
            log.error("추천 알림 발행 실패 postId={}", event.postId(), ex);
        }
    }
}

package com.careertuner.community.service;

public interface CommunitySubscriptionService {

    /** 글 구독 토글 — 구독 중이면 해지. 작성자 아닌 사람도 구독 가능. 반환값 = 토글 후 구독 여부. */
    boolean togglePostSubscription(Long postId, Long userId);

    /** 댓글 구독 토글 — 새 답글 시 COMMENT_WATCH_REPLY 알림. 반환값 = 토글 후 구독 여부. */
    boolean toggleCommentSubscription(Long commentId, Long userId);
}

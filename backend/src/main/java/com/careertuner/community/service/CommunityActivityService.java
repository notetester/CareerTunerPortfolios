package com.careertuner.community.service;

import com.careertuner.community.dto.ActivityPageResponse;

public interface CommunityActivityService {

    /** 내 활동 목록 — 익명 작성/익명 리액션 포함(본인 시점). tab: posts/comments/replies/likes/bookmarks/scraps */
    ActivityPageResponse getMyActivity(Long userId, String tab, int page, int size);

    /**
     * 타인 프로필 활동 목록 — PrivacyPolicyService.allows(대상, 뷰어, "activity.{tab}") 검사
     * (차단·관계별 공개범위 자동). 비공개 탭은 allowed=false + 빈 목록.
     * 익명 작성/익명 리액션 항목은 쿼리 단계에서 제외된다.
     */
    ActivityPageResponse getUserActivity(Long profileUserId, Long viewerId, String tab, int page, int size);

    /** 타인 프로필 활동 탭 헤더 — 탭별 공개 여부(잠금 표시용) + 표시명. */
    ActivityPageResponse.TabsDto getUserActivityTabs(Long profileUserId, Long viewerId);
}

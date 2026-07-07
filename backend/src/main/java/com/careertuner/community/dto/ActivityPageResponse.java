package com.careertuner.community.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.careertuner.community.domain.ActivityItem;

/** 활동 목록 페이지 — 내 활동/타인 프로필 활동 탭 공통 응답. */
public record ActivityPageResponse(
        String tab,
        /** 타인 프로필에서 이 탭이 공개인지(비공개 탭은 items 비움 + 잠금 표시). 내 활동은 항상 true. */
        boolean allowed,
        List<ItemDto> items,
        int total,
        int page,
        int size
) {

    public record ItemDto(
            String itemType,
            Long postId,
            Long commentId,
            Long scrapId,
            String title,
            String preview,
            String reactionType,
            boolean anonymous,
            LocalDateTime createdAt
    ) {
        public static ItemDto from(ActivityItem item) {
            return new ItemDto(
                    item.getItemType(),
                    item.getPostId(),
                    item.getCommentId(),
                    item.getScrapId(),
                    item.getTitle(),
                    item.getPreview(),
                    item.getReactionType(),
                    item.isAnonymous(),
                    item.getCreatedAt()
            );
        }
    }

    /** 타인 프로필 활동 탭 헤더 — 탭별 공개 여부(잠금 표시용). */
    public record TabsDto(Long userId, String name, Map<String, Boolean> tabs) {}
}

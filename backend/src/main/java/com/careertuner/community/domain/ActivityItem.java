package com.careertuner.community.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 활동 목록 항목 — 내 활동/타인 프로필 활동 탭의 공통 행.
 * itemType: POST / COMMENT / REPLY / SCRAP (리액션 탭에서는 대상 유형 POST/COMMENT).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityItem {

    private String itemType;
    private Long postId;
    private Long commentId;
    private Long scrapId;
    /** 대상 글 제목(스크랩은 스냅샷 제목). */
    private String title;
    /** 본문/댓글 미리보기. */
    private String preview;
    /** 리액션 탭에서의 리액션 종류(LIKE/BOOKMARK 등). 그 외 탭은 null. */
    private String reactionType;
    /** 익명 작성/익명 리액션 여부 — 타인 시점 목록에서는 쿼리 단계에서 이미 제외된다. */
    private boolean anonymous;
    private LocalDateTime createdAt;
}

package com.careertuner.community.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentReaction {

    private Long id;
    private Long userId;
    private Long commentId;
    private String reactionType;
    /** 리액션 축(RECOMMEND_AXIS/PREFERENCE) — UNIQUE (user, comment, axis). */
    private String axis;
    /** 익명 리액션 — 타인 시점 목록에서 제외, 본인 시점 표시, 집계 포함. */
    private boolean anonymous;
    private LocalDateTime createdAt;

    // JOIN으로 가져오는 대상 글 id (뷰어 상태 벌크 조회용)
    private Long postId;
}

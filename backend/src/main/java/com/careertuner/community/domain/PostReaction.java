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
public class PostReaction {

    private Long id;
    private Long userId;
    private Long postId;
    private String reactionType;
    /** 리액션 축(RECOMMEND_AXIS/PREFERENCE/BOOKMARK) — UNIQUE (user, post, axis). */
    private String axis;
    /** 익명 리액션 — 타인 시점 목록에서 제외, 본인 시점 표시, 집계 포함. */
    private boolean anonymous;
    private LocalDateTime createdAt;

    // JOIN으로 가져오는 반응자 이름 (반응자 목록용)
    private String userName;
}

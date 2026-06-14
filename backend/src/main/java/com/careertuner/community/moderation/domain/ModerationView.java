package com.careertuner.community.moderation.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 검열 목록/상세 조회용 JOIN 결과 매핑.
 * post_ai_result + community_post + users JOIN.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationView {

    private Long postId;
    private String title;
    private String content;
    private String authorName;
    private boolean anonymous;
    private Long authorId;
    private String postCategory;
    private String postStatus;

    // post_ai_result 필드
    private String resultJson;
    private String model;
    private int attemptCount;
    private String aiStatus;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}

package com.careertuner.community.moderation.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationReviewQueueView {

    private Long postId;
    private String title;
    private String content;
    private String authorName;
    private boolean anonymous;
    private String postCategory;
    private String aiCategory;
    private double confidence;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}

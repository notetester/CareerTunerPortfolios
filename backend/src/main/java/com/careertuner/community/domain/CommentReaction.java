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
    private LocalDateTime createdAt;
}

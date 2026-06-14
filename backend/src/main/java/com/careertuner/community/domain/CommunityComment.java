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
public class CommunityComment {

    private Long id;
    private Long postId;
    private Long userId;
    private Long parentId;
    private String content;
    private boolean anonymous;
    private String status;
    private int likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // JOIN으로 가져오는 작성자 정보
    private String userName;
}

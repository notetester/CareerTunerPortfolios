package com.careertuner.community.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        PostListResponse.AuthorDto author,
        String content,
        int likeCount,
        boolean isAuthor,
        LocalDateTime createdAt,
        boolean liked
) {}

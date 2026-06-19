package com.careertuner.community.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        Long parentId,
        String mentionLabel,   // 답글 대상 표시명(@익명N). 서버가 현재 익명번호로 동적 산출. 없으면 null.
        PostListResponse.AuthorDto author,
        String content,
        int likeCount,
        boolean isAuthor,
        LocalDateTime createdAt,
        boolean liked
) {}

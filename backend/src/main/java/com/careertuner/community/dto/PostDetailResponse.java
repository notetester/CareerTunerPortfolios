package com.careertuner.community.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long id,
        String category,
        String categoryLabel,
        String title,
        String content,
        List<String> tags,
        PostListResponse.AuthorDto author,
        PostListResponse.StatsDto stats,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String companyName,
        String jobRole,
        InterviewReviewDto interviewReview,
        boolean liked,
        boolean bookmarked
) {

    public record InterviewReviewDto(
            String companyName,
            String jobRole,
            String interviewType,
            Integer difficulty,
            String interviewDate,
            String resultStatus,
            List<String> questions
    ) {}
}

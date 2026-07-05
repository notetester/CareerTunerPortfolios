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
        boolean disliked,
        boolean recommended,
        boolean disrecommended,
        boolean bookmarked,
        boolean scrapped,
        boolean subscribed,
        /** 뷰어가 차단한 작성자의 게시글(content.post) 톰스톤 여부 — true 면 본문이 안내 문구로 교체됨. */
        boolean blocked
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

package com.careertuner.community.dto;

import java.util.List;

import com.careertuner.community.domain.PostCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotNull PostCategory category,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        boolean anonymous,
        List<String> tags,
        String companyName,
        String jobTitle,
        String interviewType,
        String difficulty,
        InterviewReviewRequest interviewReview
) {

    public record InterviewReviewRequest(
            @NotBlank String companyName,
            @NotBlank String jobRole,
            String interviewType,
            Integer difficulty,
            String interviewDate,
            String resultStatus,
            List<String> questions
    ) {}
}

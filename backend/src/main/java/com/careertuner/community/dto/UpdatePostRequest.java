package com.careertuner.community.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 5000) String content,
        boolean anonymous,
        List<String> tags,
        String companyName,
        String jobTitle,
        String interviewType,
        String difficulty,
        CreatePostRequest.InterviewReviewRequest interviewReview
) {}

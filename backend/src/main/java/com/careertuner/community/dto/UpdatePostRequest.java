package com.careertuner.community.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 5000) String content,
        boolean anonymous,
        /** 표시용 닉네임 프로필(선택). null 이면 계정 기본 프로필/계정명으로 표시. 익명이면 무시. */
        Long nicknameProfileId,
        List<String> tags,
        String companyName,
        String jobTitle,
        String interviewType,
        String difficulty,
        CreatePostRequest.InterviewReviewRequest interviewReview
) {}

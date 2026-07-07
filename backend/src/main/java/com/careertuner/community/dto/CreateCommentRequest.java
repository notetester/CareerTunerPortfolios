package com.careertuner.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank @Size(max = 5000) String content,
        Long parentId,
        Boolean anonymous,
        /** 표시용 닉네임 프로필(선택). null 이면 계정 기본 프로필/계정명으로 표시. 익명이면 무시. */
        Long nicknameProfileId
) {}

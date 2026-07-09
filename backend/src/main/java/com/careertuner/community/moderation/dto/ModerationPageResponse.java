package com.careertuner.community.moderation.dto;

import java.util.List;

/**
 * 관리자 검열 목록 페이지 응답.
 */
public record ModerationPageResponse(
        List<ModerationItemResponse> items,
        int total,
        int page,
        int size,
        boolean hasNext
) {}

package com.careertuner.community.moderation.dto;

/**
 * 관리자 검열 목록 조회 요청 파라미터.
 */
public record ModerationListRequest(
        String status,
        Boolean toxic,
        int page,
        int size
) {
    public ModerationListRequest {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
    }

    public int offset() {
        return (page - 1) * size;
    }
}

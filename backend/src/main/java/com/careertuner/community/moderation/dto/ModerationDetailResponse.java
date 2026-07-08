package com.careertuner.community.moderation.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 검열 단건 상세 응답.
 * toxic/aiCategory/confidence/model 은 <b>텍스트</b> 검열(MODERATION) 결과이고,
 * images 는 본문 첨부 <b>이미지</b> 검열(IMAGE_MODERATION) 결과다.
 */
public record ModerationDetailResponse(
        Long postId,
        String title,
        String content,
        String authorName,
        String category,
        String status,
        boolean toxic,
        String aiCategory,
        double confidence,
        String model,
        int attemptCount,
        LocalDateTime createdAt,
        LocalDateTime moderatedAt,
        List<ImageModerationItem> images
) {

    /** 본문 이미지별 검열 결과. action: {@code hide}(글 숨김) | {@code blur}(블러) | {@code allow}(통과). */
    public record ImageModerationItem(String url, String category, Double confidence, String action) {}
}

package com.careertuner.admin.ads.dto;

import java.time.LocalDateTime;

import com.careertuner.ads.domain.Advertisement;

/**
 * 관리자 광고 응답 — 노출/클릭 집계와 파생 CTR 포함.
 *
 * @param imageUrl 이미지 미리보기 경로(없으면 null)
 * @param ctr      클릭률(%). 노출 0이면 0.0
 */
public record AdminAdResponse(
        Long id,
        String title,
        Long imageFileId,
        String imageUrl,
        String linkUrl,
        String placement,
        String targetPlatform,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean active,
        int priority,
        int weight,
        long impressionCount,
        long clickCount,
        double ctr,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminAdResponse from(Advertisement ad) {
        long impressions = ad.getImpressionCount() != null ? ad.getImpressionCount() : 0;
        long clicks = ad.getClickCount() != null ? ad.getClickCount() : 0;
        double ctr = impressions > 0 ? Math.round((clicks * 10000.0) / impressions) / 100.0 : 0.0;
        return new AdminAdResponse(
                ad.getId(),
                ad.getTitle(),
                ad.getImageFileId(),
                ad.getImageFileId() != null ? "/api/ads/" + ad.getId() + "/image" : null,
                ad.getLinkUrl(),
                ad.getPlacement(),
                ad.getTargetPlatform(),
                ad.getStartAt(),
                ad.getEndAt(),
                Boolean.TRUE.equals(ad.getActive()),
                ad.getPriority() != null ? ad.getPriority() : 0,
                ad.getWeight() != null ? ad.getWeight() : 1,
                impressions,
                clicks,
                ctr,
                ad.getCreatedAt(),
                ad.getUpdatedAt());
    }
}

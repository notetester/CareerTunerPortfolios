package com.careertuner.ads.dto;

import com.careertuner.ads.domain.Advertisement;

/**
 * 공개 노출용 광고 응답. 관리자 통계 컬럼(집계·created_by 등)은 노출하지 않는다.
 *
 * @param imageUrl 이미지가 있으면 다운로드 엔드포인트 경로, 없으면 null(텍스트 배너)
 */
public record AdResponse(
        Long id,
        String title,
        String imageUrl,
        String linkUrl,
        String placement,
        String targetPlatform) {

    public static AdResponse from(Advertisement ad) {
        return new AdResponse(
                ad.getId(),
                ad.getTitle(),
                ad.getImageFileId() != null ? "/api/ads/" + ad.getId() + "/image" : null,
                ad.getLinkUrl(),
                ad.getPlacement(),
                ad.getTargetPlatform());
    }
}

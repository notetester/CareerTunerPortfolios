package com.careertuner.ads.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 광고 행. placement/target_platform 로 노출 대상을 좁히고 priority/weight 로 선택한다.
 *
 * <p>노출/클릭 집계는 컬럼 직접 +1(로그 테이블 없음). 이미지는 file_asset(imageFileId) 재사용.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Advertisement {

    private Long id;
    private String title;
    private Long imageFileId;
    private String linkUrl;
    private String placement;        // HOME_BANNER/FEED_INLINE/SIDEBAR/INTERSTITIAL
    private String targetPlatform;   // WEB/APP/DESKTOP/ALL
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean active;
    private Integer priority;
    private Integer weight;
    private Long impressionCount;
    private Long clickCount;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

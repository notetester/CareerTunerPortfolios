package com.careertuner.ad.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdCampaign {

    private Long id;
    private String title;
    private String body;
    private String surface;
    private String placement;
    private String creativeType;
    private String imageUrl;
    private String targetUrl;
    private String visibleToPlansJson;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private int priority;
    private boolean active;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

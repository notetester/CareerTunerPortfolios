package com.careertuner.community.guideline.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityGuideline {
    private Long id;
    private String versionLabel;
    private String summary;
    private String lede;
    private String oksJson;
    private String nosJson;
    private String rulesJson;
    private String paramsJson;
    private String status;
    private String enforceType;
    private LocalDateTime scheduledAt;
    private LocalDateTime publishedAt;
    private Long adminId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

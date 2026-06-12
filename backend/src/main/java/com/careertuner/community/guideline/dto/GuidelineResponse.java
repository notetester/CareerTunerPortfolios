package com.careertuner.community.guideline.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidelineResponse {
    private Long id;
    private String versionLabel;
    private String lede;
    private String oksJson;
    private String nosJson;
    private String rulesJson;
    private String paramsJson;
    private LocalDateTime publishedAt;
}

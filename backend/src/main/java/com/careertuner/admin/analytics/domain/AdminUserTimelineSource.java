package com.careertuner.admin.analytics.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserTimelineSource {
    private String eventType;
    private Long refId;
    private String summary;
    private String status;
    private Integer score;
    private LocalDateTime createdAt;
}

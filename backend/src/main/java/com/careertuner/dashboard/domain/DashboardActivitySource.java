package com.careertuner.dashboard.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardActivitySource {

    private String type;
    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private String content;
    private LocalDateTime occurredAt;
    private Integer score;
}

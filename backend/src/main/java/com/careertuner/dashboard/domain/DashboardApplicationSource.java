package com.careertuner.dashboard.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardApplicationSource {

    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private LocalDate postingDate;
    private String status;
    private boolean favorite;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer fitScore;
    private String requiredSkills;
    private String missingSkills;
    private LocalDateTime analyzedAt;
    private int interviewCount;
    private Integer latestInterviewScore;
}

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
public class AdminAnalysisSource {

    private Long applicationCaseId;
    private Long fitAnalysisId;
    private String userName;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private String missingSkills;
    private Integer fitScore;
    private LocalDateTime analyzedAt;
}

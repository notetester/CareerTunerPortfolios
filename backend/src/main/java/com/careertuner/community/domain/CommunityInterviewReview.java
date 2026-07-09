package com.careertuner.community.domain;

import java.math.BigDecimal;
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
public class CommunityInterviewReview {

    private Long postId;
    private Long applicationCaseId;
    private String companyName;
    private String jobRole;
    private String interviewType;
    private Integer difficulty;
    private LocalDate interviewDate;
    private String resultStatus;
    private String questionsJson;
    private String aiSummaryJson;
    private String aiExtractedQuestions;
    private String verificationStatus;
    private String verificationEvidenceUrl;
    private BigDecimal verificationConfidence;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
}

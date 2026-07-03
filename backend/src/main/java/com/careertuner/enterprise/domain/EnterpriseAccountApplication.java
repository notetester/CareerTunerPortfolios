package com.careertuner.enterprise.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseAccountApplication {

    private Long id;
    private Long userId;
    private String companyName;
    private String businessNumber;
    private String representativeName;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String websiteUrl;
    private String industry;
    private String employeeCount;
    private String evidenceFileUrl;
    private String requestedPolicyJson;
    private String status;
    private String reviewMemo;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String userEmail;
    private String userName;
}

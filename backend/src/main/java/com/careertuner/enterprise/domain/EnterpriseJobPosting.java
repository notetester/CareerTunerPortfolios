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
public class EnterpriseJobPosting {

    private Long id;
    private Long companyUserId;
    private String companyName;
    private String title;
    private String positionTitle;
    private String jobCategory;
    private String specialtiesJson;
    private String duties;
    private String qualifications;
    private String preferred;
    private String benefits;
    private String employmentType;
    private String experienceLevel;
    private String educationLevel;
    private String salaryType;
    private Integer salaryMin;
    private Integer salaryMax;
    private String salaryText;
    private String workLocation;
    private String workSchedule;
    private String headcount;
    private LocalDateTime applicationStartAt;
    private LocalDateTime applicationEndAt;
    private String applyUrl;
    private String contactEmail;
    private String contactPhone;
    private String visibility;
    private String status;
    private String reviewStatus;
    private String reviewMemo;
    private String pendingRevisionJson;
    private Long communityPostId;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String ownerEmail;
    private String ownerName;
}

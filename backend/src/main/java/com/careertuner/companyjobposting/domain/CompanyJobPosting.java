package com.careertuner.companyjobposting.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 기업 채용공고 게시판 행. status: DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED/CLOSED. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyJobPosting {

    private Long id;
    private Long companyUserId;
    private String title;
    private String jobRole;
    private String employmentType;
    private String careerLevel;
    private Integer careerYearsMin;
    private Integer careerYearsMax;
    private String educationLevel;
    private String salaryText;
    private Boolean salaryNegotiable;
    private String workLocation;
    private String workHours;
    private LocalDate deadlineDate;
    private Boolean alwaysOpen;
    private String mainTasks;
    private String requirements;
    private String preferred;
    private String benefits;
    private String hiringProcess;
    private String headcount;
    private String tagsJson;
    private String status;
    private String rejectReason;
    private Integer viewCount;
    private LocalDateTime publishedAt;
    private LocalDateTime closedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // JOIN 파생 컬럼
    private String companyName;
    private String trustGrade;
    private Boolean hasPendingRevision;
}

package com.careertuner.admin.applicationcase.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminApplicationCaseRow {

    private Long id;
    private Long userId;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private LocalDate postingDate;
    private LocalDate deadlineDate;
    private String sourceType;
    private String status;
    private boolean favorite;
    private LocalDateTime archivedAt;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer latestPostingRevision;
    private LocalDateTime latestJobAnalysisAt;
    private LocalDateTime latestCompanyAnalysisAt;
}

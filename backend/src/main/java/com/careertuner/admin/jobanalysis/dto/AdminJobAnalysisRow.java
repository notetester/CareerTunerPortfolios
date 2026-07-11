package com.careertuner.admin.jobanalysis.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminJobAnalysisRow {

    private Long id;
    private Long applicationCaseId;
    private Long jobPostingId;
    private Integer jobPostingRevision;
    private Integer latestJobPostingRevision;
    private Boolean staleAgainstLatestPosting;
    private Long userId;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private String employmentType;
    private String experienceLevel;
    private String requiredSkills;
    private String preferredSkills;
    private String duties;
    private String qualifications;
    private String difficulty;
    private String summary;
    private String evidence;
    private String ambiguousConditions;
    private LocalDateTime confirmedAt;
    private String adminMemo;
    // 모델 선택·실행 provenance (지원건별 모델 선택·재실행). 자동 초기 실행·strict 재분석만 채우고, 레거시 행은 NULL.
    private String requestedProvider;
    private String actualProvider;
    private String actualModel;
    private Boolean fallbackUsed;
    private String attemptPath;
    private String runMode;
    private LocalDateTime createdAt;
}

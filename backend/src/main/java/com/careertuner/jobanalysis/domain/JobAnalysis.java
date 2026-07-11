package com.careertuner.jobanalysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobAnalysis {

    private Long id;
    private Long applicationCaseId;
    private Long jobPostingId;
    private Integer jobPostingRevision;
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
    // 모델 선택·실행 provenance (지원건별 모델 선택·재실행 슬라이스). 전부 NULL 허용 —
    // 기존 행·자동 파이프라인 생성분은 NULL(=unknown). strict 재분석만 채운다.
    private String requestedProvider;
    private String actualProvider;
    private String actualModel;
    private Boolean fallbackUsed;
    private String attemptPath;
    private String runMode;
    private LocalDateTime createdAt;
}

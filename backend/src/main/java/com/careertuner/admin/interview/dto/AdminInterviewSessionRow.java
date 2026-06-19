package com.careertuner.admin.interview.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 관리자 면접 세션 목록/상세 행 (세션 + 지원건 + 사용자 조인). */
@Data
public class AdminInterviewSessionRow {

    private Long id;
    private Long applicationCaseId;
    private Long userId;
    private String userEmail;
    private String companyName;
    private String jobTitle;
    private String mode;
    private Integer totalScore;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private Integer questionCount;
    private Integer answeredCount;
    private String adminMemo;
}

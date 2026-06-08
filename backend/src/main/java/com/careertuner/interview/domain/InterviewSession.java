package com.careertuner.interview.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    private Long id;
    private Long applicationCaseId;
    private String mode;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer totalScore;
    /** interview_session.report (JSON 컬럼) 원문 문자열. */
    private String report;
    private LocalDateTime createdAt;
}

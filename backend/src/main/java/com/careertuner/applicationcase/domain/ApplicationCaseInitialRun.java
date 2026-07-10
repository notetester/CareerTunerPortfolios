package com.careertuner.applicationcase.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 지원 건별 초기 실행 프로필. 초기 자동 파이프라인의 중복 진입 방지(state claim) + 늦은 완료 fencing(execution_token).
 * state: PENDING(등록 직후) → RUNNING(claim) → DONE|FAILED. job/company 선택값은 async 파이프라인이 읽는다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCaseInitialRun {

    private Long applicationCaseId;
    private String state;
    private String jobAnalysisProvider;
    private String companyAnalysisProvider;
    private String executionToken;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

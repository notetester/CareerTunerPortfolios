package com.careertuner.dashboard.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 최근 면접 카드용 읽기 전용 조회 결과. interview_session(D 소유)은 조회만 하고 수정하지 않는다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardInterviewSource {

    private Long sessionId;
    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private String mode;
    private Integer totalScore;
    private LocalDateTime occurredAt;
}

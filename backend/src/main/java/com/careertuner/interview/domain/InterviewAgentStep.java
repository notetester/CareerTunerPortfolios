package com.careertuner.interview.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 멀티에이전트 면접 진행의 한 단계 트레이스. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAgentStep {

    private Long id;
    private Long interviewSessionId;
    private Long questionId;
    private Integer stepNo;
    private String agent;     // EVALUATOR/CRITIC/...
    private String action;
    private String summary;
    private String detail;    // JSON 문자열
    private LocalDateTime createdAt;
}

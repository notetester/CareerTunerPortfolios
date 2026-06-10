package com.careertuner.interview.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 면접 평가 학습 샘플. 파인튜닝/평가 하니스의 원천 데이터. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewTrainingSample {

    private Long id;
    private Long interviewSessionId;
    private Long questionId;
    private String question;
    private String answerText;
    private Integer score;
    private String feedback;
    private Boolean ragUsed;
    private String model;
    private LocalDateTime createdAt;
}

package com.careertuner.interview.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestion {

    private Long id;
    private Long interviewSessionId;
    private Long parentQuestionId;
    private String question;
    private String questionType;
    private Integer sortOrder;
}

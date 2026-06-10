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
public class InterviewAnswer {

    private Long id;
    private Long questionId;
    private String answerText;
    private String audioUrl;
    private String videoUrl;
    private Integer score;
    private String feedback;
    private String improvedAnswer;
    private LocalDateTime createdAt;
}

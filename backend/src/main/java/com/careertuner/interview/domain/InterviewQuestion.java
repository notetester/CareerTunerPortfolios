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
    /**
     * 이 질문의 모범답안(답안지). 처음 "모범답안 보기" 할 때 생성·저장하고, 이후 채점의 만점 기준으로 재사용한다.
     * 블라인드인 복습 테스트도 이 저장값을 기준으로 채점한다. (model_answer 컬럼)
     */
    private String modelAnswer;
}

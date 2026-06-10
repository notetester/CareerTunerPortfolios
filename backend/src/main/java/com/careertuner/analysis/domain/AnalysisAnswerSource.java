package com.careertuner.analysis.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "자주 개선이 필요한 답변 요소" 집계용 읽기 전용 행(기획 §8.9: 답변의 공통 약점).
 * interview_answer/interview_question(D 소유)은 조회만 하고 수정하지 않는다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisAnswerSource {

    private String questionType;
    private Integer score;
    private String feedback;
}

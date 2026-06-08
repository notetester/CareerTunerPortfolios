package com.careertuner.admin.interview.dto;

import java.util.List;

import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.InterviewQuestionResponse;

public record AdminInterviewSessionDetail(
        AdminInterviewSessionRow session,
        List<InterviewQuestionResponse> questions,
        List<InterviewAnswerResponse> answers,
        String report) {
}

package com.careertuner.admin.interview.dto;

import java.util.List;

import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.InterviewQuestionResponse;
import com.careertuner.interview.media.dto.MediaAnalysisResponse;

public record AdminInterviewSessionDetail(
        AdminInterviewSessionRow session,
        List<InterviewQuestionResponse> questions,
        List<InterviewAnswerResponse> answers,
        List<MediaAnalysisResponse> mediaResults,
        String report) {
}

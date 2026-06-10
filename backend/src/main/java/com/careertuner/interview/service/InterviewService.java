package com.careertuner.interview.service;

import java.util.List;

import com.careertuner.interview.dto.CreateInterviewSessionRequest;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.InterviewQuestionResponse;
import com.careertuner.interview.dto.InterviewReportResponse;
import com.careertuner.interview.dto.InterviewSessionResponse;
import com.careertuner.interview.dto.SubmitAnswerRequest;

public interface InterviewService {

    InterviewSessionResponse createSession(Long userId, CreateInterviewSessionRequest request);

    List<InterviewSessionResponse> listSessions(Long userId);

    List<InterviewQuestionResponse> generateQuestions(Long userId, Long sessionId, GenerateQuestionsRequest request);

    List<InterviewQuestionResponse> listQuestions(Long userId, Long sessionId);

    InterviewAnswerResponse submitAnswer(Long userId, Long questionId, SubmitAnswerRequest request);

    InterviewReportResponse getReport(Long userId, Long sessionId);
}

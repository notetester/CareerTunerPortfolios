package com.careertuner.interview.service;

import java.util.List;

import com.careertuner.interview.dto.CreateInterviewSessionRequest;
import com.careertuner.interview.dto.GenerateFollowUpsRequest;
import com.careertuner.interview.dto.InterviewAgentStepResponse;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.InterviewProgressResponse;
import com.careertuner.interview.dto.InterviewQuestionResponse;
import com.careertuner.interview.dto.InterviewReportResponse;
import com.careertuner.interview.dto.InterviewSessionResponse;
import com.careertuner.interview.dto.SubmitAnswerRequest;

public interface InterviewService {

    InterviewSessionResponse createSession(Long userId, CreateInterviewSessionRequest request);

    List<InterviewSessionResponse> listSessions(Long userId);

    List<InterviewQuestionResponse> generateQuestions(Long userId, Long sessionId, GenerateQuestionsRequest request);

    List<InterviewQuestionResponse> listQuestions(Long userId, Long sessionId);

    /** 특정 질문에 대한 지원자 답변을 바탕으로 꼬리 질문을 생성해 세션 질문 목록에 이어 붙인다. */
    List<InterviewQuestionResponse> generateFollowUps(Long userId, Long questionId, GenerateFollowUpsRequest request);

    InterviewAnswerResponse submitAnswer(Long userId, Long questionId, SubmitAnswerRequest request);

    /** 답변 유무 기반 진행 상태와 다음에 답할 질문을 반환한다. (AI 면접관 대화 진행) */
    InterviewProgressResponse getProgress(Long userId, Long sessionId);

    /** 세션의 멀티에이전트 진행 단계 트레이스. */
    List<InterviewAgentStepResponse> getAgentSteps(Long userId, Long sessionId);

    InterviewReportResponse getReport(Long userId, Long sessionId);
}

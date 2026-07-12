package com.careertuner.interview.service;

import java.util.List;
import java.util.UUID;

import com.careertuner.interview.dto.CreateInterviewSessionRequest;
import com.careertuner.interview.dto.GenerateFollowUpsRequest;
import com.careertuner.interview.dto.InterviewAgentStepResponse;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.InterviewProgressResponse;
import com.careertuner.interview.dto.InterviewQuestionResponse;
import com.careertuner.interview.dto.InterviewReportResponse;
import com.careertuner.interview.dto.InterviewSessionResponse;
import com.careertuner.interview.dto.InterviewDispatchTarget;
import com.careertuner.interview.dto.ModelAnswerResponse;
import com.careertuner.interview.dto.SessionPageResponse;
import com.careertuner.interview.dto.SessionReviewResponse;
import com.careertuner.interview.dto.SubmitAnswerRequest;
import tools.jackson.databind.JsonNode;

public interface InterviewService {

    InterviewSessionResponse createSession(Long userId, CreateInterviewSessionRequest request);

    SessionPageResponse listSessions(Long userId, int page, int size);

    /** 최근 면접 기록 soft delete (본인 세션만). */
    void deleteSession(Long userId, Long sessionId);

    /** 기존 세션 복원(=복습) 시각 기록. */
    void markResumed(Long userId, Long sessionId);

    /** 면접 세션을 지정한 플랫폼에서 이어받도록 사용자에게 알림을 남긴다. */
    void dispatchSession(Long userId, Long sessionId, InterviewDispatchTarget target);

    default List<InterviewQuestionResponse> generateQuestions(Long userId, Long sessionId,
                                                              GenerateQuestionsRequest request) {
        return generateQuestions(userId, sessionId, request, "INTERNAL:" + UUID.randomUUID());
    }

    List<InterviewQuestionResponse> generateQuestions(Long userId, Long sessionId,
                                                      GenerateQuestionsRequest request, String operationKey);

    List<InterviewQuestionResponse> listQuestions(Long userId, Long sessionId);

    /** 특정 질문에 대한 지원자 답변을 바탕으로 꼬리 질문을 생성해 세션 질문 목록에 이어 붙인다. */
    default List<InterviewQuestionResponse> generateFollowUps(Long userId, Long questionId,
                                                              GenerateFollowUpsRequest request) {
        return generateFollowUps(userId, questionId, request, "INTERNAL:" + UUID.randomUUID());
    }

    List<InterviewQuestionResponse> generateFollowUps(Long userId, Long questionId,
                                                      GenerateFollowUpsRequest request, String operationKey);

    InterviewAnswerResponse submitAnswer(Long userId, Long questionId, SubmitAnswerRequest request);

    /** 답변에 연결된 음성 또는 영상 원본을 물리 저장소와 메타데이터에서 함께 삭제한다. */
    void deleteAnswerMedia(Long userId, Long answerId, String kind);

    /** 답변 유무 기반 진행 상태와 다음에 답할 질문을 반환한다. (AI 면접관 대화 진행) */
    InterviewProgressResponse getProgress(Long userId, Long sessionId);

    /** 세션의 멀티에이전트 진행 단계 트레이스. */
    List<InterviewAgentStepResponse> getAgentSteps(Long userId, Long sessionId);

    InterviewReportResponse getReport(Long userId, Long sessionId);

    /** 질문에 대한 모범 답변 생성(학습용). 답변 제출 전에도 호출 가능. */
    ModelAnswerResponse getModelAnswer(Long userId, Long questionId);

    /** 지난 세션 복기: 질문 + 저장된 모범답안 + 내 최신 답변/점수. (최근 면접 기록에서 들어가 보기) */
    SessionReviewResponse getSessionReview(Long userId, Long sessionId);

    /**
     * 음성 모의면접 트랜스크립트를 질문별로 채점해 저장하고, 채점한 문항 수를 반환한다.
     * @param questionLimit 채점 대상 질문 수 제한 (1~6, null=전체). 체험판은 1.
     */
    int scoreVoiceTranscript(Long userId, Long sessionId, JsonNode transcript, Integer questionLimit);
}

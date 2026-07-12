package com.careertuner.interview.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.interview.domain.InterviewAgentStep;
import com.careertuner.interview.domain.InterviewAiUsageLog;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;

@Mapper
public interface InterviewMapper {

    // ── 세션 ──
    void insertSession(InterviewSession session);

    List<InterviewSession> findSessionsByUserId(@Param("userId") Long userId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    int countSessionsByUserId(@Param("userId") Long userId);

    /** 본인 세션을 soft delete (deleted_at 기록). 이미 삭제됐거나 본인 것이 아니면 0. */
    int softDeleteSession(@Param("id") Long id, @Param("userId") Long userId);

    /** 본인 세션의 복습(복원) 시각 갱신. 본인 것이 아니거나 삭제됐으면 0. */
    int touchSessionResumed(@Param("id") Long id, @Param("userId") Long userId);

    InterviewSession findSessionByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 질문/답변/리포트 입력 변경을 직렬화하는 세션 행 잠금. */
    InterviewSession lockSessionByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int updateSessionResult(@Param("id") Long id,
                            @Param("totalScore") Integer totalScore,
                            @Param("report") String report,
                            @Param("endedAt") LocalDateTime endedAt);

    /** 입력이 바뀌면 이전 리포트 스냅샷과 완료 시각을 함께 무효화한다. */
    int invalidateSessionResult(@Param("id") Long id);

    Integer findLatestScoredSessionScore(@Param("applicationCaseId") Long applicationCaseId,
                                         @Param("excludeSessionId") Long excludeSessionId);

    // ── 질문 ──
    void insertQuestion(InterviewQuestion question);

    void deleteQuestionsBySessionId(@Param("sessionId") Long sessionId);

    List<InterviewQuestion> findQuestionsBySessionId(@Param("sessionId") Long sessionId);

    InterviewQuestion findQuestionByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 세션과 질문 행을 함께 잠가 질문 재생성과 답변 제출의 삭제 경합을 막는다. */
    InterviewQuestion lockQuestionByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 답변·원본·분석·평가 trace 중 하나라도 있으면 질문 교체를 차단한다. */
    boolean hasQuestionRegenerationBlockers(@Param("sessionId") Long sessionId);

    /**
     * 유료 질문 생성 operation을 선점한다. 같은 사용자/기능/대상/키가 이미 커밋되었으면 0이다.
     * 대상 세션·질문 행 잠금 뒤 호출하므로 중복 요청은 선행 트랜잭션 종료 후 결과를 재사용한다.
     */
    int insertAiOperationReservation(@Param("userId") Long userId,
                                     @Param("featureType") String featureType,
                                     @Param("targetId") Long targetId,
                                     @Param("operationKey") String operationKey);

    /** 질문의 모범답안(답안지)을 저장한다. 채점 기준으로 재사용한다. (model_answer 컬럼 필요) */
    int updateQuestionModelAnswer(@Param("id") Long id, @Param("modelAnswer") String modelAnswer);

    /** 세션 내 가장 큰 sort_order. 꼬리 질문을 뒤에 이어 붙일 때 사용. (질문 없으면 null) */
    Integer findMaxSortOrder(@Param("sessionId") Long sessionId);

    // ── 답변 ──
    void insertAnswer(InterviewAnswer answer);

    /**
     * 멱등 키가 있는 답변의 평가 예약을 선점한다. 동일 질문/키가 이미 있으면 0을 반환한다.
     * INSERT IGNORE는 유니크 키 경쟁에서 먼저 시작된 트랜잭션이 끝날 때까지 기다리므로
     * 중복 AI 평가가 시작되기 전에 승자를 확정한다.
     */
    int insertAnswerReservation(InterviewAnswer answer);

    InterviewAnswer findAnswerByQuestionIdAndClientSubmissionId(
            @Param("questionId") Long questionId,
            @Param("clientSubmissionId") String clientSubmissionId);

    int completeAnswerReservation(InterviewAnswer answer);

    List<InterviewAnswer> findAnswersBySessionId(@Param("sessionId") Long sessionId);

    /** 특정 질문에 저장된 가장 최근 답변. 꼬리 질문 생성 입력으로 사용. (없으면 null) */
    InterviewAnswer findLatestAnswerByQuestionId(@Param("questionId") Long questionId);

    InterviewAnswer findAnswerByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int updateAnswerMediaUrls(@Param("id") Long id,
                              @Param("audioUrl") String audioUrl,
                              @Param("videoUrl") String videoUrl);

    int clearAnswerAudioUrl(@Param("id") Long id);

    int clearAnswerVideoUrl(@Param("id") Long id);

    // ── AI 사용량 ──
    void insertAiUsageLog(InterviewAiUsageLog aiUsageLog);

    // ── 에이전트 트레이스 ──
    void insertAgentStep(InterviewAgentStep step);

    List<InterviewAgentStep> findAgentStepsBySessionId(@Param("sessionId") Long sessionId);
}

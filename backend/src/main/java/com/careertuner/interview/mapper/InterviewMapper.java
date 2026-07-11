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

    int updateSessionResult(@Param("id") Long id,
                            @Param("totalScore") Integer totalScore,
                            @Param("report") String report,
                            @Param("endedAt") LocalDateTime endedAt);

    Integer findLatestScoredSessionScore(@Param("applicationCaseId") Long applicationCaseId,
                                         @Param("excludeSessionId") Long excludeSessionId);

    // ── 질문 ──
    void insertQuestion(InterviewQuestion question);

    void deleteQuestionsBySessionId(@Param("sessionId") Long sessionId);

    List<InterviewQuestion> findQuestionsBySessionId(@Param("sessionId") Long sessionId);

    InterviewQuestion findQuestionByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 질문의 모범답안(답안지)을 저장한다. 채점 기준으로 재사용한다. (model_answer 컬럼 필요) */
    int updateQuestionModelAnswer(@Param("id") Long id, @Param("modelAnswer") String modelAnswer);

    /** 세션 내 가장 큰 sort_order. 꼬리 질문을 뒤에 이어 붙일 때 사용. (질문 없으면 null) */
    Integer findMaxSortOrder(@Param("sessionId") Long sessionId);

    // ── 답변 ──
    void insertAnswer(InterviewAnswer answer);

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

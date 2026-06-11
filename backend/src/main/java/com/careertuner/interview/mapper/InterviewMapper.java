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

    List<InterviewSession> findSessionsByUserId(@Param("userId") Long userId);

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

    /** 세션 내 가장 큰 sort_order. 꼬리 질문을 뒤에 이어 붙일 때 사용. (질문 없으면 null) */
    Integer findMaxSortOrder(@Param("sessionId") Long sessionId);

    // ── 답변 ──
    void insertAnswer(InterviewAnswer answer);

    List<InterviewAnswer> findAnswersBySessionId(@Param("sessionId") Long sessionId);

    /** 특정 질문에 저장된 가장 최근 답변. 꼬리 질문 생성 입력으로 사용. (없으면 null) */
    InterviewAnswer findLatestAnswerByQuestionId(@Param("questionId") Long questionId);

    // ── AI 사용량 ──
    void insertAiUsageLog(InterviewAiUsageLog aiUsageLog);

    // ── 에이전트 트레이스 ──
    void insertAgentStep(InterviewAgentStep step);

    List<InterviewAgentStep> findAgentStepsBySessionId(@Param("sessionId") Long sessionId);
}

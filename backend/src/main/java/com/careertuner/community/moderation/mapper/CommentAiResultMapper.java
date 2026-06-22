package com.careertuner.community.moderation.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.CommentAiResult;

/**
 * 댓글 AI 검열 결과 매퍼. PostAiResultMapper 구조 복제(대상만 commentId).
 * 조회(getComments)와는 절대 조인하지 않는다 — 감사/관리자/배치 전용.
 */
@Mapper
public interface CommentAiResultMapper {

    int upsertPending(@Param("commentId") Long commentId,
                      @Param("taskType") AiTaskType taskType);

    int complete(@Param("commentId") Long commentId,
                 @Param("taskType") AiTaskType taskType,
                 @Param("resultJson") String resultJson,
                 @Param("model") String model);

    int fail(@Param("commentId") Long commentId,
             @Param("taskType") AiTaskType taskType,
             @Param("errorMessage") String errorMessage);

    CommentAiResult findByCommentIdAndTaskType(@Param("commentId") Long commentId,
                                               @Param("taskType") AiTaskType taskType);

    /** 배치 재검열 대상 댓글 ID(아직 검열 COMPLETED 기록이 없는 PUBLISHED 댓글). post 패턴 복제. */
    List<Long> findCommentIdsForModeration(@Param("force") boolean force);
}

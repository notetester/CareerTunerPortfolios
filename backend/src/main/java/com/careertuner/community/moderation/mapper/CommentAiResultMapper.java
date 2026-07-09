package com.careertuner.community.moderation.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.CommentAiResult;
import com.careertuner.community.moderation.domain.ModerationView;

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

    /** 판정 불성립 기록 — PostAiResultMapper.markUnmoderated 복제(대상만 commentId). */
    int markUnmoderated(@Param("commentId") Long commentId,
                        @Param("taskType") AiTaskType taskType,
                        @Param("reason") String reason,
                        @Param("model") String model);

    CommentAiResult findByCommentIdAndTaskType(@Param("commentId") Long commentId,
                                               @Param("taskType") AiTaskType taskType);

    /** 배치 재검열 대상 댓글 ID(아직 검열 COMPLETED 기록이 없는 PUBLISHED 댓글). post 패턴 복제. */
    List<Long> findCommentIdsForModeration(@Param("force") boolean force);

    // ── 관리자 댓글 검열 목록/상세 (PostAiResultMapper.findModerationList 복제, ModerationView 재사용) ──

    List<ModerationView> findCommentModerationList(@Param("status") String status,
                                                   @Param("toxic") Boolean toxic,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    int countCommentModerationList(@Param("status") String status,
                                   @Param("toxic") Boolean toxic);

    ModerationView findCommentModerationDetail(@Param("commentId") Long commentId);

    List<Map<String, Object>> countCommentByAiCategory();
}

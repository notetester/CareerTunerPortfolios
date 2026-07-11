package com.careertuner.community.moderation.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.ModerationView;
import com.careertuner.community.moderation.domain.ModerationReviewQueueView;
import com.careertuner.community.moderation.domain.PostAiResult;

@Mapper
public interface PostAiResultMapper {

    int upsertPending(@Param("postId") Long postId,
                      @Param("taskType") AiTaskType taskType);

    int complete(@Param("postId") Long postId,
                 @Param("taskType") AiTaskType taskType,
                 @Param("resultJson") String resultJson,
                 @Param("model") String model);

    int fail(@Param("postId") Long postId,
             @Param("taskType") AiTaskType taskType,
             @Param("errorMessage") String errorMessage);

    /**
     * 판정 불성립(모든 provider 실패 mock 폴백 / confidence 누락) 기록.
     * COMPLETED 가 아니므로 재시도 스케줄러(NOT EXISTS status='COMPLETED')가 다시 집는다.
     */
    int markUnmoderated(@Param("postId") Long postId,
                        @Param("taskType") AiTaskType taskType,
                        @Param("reason") String reason,
                        @Param("model") String model);

    PostAiResult findByPostIdAndTaskType(@Param("postId") Long postId,
                                         @Param("taskType") AiTaskType taskType);

    /** 사용자별 최근 window(since 이후) 블러 처리된 글 수 — 소프트 스트라이크 누적 제재 판정용. */
    int countBlurredByUserSince(@Param("userId") Long userId,
                                @Param("since") LocalDateTime since);

    List<ModerationView> findModerationList(@Param("status") String status,
                                            @Param("toxic") Boolean toxic,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    int countModerationList(@Param("status") String status,
                            @Param("toxic") Boolean toxic);

    ModerationView findModerationDetail(@Param("postId") Long postId);

    List<ModerationReviewQueueView> findReviewQueue(@Param("hideThreshold") double hideThreshold,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    int countReviewQueue(@Param("hideThreshold") double hideThreshold);

    int recordReviewAction(@Param("postId") Long postId,
                           @Param("action") String action,
                           @Param("reviewerId") Long reviewerId,
                           @Param("hideThreshold") double hideThreshold);

    String findReviewAction(@Param("postId") Long postId);

    List<Map<String, Object>> countByAiCategory();
}

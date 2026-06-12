package com.careertuner.community.moderation.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.ModerationView;
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

    PostAiResult findByPostIdAndTaskType(@Param("postId") Long postId,
                                         @Param("taskType") AiTaskType taskType);

    List<ModerationView> findModerationList(@Param("status") String status,
                                            @Param("toxic") Boolean toxic,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    int countModerationList(@Param("status") String status,
                            @Param("toxic") Boolean toxic);

    ModerationView findModerationDetail(@Param("postId") Long postId);

    List<Map<String, Object>> countByAiCategory();
}

package com.careertuner.community.moderation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.moderation.domain.AiTaskType;
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
}

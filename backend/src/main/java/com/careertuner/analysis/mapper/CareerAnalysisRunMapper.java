package com.careertuner.analysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.analysis.domain.CareerAnalysisRun;

@Mapper
public interface CareerAnalysisRunMapper {

    void insert(CareerAnalysisRun run);

    void insertAiUsageLog(@Param("userId") Long userId,
                          @Param("featureType") String featureType,
                          @Param("status") String status,
                          @Param("model") String model,
                          @Param("inputTokens") int inputTokens,
                          @Param("outputTokens") int outputTokens,
                          @Param("tokenUsage") int tokenUsage,
                          @Param("errorMessage") String errorMessage);

    List<CareerAnalysisRun> findByUserId(Long userId);

    List<CareerAnalysisRun> findAll();
}

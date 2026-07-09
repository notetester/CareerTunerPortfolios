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
                          @Param("creditUsed") int creditUsed,
                          @Param("errorMessage") String errorMessage);

    void insertDashboardInsight(@Param("userId") Long userId,
                                @Param("runId") Long runId,
                                @Param("summary") String summary,
                                @Param("status") String status,
                                @Param("model") String model,
                                @Param("tokenUsage") int tokenUsage);

    /** 캐시 조회: 같은 사용자·분석 유형의 가장 최근 실행 1건. fingerprint 비교로 재사용 여부를 판단한다. */
    CareerAnalysisRun findLatest(@Param("userId") Long userId, @Param("analysisType") String analysisType);

    List<CareerAnalysisRun> findByUserId(Long userId);

    List<CareerAnalysisRun> findAll();
}

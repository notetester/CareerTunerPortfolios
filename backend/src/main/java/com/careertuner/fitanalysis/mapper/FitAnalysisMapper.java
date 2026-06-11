package com.careertuner.fitanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisLearningTask;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;

@Mapper
public interface FitAnalysisMapper {

    List<FitAnalysisResult> findLatestByUserId(Long userId);

    FitAnalysisResult findLatestByUserIdAndApplicationCaseId(@Param("userId") Long userId,
                                                             @Param("applicationCaseId") Long applicationCaseId);

    /**
     * 적합도 분석 생성 입력 원천을 읽는다(application_case + 최신 job_analysis + user_profile 조인).
     * 소유권(user_id) 검증을 쿼리에서 함께 수행하며, 결과가 없으면 null.
     */
    FitAnalysisGenerationSource findGenerationSource(@Param("userId") Long userId,
                                                     @Param("applicationCaseId") Long applicationCaseId);

    /**
     * 재분석 히스토리: 해당 지원 건의 모든 적합도 분석 이력(오래된 순).
     * 점수 변화·매칭/부족 역량 변화 비교에 사용한다.
     */
    List<FitAnalysisResult> findAllByUserIdAndApplicationCaseId(@Param("userId") Long userId,
                                                                @Param("applicationCaseId") Long applicationCaseId);

    void insertFitAnalysis(FitAnalysisResult fitAnalysis);

    void insertLearningTask(FitAnalysisLearningTask task);

    List<FitAnalysisLearningTask> findLearningTasksByFitAnalysisId(Long fitAnalysisId);

    int updateLearningTaskCompleted(@Param("userId") Long userId,
                                    @Param("fitAnalysisId") Long fitAnalysisId,
                                    @Param("taskId") Long taskId,
                                    @Param("completed") boolean completed);

    FitAnalysisLearningTask findLearningTaskById(@Param("fitAnalysisId") Long fitAnalysisId,
                                                 @Param("taskId") Long taskId);

    /**
     * 공통 ai_usage_log 기록(공통 규약). C 도메인 사용량을 동일 스키마로 남긴다.
     */
    void insertAiUsageLog(@Param("userId") Long userId,
                          @Param("applicationCaseId") Long applicationCaseId,
                          @Param("featureType") String featureType,
                          @Param("status") String status,
                          @Param("model") String model,
                          @Param("inputTokens") int inputTokens,
                          @Param("outputTokens") int outputTokens,
                          @Param("tokenUsage") int tokenUsage,
                          @Param("creditUsed") int creditUsed,
                          @Param("errorMessage") String errorMessage);
}

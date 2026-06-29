package com.careertuner.fitanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.fitanalysis.domain.FitAnalysisGateResult;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisLearningTask;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.ai.FitConditionMatch;

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

    void insertHistory(@Param("fitAnalysisId") Long fitAnalysisId,
                       @Param("applicationCaseId") Long applicationCaseId,
                       @Param("previousScore") Integer previousScore,
                       @Param("newScore") Integer newScore,
                       @Param("diffSummary") String diffSummary);

    void insertConditionMatch(@Param("fitAnalysisId") Long fitAnalysisId,
                              @Param("row") FitConditionMatch row,
                              @Param("severity") String severity,
                              @Param("sortOrder") int sortOrder);

    void insertLearningTask(FitAnalysisLearningTask task);

    List<FitAnalysisLearningTask> findLearningTasksByFitAnalysisId(Long fitAnalysisId);

    int updateLearningTaskCompleted(@Param("userId") Long userId,
                                    @Param("fitAnalysisId") Long fitAnalysisId,
                                    @Param("taskId") Long taskId,
                                    @Param("completed") boolean completed);

    FitAnalysisLearningTask findLearningTaskById(@Param("fitAnalysisId") Long fitAnalysisId,
                                                 @Param("taskId") Long taskId);

    /** review-first evidence gate 결정 저장(fit_analysis 1건당 1행). */
    void insertGateResult(FitAnalysisGateResult gate);

    /** gate 가 사용한 evidence 버킷 스냅샷 저장(감사·재현용, 스킬명/축약만). */
    void insertEvidenceSource(@Param("fitAnalysisId") Long fitAnalysisId,
                              @Param("sourceType") String sourceType,
                              @Param("userOwned") boolean userOwned,
                              @Param("itemCount") int itemCount,
                              @Param("itemsJson") String itemsJson);

    /** 적합도 응답 safety 블록 구성을 위한 gate 결정 조회(없으면 null = R3 이전 분석). */
    FitAnalysisGateResult findGateResultByFitAnalysisId(@Param("fitAnalysisId") Long fitAnalysisId);

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

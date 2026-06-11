package com.careertuner.applicationcase.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.FitAnalysis;
import com.careertuner.applicationcase.dto.AiUsageFailureResponse;

@Mapper
public interface ApplicationCaseMapper {

    void insertApplicationCase(ApplicationCase applicationCase);

    List<ApplicationCase> findApplicationCasesByUserId(@Param("userId") Long userId,
                                                       @Param("view") String view,
                                                       @Param("includeArchived") boolean includeArchived);

    ApplicationCase findApplicationCaseByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int updateApplicationCase(ApplicationCase applicationCase);

    int markAnalysisCompleted(@Param("id") Long id, @Param("userId") Long userId);

    int markAnalysisStarted(@Param("id") Long id,
                            @Param("userId") Long userId,
                            @Param("previousStatus") String previousStatus);

    int markReadyAfterAnalysis(@Param("id") Long id,
                               @Param("userId") Long userId,
                               @Param("previousStatus") String previousStatus);

    int restoreAnalysisStatus(@Param("id") Long id,
                              @Param("userId") Long userId,
                              @Param("previousStatus") String previousStatus);

    int deleteApplicationCase(@Param("id") Long id, @Param("userId") Long userId);

    int softDeleteApplicationCase(@Param("id") Long id, @Param("userId") Long userId);

    int restoreDeletedApplicationCase(@Param("id") Long id, @Param("userId") Long userId);

    void insertStatusHistory(@Param("applicationCaseId") Long applicationCaseId,
                             @Param("changedByUserId") Long changedByUserId,
                             @Param("previousStatus") String previousStatus,
                             @Param("newStatus") String newStatus,
                             @Param("memo") String memo);

    void deleteFitAnalysesByCaseId(Long applicationCaseId);

    void insertFitAnalysis(FitAnalysis fitAnalysis);

    FitAnalysis findLatestFitAnalysisByCaseId(Long applicationCaseId);

    void insertAiUsageLog(AiUsageLog aiUsageLog);

    List<AiUsageFailureResponse> findBFailureLogsByCaseId(@Param("applicationCaseId") Long applicationCaseId,
                                                          @Param("limit") int limit);
}

package com.careertuner.applicationcase.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.FitAnalysis;

@Mapper
public interface ApplicationCaseMapper {

    void insertApplicationCase(ApplicationCase applicationCase);

    List<ApplicationCase> findApplicationCasesByUserId(Long userId);

    ApplicationCase findApplicationCaseByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int updateApplicationCase(ApplicationCase applicationCase);

    int deleteApplicationCase(@Param("id") Long id, @Param("userId") Long userId);

    void deleteFitAnalysesByCaseId(Long applicationCaseId);

    void insertFitAnalysis(FitAnalysis fitAnalysis);

    FitAnalysis findLatestFitAnalysisByCaseId(Long applicationCaseId);

    void insertAiUsageLog(AiUsageLog aiUsageLog);
}

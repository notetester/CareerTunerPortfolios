package com.careertuner.jobanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.jobanalysis.domain.JobAnalysis;

@Mapper
public interface JobAnalysisMapper {

    void deleteJobAnalysesByCaseId(@Param("applicationCaseId") Long applicationCaseId);

    void insertJobAnalysis(JobAnalysis jobAnalysis);

    JobAnalysis findLatestJobAnalysisByCaseId(@Param("applicationCaseId") Long applicationCaseId);

    JobAnalysis findJobAnalysisByIdAndCaseId(@Param("id") Long id, @Param("applicationCaseId") Long applicationCaseId);

    List<JobAnalysis> findJobAnalysisHistoryByCaseId(@Param("applicationCaseId") Long applicationCaseId);

    int updateJobAnalysisReview(JobAnalysis jobAnalysis);

    int updateAdminMemo(@Param("id") Long id, @Param("adminMemo") String adminMemo);
}

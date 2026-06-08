package com.careertuner.jobanalysis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.jobanalysis.domain.JobAnalysis;

@Mapper
public interface JobAnalysisMapper {

    void deleteJobAnalysesByCaseId(@Param("applicationCaseId") Long applicationCaseId);

    void insertJobAnalysis(JobAnalysis jobAnalysis);

    JobAnalysis findLatestJobAnalysisByCaseId(@Param("applicationCaseId") Long applicationCaseId);
}

package com.careertuner.companyanalysis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.companyanalysis.domain.CompanyAnalysis;

@Mapper
public interface CompanyAnalysisMapper {

    void deleteCompanyAnalysesByCaseId(@Param("applicationCaseId") Long applicationCaseId);

    void insertCompanyAnalysis(CompanyAnalysis companyAnalysis);

    CompanyAnalysis findLatestCompanyAnalysisByCaseId(@Param("applicationCaseId") Long applicationCaseId);
}

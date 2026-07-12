package com.careertuner.companyanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.companyanalysis.domain.CompanyAnalysis;

@Mapper
public interface CompanyAnalysisMapper {

    void insertCompanyAnalysis(CompanyAnalysis companyAnalysis);

    CompanyAnalysis findLatestCompanyAnalysisByCaseId(@Param("applicationCaseId") Long applicationCaseId);

    CompanyAnalysis findCompanyAnalysisByIdAndCaseId(@Param("id") Long id, @Param("applicationCaseId") Long applicationCaseId);

    List<CompanyAnalysis> findCompanyAnalysisHistoryByCaseId(@Param("applicationCaseId") Long applicationCaseId);

    int updateCompanyAnalysisReview(CompanyAnalysis companyAnalysis);

    int updateAdminMemo(@Param("id") Long id, @Param("adminMemo") String adminMemo);
}

package com.careertuner.admin.jobanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisRow;
import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisSearchCriteria;
import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisSummary;

@Mapper
public interface AdminJobAnalysisMapper {

    List<AdminJobAnalysisRow> findJobAnalyses(@Param("criteria") AdminJobAnalysisSearchCriteria criteria);

    AdminJobAnalysisSummary summarizeJobAnalyses(@Param("criteria") AdminJobAnalysisSearchCriteria criteria);
}

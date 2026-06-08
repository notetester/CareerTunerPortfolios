package com.careertuner.admin.analysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.analysis.dto.AdminAiUsageLogRow;
import com.careertuner.admin.analysis.dto.AdminCompanyAnalysisRow;
import com.careertuner.admin.analysis.dto.AdminJobAnalysisRow;

@Mapper
public interface AdminBAnalysisMapper {

    List<AdminJobAnalysisRow> findJobAnalyses(@Param("limit") int limit);

    List<AdminCompanyAnalysisRow> findCompanyAnalyses(@Param("limit") int limit);

    List<AdminAiUsageLogRow> findBUsageLogs(@Param("limit") int limit);
}

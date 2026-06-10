package com.careertuner.admin.companyanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisRow;

@Mapper
public interface AdminCompanyAnalysisMapper {

    List<AdminCompanyAnalysisRow> findCompanyAnalyses(@Param("limit") int limit);
}

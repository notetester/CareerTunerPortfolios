package com.careertuner.admin.jobanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisRow;

@Mapper
public interface AdminJobAnalysisMapper {

    List<AdminJobAnalysisRow> findJobAnalyses(@Param("limit") int limit);
}

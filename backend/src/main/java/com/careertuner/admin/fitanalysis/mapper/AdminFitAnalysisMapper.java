package com.careertuner.admin.fitanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;

@Mapper
public interface AdminFitAnalysisMapper {

    List<AdminFitAnalysisResult> findLatestAll();

    AdminFitAnalysisResult findById(Long id);
}

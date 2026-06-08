package com.careertuner.fitanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.fitanalysis.domain.FitAnalysisResult;

@Mapper
public interface FitAnalysisMapper {

    List<FitAnalysisResult> findLatestByUserId(Long userId);

    FitAnalysisResult findLatestByUserIdAndApplicationCaseId(@Param("userId") Long userId,
                                                             @Param("applicationCaseId") Long applicationCaseId);
}

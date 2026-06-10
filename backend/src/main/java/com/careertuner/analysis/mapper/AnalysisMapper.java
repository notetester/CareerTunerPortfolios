package com.careertuner.analysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.careertuner.analysis.domain.AnalysisSource;

@Mapper
public interface AnalysisMapper {

    List<AnalysisSource> findSourcesByUserId(Long userId);
}

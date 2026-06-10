package com.careertuner.admin.fitanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisMemo;
import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;

@Mapper
public interface AdminFitAnalysisMapper {

    List<AdminFitAnalysisResult> findAll();

    AdminFitAnalysisResult findById(Long id);

    List<AdminFitAnalysisMemo> findMemosByFitAnalysisId(Long fitAnalysisId);

    AdminFitAnalysisMemo findMemoByIdAndFitAnalysisId(@Param("id") Long id, @Param("fitAnalysisId") Long fitAnalysisId);

    void insertMemo(AdminFitAnalysisMemo memo);

    int updateMemo(AdminFitAnalysisMemo memo);

    int deleteMemo(@Param("id") Long id, @Param("fitAnalysisId") Long fitAnalysisId);
}

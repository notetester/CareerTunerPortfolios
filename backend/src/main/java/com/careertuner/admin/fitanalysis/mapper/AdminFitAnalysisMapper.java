package com.careertuner.admin.fitanalysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisMemo;
import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;
import com.careertuner.admin.fitanalysis.domain.AdminGateStatsRow;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListCriteria;

@Mapper
public interface AdminFitAnalysisMapper {

    /** 서버측 필터 + 페이징 목록 조회(최신순). */
    List<AdminFitAnalysisResult> findAll(@Param("c") AdminFitAnalysisListCriteria criteria);

    /** 위 필터에 걸리는 전체 건수(페이징 total). */
    long countAll(@Param("c") AdminFitAnalysisListCriteria criteria);

    AdminFitAnalysisResult findById(Long id);

    /** gate 통계: 전체 gate 행의 분포 관측용 최소 컬럼 조회(조인 없음, 집계는 서비스에서). */
    List<AdminGateStatsRow> findAllGateRows();

    /** gate review workflow: 처리 상태 갱신(반영 행 수 반환 — gate 결과 없는 분석이면 0). */
    int updateGateReview(@Param("fitAnalysisId") Long fitAnalysisId,
                         @Param("adminUserId") Long adminUserId,
                         @Param("reviewStatus") String reviewStatus);

    List<AdminFitAnalysisMemo> findMemosByFitAnalysisId(Long fitAnalysisId);

    AdminFitAnalysisMemo findMemoByIdAndFitAnalysisId(@Param("id") Long id, @Param("fitAnalysisId") Long fitAnalysisId);

    void insertMemo(AdminFitAnalysisMemo memo);

    int updateMemo(AdminFitAnalysisMemo memo);

    int deleteMemo(@Param("id") Long id, @Param("fitAnalysisId") Long fitAnalysisId);
}

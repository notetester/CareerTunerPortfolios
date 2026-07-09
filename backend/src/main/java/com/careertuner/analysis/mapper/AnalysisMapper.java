package com.careertuner.analysis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.careertuner.analysis.domain.AnalysisAnswerSource;
import com.careertuner.analysis.domain.AnalysisFitPointSource;
import com.careertuner.analysis.domain.AnalysisSource;
import com.careertuner.analysis.domain.AnalysisWeeklyMetricSource;

@Mapper
public interface AnalysisMapper {

    List<AnalysisSource> findSourcesByUserId(Long userId);

    /** 재분석을 포함한 전체 적합도 점수 이력. 월별/시간순 변화 집계용. */
    List<AnalysisFitPointSource> findFitScoreHistoryByUserId(Long userId);

    AnalysisWeeklyMetricSource findWeeklyMetricsByUserId(Long userId);

    /** 점수가 기록된 면접 답변(질문 유형·점수·피드백). D 테이블 읽기 전용 — 답변 공통 약점 집계용. */
    List<AnalysisAnswerSource> findAnswerSourcesByUserId(Long userId);
}

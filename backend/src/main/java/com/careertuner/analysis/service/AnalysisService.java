package com.careertuner.analysis.service;

import java.util.List;

import com.careertuner.analysis.dto.AnalysisSummaryResponse;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;

public interface AnalysisService {

    /** 조회용. 저장된 요약을 재사용하고, 입력이 바뀐 경우에만 1회 자동 재생성한다(크레딧 미차감). */
    AnalysisSummaryResponse getSummary(Long userId);

    /** 사용자가 명시적으로 요청한 재생성. 항상 AI를 실행하고 크레딧을 차감한다. */
    AnalysisSummaryResponse refreshSummary(Long userId);

    List<CareerAnalysisRunResponse> getHistory(Long userId);
}

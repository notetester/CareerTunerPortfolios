package com.careertuner.analysis.service;

import java.util.List;

import com.careertuner.analysis.dto.AnalysisSummaryResponse;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;

public interface AnalysisService {

    AnalysisSummaryResponse getSummary(Long userId);

    List<CareerAnalysisRunResponse> getHistory(Long userId);
}

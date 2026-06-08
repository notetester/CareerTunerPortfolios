package com.careertuner.analysis.service;

import com.careertuner.analysis.dto.AnalysisSummaryResponse;

public interface AnalysisService {

    AnalysisSummaryResponse getSummary(Long userId);
}

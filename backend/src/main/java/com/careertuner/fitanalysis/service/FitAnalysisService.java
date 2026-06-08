package com.careertuner.fitanalysis.service;

import java.util.List;

import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;

public interface FitAnalysisService {

    List<FitAnalysisDetailResponse> list(Long userId);

    FitAnalysisDetailResponse getByApplicationCase(Long userId, Long applicationCaseId);
}

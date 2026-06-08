package com.careertuner.admin.fitanalysis.service;

import java.util.List;

import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListItemResponse;

public interface AdminFitAnalysisService {

    List<AdminFitAnalysisListItemResponse> list();

    AdminFitAnalysisDetailResponse get(Long id);
}

package com.careertuner.admin.prompt.fitanalysis.service;

import java.util.List;

import com.careertuner.admin.prompt.fitanalysis.dto.AdminFitAnalysisPromptResponse;

public interface AdminFitAnalysisPromptService {

    List<AdminFitAnalysisPromptResponse> list();

    AdminFitAnalysisPromptResponse get(String key);
}

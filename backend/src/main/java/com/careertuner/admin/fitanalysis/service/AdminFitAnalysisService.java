package com.careertuner.admin.fitanalysis.service;

import java.util.List;

import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListItemResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoRequest;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoResponse;

public interface AdminFitAnalysisService {

    List<AdminFitAnalysisListItemResponse> list();

    AdminFitAnalysisDetailResponse get(Long id);

    List<AdminFitAnalysisMemoResponse> listMemos(Long fitAnalysisId);

    AdminFitAnalysisMemoResponse createMemo(Long fitAnalysisId, Long adminUserId, AdminFitAnalysisMemoRequest request);

    AdminFitAnalysisMemoResponse updateMemo(Long fitAnalysisId, Long memoId, AdminFitAnalysisMemoRequest request);

    void deleteMemo(Long fitAnalysisId, Long memoId);
}

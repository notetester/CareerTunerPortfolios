package com.careertuner.admin.fitanalysis.service;

import java.util.List;

import com.careertuner.admin.common.grid.PageResult;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListQuery;
import com.careertuner.admin.fitanalysis.dto.AdminGateReviewRequest;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListItemResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoRequest;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoResponse;
import com.careertuner.admin.fitanalysis.dto.AdminGateStatsResponse;

public interface AdminFitAnalysisService {

    /** 서버측 필터 + 페이징 목록. 대량 데이터에서도 페이지 단위로만 조회한다. */
    PageResult<AdminFitAnalysisListItemResponse> list(AdminFitAnalysisListQuery query);

    AdminFitAnalysisDetailResponse get(Long id);

    /** gate 통계: 운영 gate reason 분포 관측(차기 model-card 개정·alias 후보 발굴의 전제). */
    AdminGateStatsResponse getGateStats();

    /** gate review workflow: 처리 상태 갱신(+선택 메모). 갱신된 상세를 반환한다. */
    AdminFitAnalysisDetailResponse reviewGate(Long fitAnalysisId, Long adminUserId, AdminGateReviewRequest request);

    List<AdminFitAnalysisMemoResponse> listMemos(Long fitAnalysisId);

    AdminFitAnalysisMemoResponse createMemo(Long fitAnalysisId, Long adminUserId, AdminFitAnalysisMemoRequest request);

    AdminFitAnalysisMemoResponse updateMemo(Long fitAnalysisId, Long memoId, AdminFitAnalysisMemoRequest request);

    void deleteMemo(Long fitAnalysisId, Long memoId);
}

package com.careertuner.fitanalysis.service;

import java.util.List;

import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisLearningTaskResponse;

public interface FitAnalysisService {

    List<FitAnalysisDetailResponse> list(Long userId);

    FitAnalysisDetailResponse getByApplicationCase(Long userId, Long applicationCaseId);

    /**
     * 지원 건의 공고 분석 결과와 사용자 프로필을 비교해 적합도 분석을 생성/저장한다(C 담당 AI 12~15).
     * API 키가 없으면 mock, 있으면 동일 흐름으로 실제 구조화 분석이 동작한다.
     */
    FitAnalysisDetailResponse generate(Long userId, Long applicationCaseId);

    FitAnalysisLearningTaskResponse updateLearningTask(Long userId,
                                                       Long fitAnalysisId,
                                                       Long taskId,
                                                       boolean completed);
}

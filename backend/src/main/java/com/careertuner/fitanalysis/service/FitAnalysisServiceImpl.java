package com.careertuner.fitanalysis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FitAnalysisServiceImpl implements FitAnalysisService {

    private final FitAnalysisMapper fitAnalysisMapper;

    @Override
    @Transactional(readOnly = true)
    public List<FitAnalysisDetailResponse> list(Long userId) {
        return fitAnalysisMapper.findLatestByUserId(userId).stream()
                .map(FitAnalysisDetailResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FitAnalysisDetailResponse getByApplicationCase(Long userId, Long applicationCaseId) {
        FitAnalysisResult result = fitAnalysisMapper.findLatestByUserIdAndApplicationCaseId(userId, applicationCaseId);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "적합도 분석 결과를 찾을 수 없습니다.");
        }
        return FitAnalysisDetailResponse.from(result);
    }
}

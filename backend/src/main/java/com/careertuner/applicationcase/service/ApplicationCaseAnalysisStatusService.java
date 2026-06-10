package com.careertuner.applicationcase.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

@Service
public class ApplicationCaseAnalysisStatusService {

    private final ApplicationCaseMapper applicationCaseMapper;

    public ApplicationCaseAnalysisStatusService(ApplicationCaseMapper applicationCaseMapper) {
        this.applicationCaseMapper = applicationCaseMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAnalyzing(Long userId, Long applicationCaseId, String previousStatus) {
        int updated = applicationCaseMapper.markAnalysisStarted(applicationCaseId, userId, previousStatus);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "분석 상태를 시작 상태로 변경하지 못했습니다.");
        }
    }

    @Transactional
    public void markReadyAfterAnalysis(Long userId, Long applicationCaseId, String previousStatus) {
        int updated = applicationCaseMapper.markReadyAfterAnalysis(applicationCaseId, userId, previousStatus);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "분석 완료 상태로 변경하지 못했습니다.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restorePreviousStatus(Long userId, Long applicationCaseId, String previousStatus) {
        applicationCaseMapper.restoreAnalysisStatus(applicationCaseId, userId, previousStatus);
    }
}

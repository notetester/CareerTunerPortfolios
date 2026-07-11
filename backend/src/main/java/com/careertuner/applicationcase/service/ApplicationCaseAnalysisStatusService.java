package com.careertuner.applicationcase.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.mapper.ApplicationCaseExtractionMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

@Service
public class ApplicationCaseAnalysisStatusService {

    private final ApplicationCaseMapper applicationCaseMapper;
    private final ApplicationCaseExtractionMapper extractionMapper;

    public ApplicationCaseAnalysisStatusService(ApplicationCaseMapper applicationCaseMapper,
                                                ApplicationCaseExtractionMapper extractionMapper) {
        this.applicationCaseMapper = applicationCaseMapper;
        this.extractionMapper = extractionMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAnalyzing(Long userId, Long applicationCaseId, String previousStatus) {
        int updated = applicationCaseMapper.markAnalysisStarted(applicationCaseId, userId, previousStatus);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "분석 상태를 시작 상태로 변경하지 못했습니다.");
        }
    }

    /**
     * 수동(strict) 분석 전용 획득 — {@link #markAnalyzing} 에 <b>재추출과의 상호 배제</b>를 더한다.
     * 같은 TX 에서 케이스 행을 {@code FOR UPDATE} 로 잠근 뒤 활성 추출(QUEUED/RUNNING)이 있으면 거절하고,
     * 없을 때만 ANALYZING CAS 를 수행한다. strict 재추출의 획득 TX 도 같은 케이스 행을 잠그므로 두 획득이
     * 직렬화된다 — 재추출이 먼저면 여기서 활성 추출이 보여 거절되고, 분석이 먼저면 재추출 쪽이 ANALYZING 을
     * 보고 거절된다(스냅샷 검사 사이 TOCTOU 제거).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAnalyzingExclusive(Long userId, Long applicationCaseId, String previousStatus) {
        applicationCaseMapper.lockApplicationCaseById(applicationCaseId);
        if (extractionMapper.countActiveExtractionsByApplicationCaseId(applicationCaseId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "공고 재추출이 진행 중입니다. 완료된 후 다시 시도해 주세요.");
        }
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

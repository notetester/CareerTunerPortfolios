package com.careertuner.interview.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.interview.domain.InterviewAiUsageLog;
import com.careertuner.interview.mapper.InterviewMapper;

/** 면접 AI 호출 사용량을 ai_usage_log 에 기록한다. (applicationcase 패턴 동일) */
@Service
public class InterviewAiUsageLogService {

    private final InterviewMapper interviewMapper;
    private final AiUsageLogService commonUsageLogService;

    public InterviewAiUsageLogService(InterviewMapper interviewMapper,
                                      AiUsageLogService commonUsageLogService) {
        this.interviewMapper = interviewMapper;
        this.commonUsageLogService = commonUsageLogService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId, Long applicationCaseId, String featureType,
                              InterviewOpenAiClient.Usage usage) {
        if (usage == null) {
            return;
        }
        commonUsageLogService.recordSuccessValues(userId, applicationCaseId, featureType,
                usage.model(), usage.inputTokens(), usage.outputTokens(), usage.totalTokens(),
                creditUsed(usage.totalTokens()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, Long applicationCaseId, String featureType, String message) {
        interviewMapper.insertAiUsageLog(InterviewAiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("FAILED")
                .creditUsed(0)
                .errorMessage(truncate(message, 1000))
                .build());
    }

    private int creditUsed(int totalTokens) {
        if (totalTokens <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(totalTokens / 1000.0));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

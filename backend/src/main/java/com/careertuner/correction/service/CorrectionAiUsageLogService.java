package com.careertuner.correction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.correction.ai.CorrectionAiClient;

@Service
public class CorrectionAiUsageLogService {

    private final ApplicationCaseMapper applicationCaseMapper;

    public CorrectionAiUsageLogService(ApplicationCaseMapper applicationCaseMapper) {
        this.applicationCaseMapper = applicationCaseMapper;
    }

    @Transactional
    public Long recordSuccess(Long userId, Long applicationCaseId, String featureType, CorrectionAiClient.Usage usage) {
        if (usage == null) {
            throw new IllegalArgumentException("usage is required");
        }
        AiUsageLog log = AiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("SUCCESS")
                .model(usage.model())
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .tokenUsage(usage.totalTokens())
                .creditUsed(0)
                .build();
        applicationCaseMapper.insertAiUsageLog(log);
        return log.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordFailure(Long userId, Long applicationCaseId, String featureType, String message) {
        AiUsageLog log = AiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("FAILED")
                .creditUsed(0)
                .errorMessage(truncate(message, 1000))
                .build();
        applicationCaseMapper.insertAiUsageLog(log);
        return log.getId();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

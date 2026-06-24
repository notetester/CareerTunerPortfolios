package com.careertuner.applicationcase.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;

@Service
public class AiUsageLogService {

    private final ApplicationCaseMapper applicationCaseMapper;

    public AiUsageLogService(ApplicationCaseMapper applicationCaseMapper) {
        this.applicationCaseMapper = applicationCaseMapper;
    }

    @Transactional
    public void recordSuccess(Long userId, Long applicationCaseId, String featureType, OpenAiResponsesClient.Usage usage) {
        if (usage == null) {
            return;
        }
        applicationCaseMapper.insertAiUsageLog(AiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("SUCCESS")
                .model(usage.model())
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .tokenUsage(usage.totalTokens())
                .creditUsed(creditUsed(usage.totalTokens()))
                .build());
    }

    @Transactional
    public void recordLocalSuccess(Long userId, Long applicationCaseId, String featureType, OpenAiResponsesClient.Usage usage) {
        if (usage == null) {
            return;
        }
        applicationCaseMapper.insertAiUsageLog(AiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("SUCCESS")
                .model(usage.model())
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .tokenUsage(usage.totalTokens())
                .creditUsed(0)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, Long applicationCaseId, String featureType, String message) {
        recordFailure(userId, applicationCaseId, featureType, null, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, Long applicationCaseId, String featureType, String model, String message) {
        applicationCaseMapper.insertAiUsageLog(AiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("FAILED")
                .model(model)
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

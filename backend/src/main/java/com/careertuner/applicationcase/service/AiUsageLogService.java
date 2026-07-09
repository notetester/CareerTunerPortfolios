package com.careertuner.applicationcase.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.billing.service.AiChargeRequestSettlementService;

@Service
public class AiUsageLogService {

    private final ApplicationCaseMapper applicationCaseMapper;
    private final AiChargeRequestSettlementService chargeSettlementService;

    public AiUsageLogService(ApplicationCaseMapper applicationCaseMapper,
                             AiChargeRequestSettlementService chargeSettlementService) {
        this.applicationCaseMapper = applicationCaseMapper;
        this.chargeSettlementService = chargeSettlementService;
    }

    @Transactional
    public void recordSuccess(Long userId, Long applicationCaseId, String featureType, AiUsage usage) {
        if (usage == null) {
            return;
        }
        recordSuccessValues(userId, applicationCaseId, featureType, usage.model(),
                usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), creditUsed(usage.totalTokens()));
    }

    @Transactional
    public Long recordSuccessValues(Long userId,
                                    Long applicationCaseId,
                                    String featureType,
                                    String model,
                                    int inputTokens,
                                    int outputTokens,
                                    int tokenUsage,
                                    int estimatedCreditUsed) {
        AiUsageLog log = AiUsageLog.builder()
                .userId(userId)
                .applicationCaseId(applicationCaseId)
                .featureType(featureType)
                .status("SUCCESS")
                .model(model)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .tokenUsage(tokenUsage)
                .creditUsed(estimatedCreditUsed)
                .build();
        applicationCaseMapper.insertAiUsageLog(log);
        chargeSettlementService.settleFirstAcknowledgedUsage(userId, featureType, log.getId(), tokenUsage);
        return log.getId();
    }

    @Transactional
    public void recordLocalSuccess(Long userId, Long applicationCaseId, String featureType, OpenAiResponsesClient.Usage usage) {
        if (usage == null) {
            return;
        }
        recordSuccessValues(userId, applicationCaseId, featureType, usage.model(),
                usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), 0);
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

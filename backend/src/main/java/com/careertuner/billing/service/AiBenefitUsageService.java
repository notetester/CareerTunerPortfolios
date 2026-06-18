package com.careertuner.billing.service;

import com.careertuner.billing.dto.BenefitConsumeResult;

public interface AiBenefitUsageService {

    BenefitConsumeResult consumeByFeature(Long userId,
                                          String featureType,
                                          String refType,
                                          Long refId,
                                          Long aiUsageLogId,
                                          String reason);
}

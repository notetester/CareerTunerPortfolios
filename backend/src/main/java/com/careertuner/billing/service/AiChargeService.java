package com.careertuner.billing.service;

import com.careertuner.billing.dto.AiChargeCommand;
import com.careertuner.billing.dto.AiChargeResult;

public interface AiChargeService {

    AiChargeResult charge(AiChargeCommand command);
}

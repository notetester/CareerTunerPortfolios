package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiFeatureBenefitPolicy {

    private Long id;
    private String featureType;
    private String benefitCode;
    private String chargeUnit;
    private boolean includedInTicket;
    private int defaultCreditCost;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

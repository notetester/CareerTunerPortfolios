package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundRequest {
    private Long id;
    private Long paymentId;
    private Long userId;
    private String status;
    private String reasonCode;
    private String reasonText;
    private String eligibilityResult;
    private boolean creditUsed;
    private boolean benefitUsed;
    private int refundAmount;
    private String decisionBasisJson;
    private Long reviewedBy;
    private String reviewedReason;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime updatedAt;
}

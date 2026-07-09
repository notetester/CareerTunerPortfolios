package com.careertuner.billing.dto;

import java.time.LocalDateTime;

public record RefundRequestResponse(
        Long id,
        Long paymentId,
        Long userId,
        String userEmail,
        String userName,
        String orderId,
        String productType,
        String productCode,
        String plan,
        int paymentAmount,
        LocalDateTime paidAt,
        String paymentStatus,
        String status,
        String reasonCode,
        String reasonText,
        String eligibilityResult,
        boolean creditUsed,
        boolean benefitUsed,
        int refundAmount,
        String decisionBasisJson,
        Long reviewedBy,
        String reviewedReason,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt) {
}

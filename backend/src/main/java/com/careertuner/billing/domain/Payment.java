package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 결제 내역(payment). 개발 단계에서는 외부 PG 없이 즉시 성공 처리한다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    private Long id;
    private Long userId;
    private String provider;
    private String productCode;
    private String orderId;
    private String paymentKey;
    private Integer amount;
    private String plan;
    private Integer creditAmount;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}

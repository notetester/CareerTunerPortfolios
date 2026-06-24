package com.careertuner.payment.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** payment 테이블에 저장되는 결제 건. 크레딧 단건 결제에서는 plan을 비워 둔다. */
@Getter
@Setter
public class Payment {

    private Long id;
    private Long userId;
    private String provider;
    private String productType;
    private String productCode;
    private String orderId;
    private String paymentKey;
    private int amount;
    private String plan;
    private Integer creditAmount;
    private String policySnapshotJson;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}

package com.careertuner.billing.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 관리자 결제 목록 한 줄(payment + users 조인). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentRow {

    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private String provider;
    private String productCode;
    private Integer amount;
    private String plan;
    private Integer creditAmount;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}

package com.careertuner.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 관리자 결제 요약(건수·매출). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentSummary {

    private int totalCount;
    private int paidCount;
    private long totalRevenue;
}

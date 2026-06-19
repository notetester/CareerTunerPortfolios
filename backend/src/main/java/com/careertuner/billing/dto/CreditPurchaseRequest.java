package com.careertuner.billing.dto;

import jakarta.validation.constraints.NotBlank;

/** 크레딧 충전 상품 구매. */
public record CreditPurchaseRequest(
        @NotBlank(message = "충전 상품을 선택해 주세요.")
        String productCode
) {}

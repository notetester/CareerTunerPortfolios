package com.careertuner.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 크레딧 충전 상품(credit_product). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditProduct {

    private Long id;
    private String code;
    private String name;
    private Integer price;
    private Integer creditAmount;
    private String description;
    private String badge;
    private boolean enabled;
    private Integer sortOrder;
}

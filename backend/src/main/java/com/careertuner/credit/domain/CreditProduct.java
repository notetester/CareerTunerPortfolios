package com.careertuner.credit.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** credit_product 테이블의 크레딧 충전 상품. 가격과 지급 크레딧은 DB 값이 기준이다. */
@Getter
@Setter
public class CreditProduct {

    private Long id;
    private String code;
    private String name;
    private int price;
    private int creditAmount;
    private String description;
    private String badge;
    private boolean enabled;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

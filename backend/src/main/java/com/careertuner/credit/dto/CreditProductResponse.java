package com.careertuner.credit.dto;

import com.careertuner.credit.domain.CreditProduct;

/** 사용자 화면에 노출할 크레딧 상품 정보. */
public record CreditProductResponse(
        String code,
        String name,
        int price,
        int creditAmount,
        String description,
        String badge,
        int sortOrder
) {

    public static CreditProductResponse from(CreditProduct product) {
        return new CreditProductResponse(
                product.getCode(),
                product.getName(),
                product.getPrice(),
                product.getCreditAmount(),
                product.getDescription(),
                product.getBadge(),
                product.getSortOrder());
    }
}

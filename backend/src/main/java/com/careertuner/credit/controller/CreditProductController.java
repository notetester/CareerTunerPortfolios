package com.careertuner.credit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.credit.dto.CreditProductResponse;
import com.careertuner.credit.service.CreditProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/credit-products")
@RequiredArgsConstructor
public class CreditProductController {

    private final CreditProductService creditProductService;

    /** 결제 화면의 크레딧 상품 목록. 실제 가격/수량은 DB의 credit_product 값을 따른다. */
    @GetMapping
    public ApiResponse<List<CreditProductResponse>> list() {
        return ApiResponse.ok(creditProductService.listEnabledProducts());
    }
}

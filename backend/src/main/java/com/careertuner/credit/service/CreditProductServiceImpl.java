package com.careertuner.credit.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.credit.dto.CreditProductResponse;
import com.careertuner.credit.mapper.CreditProductMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreditProductServiceImpl implements CreditProductService {

    private final CreditProductMapper creditProductMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CreditProductResponse> listEnabledProducts() {
        // 상품 가격과 지급 크레딧은 하드코딩하지 않고 DB 기준으로 내려준다.
        return creditProductMapper.findEnabledProducts().stream()
                .map(CreditProductResponse::from)
                .toList();
    }
}

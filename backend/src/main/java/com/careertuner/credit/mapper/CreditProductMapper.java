package com.careertuner.credit.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.careertuner.credit.domain.CreditProduct;

@Mapper
public interface CreditProductMapper {

    /** 사용자에게 노출 가능한 충전 상품만 정렬 순서대로 조회한다. */
    List<CreditProduct> findEnabledProducts();
}

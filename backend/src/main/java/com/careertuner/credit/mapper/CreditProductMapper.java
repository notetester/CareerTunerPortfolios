package com.careertuner.credit.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.credit.domain.CreditProduct;

@Mapper
public interface CreditProductMapper {

    /** 사용자에게 노출 가능한 충전 상품만 정렬 순서대로 조회한다. */
    List<CreditProduct> findEnabledProducts();

    /** 결제 준비 단계에서 상품 코드를 기준으로 활성 충전 상품을 조회한다. */
    CreditProduct findEnabledProductByCode(@Param("code") String code);
}

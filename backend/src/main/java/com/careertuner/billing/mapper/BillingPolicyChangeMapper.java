package com.careertuner.billing.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.billing.domain.BillingPolicyChange;

@Mapper
public interface BillingPolicyChangeMapper {

    void insert(BillingPolicyChange change);

    BillingPolicyChange findById(@Param("id") Long id);

    List<BillingPolicyChange> findRecent(@Param("limit") int limit);

    BillingPolicyChange findLatestEffective(@Param("targetType") String targetType,
                                            @Param("targetCode") String targetCode,
                                            @Param("asOf") LocalDateTime asOf);

    int cancelScheduled(@Param("id") Long id,
                        @Param("canceledBy") Long canceledBy);
}

package com.careertuner.loginrisk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.loginrisk.domain.LoginRiskPolicy;

@Mapper
public interface LoginRiskPolicyMapper {

    LoginRiskPolicy findPolicy();

    int updatePolicy(@Param("enabled") boolean enabled,
                     @Param("maxFailedCount") int maxFailedCount,
                     @Param("lockMinutes") int lockMinutes,
                     @Param("updatedBy") Long updatedBy);
}

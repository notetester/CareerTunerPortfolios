package com.careertuner.admin.policy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.policy.dto.AdminSystemPolicyRow;

@Mapper
public interface AdminPolicyMapper {

    List<AdminSystemPolicyRow> findAll();

    AdminSystemPolicyRow findByCode(@Param("policyCode") String policyCode);

    int updatePolicy(@Param("policyCode") String policyCode,
                     @Param("configJson") String configJson,
                     @Param("scheduleType") String scheduleType,
                     @Param("active") Boolean active,
                     @Param("updatedBy") Long updatedBy);

    int updateLastRun(@Param("policyCode") String policyCode,
                      @Param("status") String status,
                      @Param("message") String message,
                      @Param("updatedBy") Long updatedBy);
}

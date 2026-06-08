package com.careertuner.admin.aiusage.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.aiusage.dto.AdminAiUsageLogRow;

@Mapper
public interface AdminAiUsageMapper {

    List<AdminAiUsageLogRow> findBUsageLogs(@Param("limit") int limit);
}

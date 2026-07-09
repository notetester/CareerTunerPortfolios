package com.careertuner.admin.log.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.log.dto.AdminAiUsageLogEntry;

@Mapper
public interface AdminLogMapper {

    List<AdminAiUsageLogEntry> findRecentAiUsage(@Param("status") String status, @Param("limit") int limit);
}

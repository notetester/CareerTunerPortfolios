package com.careertuner.admin.aiusage.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.aiusage.dto.AdminAiUsageLogRow;
import com.careertuner.admin.aiusage.dto.AdminAiUsageSearchCriteria;
import com.careertuner.admin.aiusage.dto.AdminAiUsageSummary;

@Mapper
public interface AdminAiUsageMapper {

    List<AdminAiUsageLogRow> findBUsageLogs(@Param("criteria") AdminAiUsageSearchCriteria criteria);

    AdminAiUsageSummary summarizeBUsageLogs(@Param("criteria") AdminAiUsageSearchCriteria criteria);

    List<AdminAiUsageLogRow> findBUsageLogsByCaseId(@Param("applicationCaseId") Long applicationCaseId,
                                                    @Param("limit") int limit);
}

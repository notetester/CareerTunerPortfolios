package com.careertuner.admin.log.service;

import java.util.List;

import com.careertuner.admin.log.dto.AdminAiUsageLogEntry;

public interface AdminLogService {

    List<AdminAiUsageLogEntry> getRecentAiUsage(String status, int limit);
}

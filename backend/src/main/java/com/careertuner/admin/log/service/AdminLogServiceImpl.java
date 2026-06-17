package com.careertuner.admin.log.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.log.dto.AdminAiUsageLogEntry;
import com.careertuner.admin.log.mapper.AdminLogMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLogServiceImpl implements AdminLogService {

    private final AdminLogMapper adminLogMapper;

    @Override
    public List<AdminAiUsageLogEntry> getRecentAiUsage(String status, int limit) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        int capped = limit <= 0 ? 100 : Math.min(limit, 500);
        return adminLogMapper.findRecentAiUsage(normalized, capped);
    }
}

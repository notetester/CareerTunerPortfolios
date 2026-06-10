package com.careertuner.admin.aiusage.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.aiusage.dto.AdminAiUsageLogRow;
import com.careertuner.admin.aiusage.mapper.AdminAiUsageMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAiUsageService {

    private final AdminAiUsageMapper mapper;

    @Transactional(readOnly = true)
    public List<AdminAiUsageLogRow> bUsageLogs(AuthUser authUser, String featureType, String status, int limit) {
        requireAdmin(authUser);
        return mapper.findBUsageLogs(blankToNull(featureType), blankToNull(status), normalizeLimit(limit));
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

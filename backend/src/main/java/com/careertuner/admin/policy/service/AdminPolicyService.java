package com.careertuner.admin.policy.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.admin.policy.dto.AdminPolicyRunResult;
import com.careertuner.admin.policy.dto.AdminPolicyUpdateRequest;
import com.careertuner.admin.policy.dto.AdminSystemPolicyRow;
import com.careertuner.admin.policy.mapper.AdminPolicyMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminPolicyService {

    private final AdminPolicyMapper mapper;
    private final AdminActionLogService actionLogService;

    @Transactional(readOnly = true)
    public List<AdminSystemPolicyRow> policies(AuthUser authUser) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.findAll();
    }

    @Transactional
    public AdminSystemPolicyRow update(AuthUser authUser, String policyCode, AdminPolicyUpdateRequest request) {
        AdminAccess.requireSuperAdmin(authUser);
        String code = normalizeCode(policyCode);
        AdminSystemPolicyRow before = requirePolicy(code);
        int updated = mapper.updatePolicy(code, request.configJson().trim(), normalizeSchedule(request.scheduleType()),
                request.active(), authUser.id());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 정책을 찾을 수 없습니다.");
        }
        actionLogService.record(authUser, null, "SYSTEM_POLICY_UPDATED", "ADMIN_POLICY",
                policySnapshot(before),
                "{\"policyCode\":\"%s\",\"scheduleType\":\"%s\",\"active\":%s}".formatted(
                        code, normalizeSchedule(request.scheduleType()), request.active()),
                request.reason());
        return requirePolicy(code);
    }

    @Transactional
    public AdminPolicyRunResult run(AuthUser authUser, String policyCode, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        String code = normalizeCode(policyCode);
        requirePolicy(code);
        String message = "정책 수동 실행 요청이 기록되었습니다. 실제 배치 작업은 별도 스케줄러가 처리합니다.";
        mapper.updateLastRun(code, "REQUESTED", message, authUser.id());
        actionLogService.record(authUser, null, "SYSTEM_POLICY_RUN_REQUESTED", "ADMIN_POLICY",
                null, "{\"policyCode\":\"%s\",\"status\":\"REQUESTED\"}".formatted(code), reason);
        return new AdminPolicyRunResult(code, "REQUESTED", message, LocalDateTime.now());
    }

    private AdminSystemPolicyRow requirePolicy(String policyCode) {
        AdminSystemPolicyRow row = mapper.findByCode(policyCode);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 정책을 찾을 수 없습니다.");
        }
        return row;
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정책 코드가 필요합니다.");
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String normalizeSchedule(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "실행 주기가 필요합니다.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String policySnapshot(AdminSystemPolicyRow row) {
        return "{\"policyCode\":\"%s\",\"scheduleType\":\"%s\",\"active\":%s}".formatted(
                row.policyCode(), row.scheduleType(), row.active());
    }
}

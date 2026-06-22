package com.careertuner.billing.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.billing.domain.BillingPolicyChange;
import com.careertuner.billing.dto.AdminBillingPolicyChangeRequest;
import com.careertuner.billing.dto.AdminBillingPolicyChangeResponse;
import com.careertuner.billing.mapper.BillingPolicyChangeMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class BillingPolicyChangeService {

    private static final String STATUS_SCHEDULED = "SCHEDULED";
    private static final Set<String> TARGET_TYPES = Set.of(
            BillingPolicyService.TARGET_SUBSCRIPTION_PLAN,
            BillingPolicyService.TARGET_CREDIT_PRODUCT,
            BillingPolicyService.TARGET_SUBSCRIPTION_BENEFIT_POLICY,
            BillingPolicyService.TARGET_AI_FEATURE_BENEFIT_POLICY);
    private static final Set<String> APPLY_MODES = Set.of(
            "NEXT_SUBSCRIPTION_PERIOD",
            "NEW_PURCHASE_FROM_EFFECTIVE_AT",
            "NEXT_BENEFIT_PERIOD");

    private final BillingPolicyChangeMapper mapper;
    private final BillingPolicyService policyService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AdminBillingPolicyChangeResponse> list(AuthUser authUser) {
        requireAdmin(authUser);
        return mapper.findRecent(200).stream()
                .map(AdminBillingPolicyChangeResponse::from)
                .toList();
    }

    @Transactional
    public AdminBillingPolicyChangeResponse create(AuthUser authUser, AdminBillingPolicyChangeRequest request) {
        requireAdmin(authUser);
        if (request == null || request.nextSnapshot() == null || request.nextSnapshot().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경할 정책 값이 필요합니다.");
        }
        String targetType = normalize(request.targetType(), TARGET_TYPES, "정책 대상이 올바르지 않습니다.");
        String targetCode = policyService.normalizeTargetCode(targetType, request.nextSnapshot());
        String applyMode = normalize(defaultApplyMode(targetType, request.applyMode()), APPLY_MODES, "적용 방식이 올바르지 않습니다.");
        LocalDateTime effectiveFrom = request.effectiveFrom() == null ? LocalDateTime.now() : request.effectiveFrom();

        BillingPolicyChange change = new BillingPolicyChange();
        change.setTargetType(targetType);
        change.setTargetCode(targetCode);
        change.setCurrentSnapshotJson(policyService.currentSnapshotJson(targetType, targetCode));
        change.setNextSnapshotJson(writeJson(request.nextSnapshot()));
        change.setEffectiveFrom(effectiveFrom);
        change.setApplyMode(applyMode);
        change.setStatus(STATUS_SCHEDULED);
        change.setCreatedBy(authUser.id());
        mapper.insert(change);
        return AdminBillingPolicyChangeResponse.from(change);
    }

    @Transactional
    public AdminBillingPolicyChangeResponse cancel(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        BillingPolicyChange existing = mapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "예약 변경을 찾을 수 없습니다.");
        }
        int updated = mapper.cancelScheduled(id, authUser.id());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "취소할 수 없는 예약 상태입니다.");
        }
        return AdminBillingPolicyChangeResponse.from(mapper.findById(id));
    }

    private String defaultApplyMode(String targetType, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        if (BillingPolicyService.TARGET_CREDIT_PRODUCT.equals(targetType)) {
            return "NEW_PURCHASE_FROM_EFFECTIVE_AT";
        }
        if (BillingPolicyService.TARGET_SUBSCRIPTION_PLAN.equals(targetType)) {
            return "NEXT_SUBSCRIPTION_PERIOD";
        }
        return "NEXT_BENEFIT_PERIOD";
    }

    private String normalize(String value, Set<String> allowed, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return normalized;
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정책 값 형식이 올바르지 않습니다.");
        }
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}

package com.careertuner.billing.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.billing.domain.RefundRequest;
import com.careertuner.billing.dto.RefundRequestCreateRequest;
import com.careertuner.billing.dto.RefundRequestResponse;
import com.careertuner.billing.dto.RefundReviewRequest;
import com.careertuner.billing.mapper.RefundRequestMapper;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RefundRequestService {
    private static final String STATUS_REQUESTED = "REQUESTED";
    private static final String PRODUCT_SUBSCRIPTION = "SUBSCRIPTION";
    private static final Set<String> REASON_CODES = Set.of(
            "CHANGE_OF_MIND", "DUPLICATE_PAYMENT", "SYSTEM_ERROR", "LEGAL_REQUIREMENT", "OTHER");
    private static final Set<String> EXCEPTION_CODES = Set.of(
            "DUPLICATE_PAYMENT", "SYSTEM_ERROR", "LEGAL_REQUIREMENT");

    private final RefundRequestMapper mapper;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Transactional
    public RefundRequestResponse create(Long userId, RefundRequestCreateRequest request) {
        requireUser(userId);
        Payment payment = mapper.findOwnedPayment(request.paymentId(), userId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "환불을 신청할 결제 건을 찾을 수 없습니다.");
        }
        if (!"PAID".equalsIgnoreCase(payment.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결제 완료 상태에서만 환불을 신청할 수 있습니다.");
        }

        String reasonCode = normalizeReasonCode(request.reasonCode());
        String reasonText = trimToNull(request.reasonText());
        if ("OTHER".equals(reasonCode) && reasonText == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "기타 사유의 상세 내용을 입력해 주세요.");
        }

        LocalDateTime paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
        if (paidAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결제 시각을 확인할 수 없습니다.");
        }
        boolean creditUsed = mapper.existsCreditUsageAfter(userId, paidAt);
        boolean benefitUsed = PRODUCT_SUBSCRIPTION.equalsIgnoreCase(payment.getProductType())
                && mapper.existsBenefitUsageAfter(userId, paidAt);
        PolicyRule rule = readPolicyRule(payment.getPolicySnapshotJson());
        boolean expired = LocalDateTime.now().isAfter(paidAt.plusDays(rule.withdrawalDays()));
        boolean used = creditUsed || benefitUsed;

        String eligibility = eligibility(reasonCode, expired, used, rule.usedPolicy(), rule.valid());
        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("policyId", rule.policyId());
        basis.put("policyVersion", rule.policyVersion());
        basis.put("policySnapshotValid", rule.valid());
        basis.put("withdrawalDays", rule.withdrawalDays());
        basis.put("usedPolicy", rule.usedPolicy());
        basis.put("productType", payment.getProductType());
        basis.put("paidAt", paidAt);
        basis.put("elapsedDays", Math.max(0, Duration.between(paidAt, LocalDateTime.now()).toDays()));
        basis.put("creditUsed", creditUsed);
        basis.put("benefitUsed", benefitUsed);
        basis.put("reasonCode", reasonCode);
        basis.put("result", eligibility);

        RefundRequest entity = new RefundRequest();
        entity.setPaymentId(payment.getId());
        entity.setUserId(userId);
        entity.setStatus(STATUS_REQUESTED);
        entity.setReasonCode(reasonCode);
        entity.setReasonText(reasonText);
        entity.setEligibilityResult(eligibility);
        entity.setCreditUsed(creditUsed);
        entity.setBenefitUsed(benefitUsed);
        entity.setRefundAmount(payment.getAmount());
        entity.setDecisionBasisJson(writeJson(basis));
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 환불을 신청한 결제 건입니다.");
        }
        return requireResponse(entity.getId());
    }

    @Transactional(readOnly = true)
    public List<RefundRequestResponse> listMine(Long userId) {
        requireUser(userId);
        return mapper.findResponsesByUser(userId);
    }

    @Transactional(readOnly = true)
    public List<RefundRequestResponse> listAdmin(AuthUser authUser, String status) {
        requireAdmin(authUser);
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !Set.of("REQUESTED", "APPROVED", "REJECTED").contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 환불 상태입니다.");
        }
        return mapper.findAdminResponses(normalized);
    }

    @Transactional
    public RefundRequestResponse approve(AuthUser authUser, Long id, RefundReviewRequest request) {
        requireAdmin(authUser);
        RefundRequestResponse current = requireResponse(id);
        requireRequested(current);
        if (mapper.markPaymentRefunded(current.paymentId()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "결제 상태가 변경되어 환불 승인할 수 없습니다.");
        }
        if (mapper.approve(id, authUser.id(), request.reviewedReason().trim()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 환불 신청입니다.");
        }
        notifyResult(current.userId(), id, "환불 신청이 승인되었습니다",
                "주문번호 %s 건이 전액 환불 처리되었습니다.".formatted(current.orderId()));
        return requireResponse(id);
    }

    @Transactional
    public RefundRequestResponse reject(AuthUser authUser, Long id, RefundReviewRequest request) {
        requireAdmin(authUser);
        RefundRequestResponse current = requireResponse(id);
        requireRequested(current);
        if (mapper.reject(id, authUser.id(), request.reviewedReason().trim()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 환불 신청입니다.");
        }
        notifyResult(current.userId(), id, "환불 신청 검토가 완료되었습니다",
                "주문번호 %s 건은 환불 불가로 처리되었습니다.".formatted(current.orderId()));
        return requireResponse(id);
    }

    private String eligibility(String reasonCode, boolean expired, boolean used, String usedPolicy, boolean policyValid) {
        if (EXCEPTION_CODES.contains(reasonCode)) return "REVIEW_REQUIRED";
        if (!policyValid) return "REVIEW_REQUIRED";
        if (expired) return "INELIGIBLE";
        if (used) return "NO_REFUND".equals(usedPolicy) ? "INELIGIBLE" : "REVIEW_REQUIRED";
        return "ELIGIBLE";
    }

    private PolicyRule readPolicyRule(String snapshotJson) {
        int withdrawalDays = 7;
        String usedPolicy = "NO_REFUND";
        Long policyId = null;
        int policyVersion = 0;
        boolean valid = false;
        if (snapshotJson != null && !snapshotJson.isBlank()) {
            try {
                JsonNode refund = objectMapper.readTree(snapshotJson).path("refundPolicy");
                policyId = refund.path("id").isNumber() ? refund.path("id").longValue() : null;
                policyVersion = refund.path("version").asInt(0);
                JsonNode rules = refund.path("rules");
                if (!refund.isMissingNode() && rules.isObject()) {
                    JsonNode daysNode = rules.path("withdrawalDays");
                    JsonNode usedNode = rules.path("usedPolicy");
                    withdrawalDays = Math.max(0, daysNode.isNumber() ? daysNode.intValue() : 7);
                    usedPolicy = usedNode.isTextual() ? usedNode.asText().toUpperCase(Locale.ROOT) : "NO_REFUND";
                    valid = true;
                }
            } catch (JacksonException ignored) {
                // 이전 결제의 스냅샷이 없거나 손상된 경우 보수적인 기본 규칙으로 관리자 검토 근거를 남긴다.
            }
        }
        return new PolicyRule(policyId, policyVersion, withdrawalDays, usedPolicy, valid);
    }

    private RefundRequestResponse requireResponse(Long id) {
        RefundRequestResponse response = mapper.findResponseById(id);
        if (response == null) throw new BusinessException(ErrorCode.NOT_FOUND, "환불 신청을 찾을 수 없습니다.");
        return response;
    }

    private void requireRequested(RefundRequestResponse response) {
        if (!STATUS_REQUESTED.equals(response.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 환불 신청입니다.");
        }
    }

    private String normalizeReasonCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!REASON_CODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 환불 사유입니다.");
        }
        return normalized;
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "환불 판정 근거를 저장하지 못했습니다.");
        }
    }

    private void notifyResult(Long userId, Long refundId, String title, String message) {
        notificationService.notify(Notification.builder()
                .userId(userId).type("REFUND_RESULT").targetType("REFUND_REQUEST").targetId(refundId)
                .title(title).message(message).link("/billing?tab=history").build());
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인 후 환불을 신청할 수 있습니다.");
    }

    private void requireAdmin(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PolicyRule(Long policyId, int policyVersion, int withdrawalDays, String usedPolicy, boolean valid) {
    }
}

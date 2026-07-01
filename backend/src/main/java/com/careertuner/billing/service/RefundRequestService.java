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
import com.careertuner.billing.domain.RefundPolicy;
import com.careertuner.billing.domain.BenefitTransaction;
import com.careertuner.billing.domain.UserBenefitBalance;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.dto.RefundEligibilityRequest;
import com.careertuner.billing.dto.RefundEligibilityResponse;
import com.careertuner.billing.dto.RefundRequestCreateRequest;
import com.careertuner.billing.dto.RefundRequestResponse;
import com.careertuner.billing.dto.RefundReviewRequest;
import com.careertuner.billing.mapper.RefundRequestMapper;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.credit.domain.CreditTransaction;
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
    private final RefundPolicyService refundPolicyService;

    @Transactional
    public RefundRequestResponse create(Long userId, RefundRequestCreateRequest request) {
        requireUser(userId);
        String reasonCode = normalizeReasonCode(request.reasonCode());
        String reasonText = trimToNull(request.reasonText());
        if ("OTHER".equals(reasonCode) && reasonText == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "기타 사유의 상세 내용을 입력해 주세요.");
        }
        Evaluation evaluation = evaluate(userId, request.paymentId(), reasonCode);
        if ("INELIGIBLE".equals(evaluation.result())) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED, evaluation.message());
        }

        RefundRequest entity = new RefundRequest();
        entity.setPaymentId(evaluation.payment().getId());
        entity.setUserId(userId);
        entity.setStatus(STATUS_REQUESTED);
        entity.setReasonCode(reasonCode);
        entity.setReasonText(reasonText);
        entity.setEligibilityResult(evaluation.result());
        entity.setCreditUsed(evaluation.creditUsed());
        entity.setBenefitUsed(evaluation.benefitUsed());
        entity.setRefundAmount(evaluation.payment().getAmount());
        entity.setDecisionBasisJson(writeJson(evaluation.basis()));
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 환불을 신청한 결제 건입니다.");
        }
        return requireResponse(entity.getId());
    }

    @Transactional(readOnly = true)
    public RefundEligibilityResponse preview(Long userId, RefundEligibilityRequest request) {
        requireUser(userId);
        Evaluation evaluation = evaluate(userId, request.paymentId(), normalizeReasonCode(request.reasonCode()));
        PolicyRule rule = evaluation.rule();
        return new RefundEligibilityResponse(
                evaluation.payment().getId(), evaluation.result(), evaluation.decisionCode(), evaluation.message(),
                evaluation.creditUsed(), evaluation.benefitUsed(), evaluation.payment().getAmount(),
                rule.policyId(), rule.policyVersion(), rule.title(), rule.summary(), rule.withdrawalDays());
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
        Payment payment = mapper.findOwnedPayment(current.paymentId(), current.userId());
        if (payment == null || !"PAID".equalsIgnoreCase(payment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "결제 상태가 변경되어 환불 승인할 수 없습니다.");
        }
        if ("CREDIT".equalsIgnoreCase(payment.getProductType())) {
            reclaimPurchasedCredits(payment);
        } else if (PRODUCT_SUBSCRIPTION.equalsIgnoreCase(payment.getProductType())) {
            revokeRefundedSubscription(payment);
        }
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

    private void reclaimPurchasedCredits(Payment payment) {
        Integer creditAmount = payment.getCreditAmount();
        if (creditAmount == null || creditAmount <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "결제 건의 지급 크레딧 수량을 확인할 수 없습니다.");
        }
        LocalDateTime paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
        if (paidAt == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "결제 시각을 확인할 수 없어 환불 승인할 수 없습니다.");
        }
        if (mapper.existsCreditUsageAfter(payment.getUserId(), paidAt)) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED,
                    "결제 이후 크레딧 사용 이력이 있어 환불 승인할 수 없습니다.");
        }
        if (mapper.deductUserCreditForRefund(payment.getUserId(), creditAmount) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "회수할 크레딧 잔액이 부족하여 환불 승인할 수 없습니다.");
        }
        Integer balanceAfter = mapper.findUserCredit(payment.getUserId());
        if (balanceAfter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "크레딧을 회수할 사용자를 찾을 수 없습니다.");
        }
        mapper.insertCreditTransaction(CreditTransaction.builder()
                .userId(payment.getUserId())
                .type("REFUND")
                .amount(-creditAmount)
                .balanceAfter(balanceAfter)
                .reason("주문번호 %s 환불 회수".formatted(payment.getOrderId()))
                .build());
    }

    private void revokeRefundedSubscription(Payment payment) {
        LocalDateTime paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
        if (paidAt == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "결제 시각을 확인할 수 없어 환불 승인할 수 없습니다.");
        }
        if (mapper.existsBenefitUsageAfter(payment.getUserId(), paidAt)) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED,
                    "결제 이후 구독 사용권 사용 이력이 있어 환불 승인할 수 없습니다.");
        }

        String planCode = payment.getPlan() != null && !payment.getPlan().isBlank()
                ? payment.getPlan()
                : payment.getProductCode();
        UserSubscription subscription = mapper.findSubscriptionForRefund(
                payment.getId(), payment.getUserId(), planCode, paidAt);
        if (subscription == null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "환불할 결제와 연결된 구독 정보를 찾을 수 없습니다.");
        }
        if (subscription.getPaymentId() == null
                && mapper.attachPaymentToSubscription(subscription.getId(), payment.getId()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "구독 결제 연결 상태가 변경되었습니다.");
        }

        List<UserBenefitBalance> balances = mapper.findBenefitBalancesForRefund(
                payment.getUserId(), subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());
        if (balances.stream().anyMatch(balance -> balance.getUsedQuantity() > 0)) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED,
                    "구독 사용권 사용 이력이 있어 환불 승인할 수 없습니다.");
        }
        if (mapper.markSubscriptionRefunded(subscription.getId()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "구독 상태가 변경되어 환불 승인할 수 없습니다.");
        }

        for (UserBenefitBalance balance : balances) {
            int remaining = balance.getRemainingQuantity();
            if (remaining <= 0) continue;
            if (mapper.revokeBenefitBalanceIfUnused(balance.getId()) == 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "사용권 상태가 변경되어 환불 승인할 수 없습니다.");
            }
            mapper.insertBenefitTransaction(BenefitTransaction.builder()
                    .userId(payment.getUserId())
                    .benefitCode(balance.getBenefitCode())
                    .transactionType("REFUND_REVOKE")
                    .amount(-remaining)
                    .balanceAfter(0)
                    .refType("BENEFIT_BALANCE")
                    .refId(balance.getId())
                    .reason("주문번호 %s 구독 환불 회수".formatted(payment.getOrderId()))
                    .build());
        }
        mapper.resetUserPlanAfterSubscriptionRefund(
                payment.getUserId(), subscription.getId(), LocalDateTime.now());
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

    private Evaluation evaluate(Long userId, Long paymentId, String reasonCode) {
        Payment payment = mapper.findOwnedPayment(paymentId, userId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "환불을 신청할 결제 건을 찾을 수 없습니다.");
        }
        if (!"PAID".equalsIgnoreCase(payment.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결제 완료 상태에서만 환불을 신청할 수 있습니다.");
        }
        LocalDateTime paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
        if (paidAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결제 시각을 확인할 수 없습니다.");
        }
        boolean creditUsed = mapper.existsCreditUsageAfter(userId, paidAt);
        boolean benefitUsed = PRODUCT_SUBSCRIPTION.equalsIgnoreCase(payment.getProductType())
                && mapper.existsBenefitUsageAfter(userId, paidAt);
        PolicyRule rule = readPolicyRule(payment.getPolicySnapshotJson());
        if (!rule.valid()) {
            rule = readCurrentPolicyRule();
        }
        long elapsedDays = Math.max(0, Duration.between(paidAt, LocalDateTime.now()).toDays());
        boolean expired = LocalDateTime.now().isAfter(paidAt.plusDays(rule.withdrawalDays()));

        String result;
        String decisionCode;
        String message;
        if (EXCEPTION_CODES.contains(reasonCode)) {
            result = "REVIEW_REQUIRED";
            decisionCode = "EXCEPTION_REVIEW";
            message = "예외 사유로 접수되며 관리자가 결제 및 사용 이력을 검토합니다.";
        } else if (!rule.valid()) {
            result = "REVIEW_REQUIRED";
            decisionCode = "POLICY_SNAPSHOT_REVIEW";
            message = "결제 당시 정책을 자동 판정할 수 없어 관리자가 직접 검토합니다.";
        } else if (expired) {
            result = "INELIGIBLE";
            decisionCode = "WITHDRAWAL_PERIOD_EXPIRED";
            message = "결제일로부터 %d일이 지나 정책상 환불 신청 기간(%d일)이 종료되었습니다."
                    .formatted(elapsedDays, rule.withdrawalDays());
        } else if ((creditUsed || benefitUsed) && "NO_REFUND".equals(rule.usedPolicy())) {
            result = "INELIGIBLE";
            decisionCode = creditUsed && benefitUsed ? "CREDIT_AND_BENEFIT_USED"
                    : creditUsed ? "CREDIT_USED" : "BENEFIT_USED";
            message = creditUsed && benefitUsed
                    ? "결제 이후 크레딧과 사용권 사용 이력이 있어 환불이 불가합니다."
                    : creditUsed
                            ? "결제 이후 크레딧 사용 이력이 있어 환불이 불가합니다."
                            : "구독 사용권 사용 이력이 있어 환불이 불가합니다.";
        } else if (creditUsed || benefitUsed) {
            result = "REVIEW_REQUIRED";
            decisionCode = "USED_RESOURCE_REVIEW";
            message = "결제 이후 유료 기능 사용 이력이 있어 관리자가 직접 검토합니다.";
        } else {
            result = "ELIGIBLE";
            decisionCode = "UNUSED_WITHIN_PERIOD";
            message = "미사용·신청 기간 내 결제로 전액 환불 검토를 신청할 수 있습니다.";
        }

        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("policyId", rule.policyId());
        basis.put("policyVersion", rule.policyVersion());
        basis.put("policySnapshotValid", rule.valid());
        basis.put("withdrawalDays", rule.withdrawalDays());
        basis.put("usedPolicy", rule.usedPolicy());
        basis.put("productType", payment.getProductType());
        basis.put("paidAt", paidAt);
        basis.put("elapsedDays", elapsedDays);
        basis.put("creditUsed", creditUsed);
        basis.put("benefitUsed", benefitUsed);
        basis.put("reasonCode", reasonCode);
        basis.put("result", result);
        basis.put("decisionCode", decisionCode);
        basis.put("message", message);
        return new Evaluation(payment, rule, creditUsed, benefitUsed, result, decisionCode, message, basis);
    }

    private PolicyRule readPolicyRule(String snapshotJson) {
        int withdrawalDays = 7;
        String usedPolicy = "NO_REFUND";
        Long policyId = null;
        int policyVersion = 0;
        boolean valid = false;
        String title = null;
        String summary = null;
        if (snapshotJson != null && !snapshotJson.isBlank()) {
            try {
                JsonNode refund = objectMapper.readTree(snapshotJson).path("refundPolicy");
                policyId = refund.path("id").isNumber() ? refund.path("id").longValue() : null;
                policyVersion = refund.path("version").isNumber() ? refund.path("version").intValue() : 0;
                title = refund.path("title").isTextual() ? refund.path("title").asText() : null;
                summary = refund.path("summary").isTextual() ? refund.path("summary").asText() : null;
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
        return new PolicyRule(policyId, policyVersion, title, summary, withdrawalDays, usedPolicy, valid);
    }

    private PolicyRule readCurrentPolicyRule() {
        RefundPolicy policy = refundPolicyService.currentPolicy();
        if (policy == null || policy.getRulesJson() == null || policy.getRulesJson().isBlank()) {
            return new PolicyRule(null, 0, null, null, 7, "NO_REFUND", false);
        }
        try {
            JsonNode rules = objectMapper.readTree(policy.getRulesJson());
            JsonNode daysNode = rules.path("withdrawalDays");
            JsonNode usedNode = rules.path("usedPolicy");
            int days = Math.max(0, daysNode.isNumber() ? daysNode.intValue() : 7);
            String used = usedNode.isTextual() ? usedNode.asText().toUpperCase(Locale.ROOT) : "NO_REFUND";
            return new PolicyRule(policy.getId(), policy.getVersion(), policy.getTitle(), policy.getSummary(),
                    days, used, true);
        } catch (JacksonException ex) {
            return new PolicyRule(policy.getId(), policy.getVersion(), policy.getTitle(), policy.getSummary(),
                    7, "NO_REFUND", false);
        }
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

    private record PolicyRule(Long policyId, int policyVersion, String title, String summary,
                              int withdrawalDays, String usedPolicy, boolean valid) {
    }

    private record Evaluation(Payment payment, PolicyRule rule, boolean creditUsed, boolean benefitUsed,
                              String result, String decisionCode, String message, Map<String, Object> basis) {
    }
}

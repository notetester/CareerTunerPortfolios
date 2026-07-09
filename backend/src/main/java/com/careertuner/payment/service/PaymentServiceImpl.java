package com.careertuner.payment.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.service.BillingPolicyService;
import com.careertuner.billing.service.BillingService;
import com.careertuner.billing.domain.RefundPolicy;
import com.careertuner.billing.service.RefundPolicyService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.domain.CreditProduct;
import com.careertuner.payment.domain.Payment;
import com.careertuner.payment.dto.TossPaymentCancelResponse;
import com.careertuner.payment.dto.TossPaymentConfirmRequest;
import com.careertuner.payment.dto.TossPaymentConfirmResponse;
import com.careertuner.payment.dto.TossPaymentReadyRequest;
import com.careertuner.payment.dto.TossPaymentReadyResponse;
import com.careertuner.payment.mapper.PaymentMapper;
import com.careertuner.payment.service.TossPaymentClient.ConfirmedPayment;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String PROVIDER_TOSS = "TOSS";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String PRODUCT_TYPE_CREDIT = "CREDIT";
    private static final String PRODUCT_TYPE_SUBSCRIPTION = "SUBSCRIPTION";
    private static final int ORDER_ID_RANDOM_LENGTH = 8;
    private static final int ORDER_ID_RETRY_COUNT = 3;
    private static final char[] ORDER_ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter ORDER_ID_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BillingService billingService;
    private final BillingPolicyService billingPolicyService;
    private final RefundPolicyService refundPolicyService;
    private final PaymentMapper paymentMapper;
    private final TossPaymentClient tossPaymentClient;
    private final TossPaymentProperties tossPaymentProperties;
    /** 활동 리워드 적립(크레딧 구매 확정 시 CREDIT_PURCHASE 페이백). 규칙 off 면 미적립. */
    private final com.careertuner.reward.service.RewardService rewardService;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 리워드 적립은 결제 확정 실패로 이어지지 않도록 예외를 흡수한다. */
    private void grantRewardSafely(Long userId, String eventCode, String refType, Long refId) {
        try {
            rewardService.grant(userId, eventCode, refType, refId);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(PaymentServiceImpl.class)
                    .warn("리워드 적립 실패 event={} userId={} : {}", eventCode, userId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public TossPaymentReadyResponse ready(Long userId, String email, TossPaymentReadyRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결제 요청 정보가 필요합니다.");
        }
        RefundPolicy refundPolicy = refundPolicyService.requirePaymentAcknowledgement(
                userId, request.refundPolicyId(), request.policyAcknowledgementKey());
        if (PRODUCT_TYPE_SUBSCRIPTION.equals(request.productType())) {
            return readySubscription(
                    userId, email, request.productCode(), refundPolicy, request.policyAcknowledgementKey());
        }
        if (!PRODUCT_TYPE_CREDIT.equals(request.productType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Unsupported payment product type.");
        }
        return readyCredit(
                userId, email, request.productCode(), refundPolicy, request.policyAcknowledgementKey());
    }

    private TossPaymentReadyResponse readyCredit(
            Long userId, String email, String productCode, RefundPolicy refundPolicy,
            String policyAcknowledgementKey) {
        CreditProduct product = requireActiveProduct(productCode);
        validateProduct(product);

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setProvider(PROVIDER_TOSS);
        payment.setProductType(PRODUCT_TYPE_CREDIT);
        payment.setProductCode(product.getCode());
        payment.setPaymentKey(null);
        payment.setAmount(product.getPrice());
        payment.setPlan(null);
        payment.setCreditAmount(product.getCreditAmount());
        payment.setPolicySnapshotJson(refundPolicyService.appendPaymentSnapshot(
                billingPolicyService.creditProductSnapshotJson(product), refundPolicy, policyAcknowledgementKey));
        payment.setStatus(STATUS_READY);
        insertPaymentWithUniqueOrderId(payment);

        return new TossPaymentReadyResponse(
                payment.getOrderId(),
                PRODUCT_TYPE_CREDIT,
                product.getCode(),
                null,
                orderName(product),
                payment.getAmount(),
                product.getCreditAmount(),
                email,
                tossPaymentProperties.getSuccessUrl(),
                tossPaymentProperties.getFailUrl());
    }

    private TossPaymentReadyResponse readySubscription(
            Long userId, String email, String planCode, RefundPolicy refundPolicy,
            String policyAcknowledgementKey) {
        SubscriptionPlan plan = requirePaidSubscriptionPlan(planCode);

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setProvider(PROVIDER_TOSS);
        payment.setProductType(PRODUCT_TYPE_SUBSCRIPTION);
        payment.setProductCode(plan.getCode());
        payment.setPaymentKey(null);
        payment.setAmount(plan.getMonthlyPrice());
        payment.setPlan(plan.getCode());
        payment.setCreditAmount(0);
        payment.setPolicySnapshotJson(refundPolicyService.appendPaymentSnapshot(
                billingPolicyService.subscriptionSnapshotJson(plan.getCode()), refundPolicy,
                policyAcknowledgementKey));
        payment.setStatus(STATUS_READY);
        insertPaymentWithUniqueOrderId(payment);

        return new TossPaymentReadyResponse(
                payment.getOrderId(),
                PRODUCT_TYPE_SUBSCRIPTION,
                plan.getCode(),
                plan.getCode(),
                orderName(plan),
                payment.getAmount(),
                0,
                email,
                tossPaymentProperties.getSuccessUrl(),
                tossPaymentProperties.getFailUrl());
    }

    @Override
    @Transactional
    public TossPaymentConfirmResponse confirm(Long userId, TossPaymentConfirmRequest request) {
        Payment payment = requireOwnedPayment(request.orderId(), userId);
        normalizePaymentProductType(payment);
        validateConfirmRequest(payment, request);

        ConfirmedPayment confirmed = tossPaymentClient.confirm(request.paymentKey(), request.orderId(), request.amount());
        validateTossConfirmedPayment(payment, request, confirmed);

        int paidRows = markPaidIfReady(payment, request.paymentKey());
        if (paidRows == 0) {
            Payment latest = paymentMapper.findByOrderId(payment.getOrderId());
            if (latest != null && STATUS_PAID.equals(latest.getStatus())) {
                normalizePaymentProductType(latest);
                int balance = requireUserCredit(payment.getUserId());
                return confirmedResponse(latest, latest.getPaymentKey(), balance);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "Payment status changed before confirmation.");
        }

        int balance;
        if (PRODUCT_TYPE_SUBSCRIPTION.equals(payment.getProductType())) {
            billingService.activateSubscriptionAfterPayment(
                    payment.getUserId(),
                    payment.getId(),
                    payment.getPlan(),
                    payment.getPolicySnapshotJson());
            balance = requireUserCredit(payment.getUserId());
        } else {
            balance = billingService.grantCreditsAfterPayment(
                    payment.getUserId(),
                    payment.getProductCode(),
                    requireCreditAmount(payment));
            // 크레딧 구매 페이백 리워드(규칙 on 일 때만). 실패해도 결제 확정은 유지.
            grantRewardSafely(payment.getUserId(), "CREDIT_PURCHASE", "PAYMENT", payment.getId());
        }

        return confirmedResponse(payment, request.paymentKey(), balance);
    }

    @Override
    @Transactional
    public TossPaymentCancelResponse cancelReadyPayment(Long userId, String orderId) {
        Payment payment = requireOwnedPayment(orderId, userId);
        normalizePaymentProductType(payment);

        if (STATUS_READY.equals(payment.getStatus())) {
            paymentMapper.markCanceledIfReady(payment.getOrderId());
            payment.setStatus(STATUS_CANCELED);
        }

        Payment latest = paymentMapper.findByOrderId(payment.getOrderId());
        if (latest != null) {
            normalizePaymentProductType(latest);
            return cancelResponse(latest);
        }
        return cancelResponse(payment);
    }

    private TossPaymentCancelResponse cancelResponse(Payment payment) {
        return new TossPaymentCancelResponse(
                payment.getOrderId(),
                payment.getProductType(),
                payment.getProductCode(),
                payment.getPlan(),
                payment.getStatus());
    }

    private TossPaymentConfirmResponse confirmedResponse(Payment payment, String paymentKey, int balance) {
        return new TossPaymentConfirmResponse(
                payment.getOrderId(),
                paymentKey,
                payment.getProductType(),
                payment.getProductCode(),
                payment.getPlan(),
                payment.getAmount(),
                requireCreditAmount(payment),
                STATUS_PAID,
                balance);
    }

    private CreditProduct requireActiveProduct(String productCode) {
        CreditProduct product = billingPolicyService.enabledCreditProductByCode(productCode);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Purchasable credit product was not found.");
        }
        return product;
    }

    private SubscriptionPlan requirePaidSubscriptionPlan(String planCode) {
        SubscriptionPlan plan = billingPolicyService.activePlanByCode(planCode);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Purchasable subscription plan was not found.");
        }
        if (plan.getMonthlyPrice() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Free plan does not require payment.");
        }
        return plan;
    }

    private void validateProduct(CreditProduct product) {
        if (product.getPrice() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Product amount is invalid.");
        }
        if (product.getCreditAmount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Credit product amount is invalid.");
        }
    }

    private Payment requireOwnedPayment(String orderId, Long userId) {
        Payment payment = paymentMapper.findByOrderId(orderId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Payment was not found.");
        }
        if (!userId.equals(payment.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot confirm another user's payment.");
        }
        return payment;
    }

    private void validateConfirmRequest(Payment payment, TossPaymentConfirmRequest request) {
        if (!PROVIDER_TOSS.equals(payment.getProvider())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Not a Toss payment.");
        }
        if (STATUS_PAID.equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Payment was already confirmed.");
        }
        if (!STATUS_READY.equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Payment is not ready for confirmation.");
        }
        if (payment.getAmount() != request.amount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Payment amount does not match.");
        }
        if (PRODUCT_TYPE_CREDIT.equals(payment.getProductType()) && requireCreditAmount(payment) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Credit amount is invalid.");
        }
        if (PRODUCT_TYPE_SUBSCRIPTION.equals(payment.getProductType()) && isBlank(payment.getPlan())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Subscription plan is missing.");
        }
        if (paymentMapper.existsByPaymentKey(request.paymentKey())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Payment key was already processed.");
        }
    }

    private void validateTossConfirmedPayment(Payment payment,
                                              TossPaymentConfirmRequest request,
                                              ConfirmedPayment confirmed) {
        if (!request.paymentKey().equals(confirmed.paymentKey())) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss payment key does not match.");
        }
        if (!payment.getOrderId().equals(confirmed.orderId())) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss order id does not match.");
        }
        if (payment.getAmount() != confirmed.totalAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss amount does not match.");
        }
        if (!"DONE".equals(confirmed.status())) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss payment was not completed.");
        }
    }

    private int requireCreditAmount(Payment payment) {
        return payment.getCreditAmount() == null ? 0 : payment.getCreditAmount();
    }

    private int markPaidIfReady(Payment payment, String paymentKey) {
        try {
            return paymentMapper.markPaidIfReady(payment.getOrderId(), paymentKey);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "Payment key was already processed.");
        }
    }

    private int requireUserCredit(Long userId) {
        Integer balance = paymentMapper.findUserCredit(userId);
        if (balance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Payment user was not found.");
        }
        return balance;
    }

    private String generateUniqueOrderId(Long userId) {
        return "CT-%s-%d-%s".formatted(
                LocalDateTime.now().format(ORDER_ID_TIME_FORMAT),
                userId,
                randomToken());
    }

    private void insertPaymentWithUniqueOrderId(Payment payment) {
        for (int i = 0; i < ORDER_ID_RETRY_COUNT; i++) {
            try {
                payment.setOrderId(generateUniqueOrderId(payment.getUserId()));
                paymentMapper.insertPayment(payment);
                return;
            } catch (DuplicateKeyException ex) {
                payment.setId(null);
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not generate payment order id.");
    }

    private String randomToken() {
        StringBuilder builder = new StringBuilder(ORDER_ID_RANDOM_LENGTH);
        for (int i = 0; i < ORDER_ID_RANDOM_LENGTH; i++) {
            builder.append(ORDER_ID_CHARS[secureRandom.nextInt(ORDER_ID_CHARS.length)]);
        }
        return builder.toString();
    }

    private String orderName(CreditProduct product) {
        return isBlank(product.getName()) ? "Credit " + product.getCreditAmount() : product.getName();
    }

    private String orderName(SubscriptionPlan plan) {
        return isBlank(plan.getName()) ? plan.getCode() + " plan" : plan.getName() + " plan";
    }

    private void normalizePaymentProductType(Payment payment) {
        if (isBlank(payment.getProductType())) {
            payment.setProductType(PRODUCT_TYPE_CREDIT);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

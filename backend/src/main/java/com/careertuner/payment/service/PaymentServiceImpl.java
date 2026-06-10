package com.careertuner.payment.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.domain.CreditProduct;
import com.careertuner.credit.mapper.CreditProductMapper;
import com.careertuner.payment.domain.Payment;
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
    private static final int ORDER_ID_RANDOM_LENGTH = 8;
    private static final int ORDER_ID_RETRY_COUNT = 3;
    private static final char[] ORDER_ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter ORDER_ID_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CreditProductMapper creditProductMapper;
    private final PaymentMapper paymentMapper;
    private final TossPaymentClient tossPaymentClient;
    private final TossPaymentProperties tossPaymentProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 상품 정책을 DB에서 확정하고 Toss 결제창 호출 전 READY 결제 건을 만든다. */
    @Override
    @Transactional
    public TossPaymentReadyResponse ready(Long userId, String email, TossPaymentReadyRequest request) {
        CreditProduct product = requireActiveProduct(request.productCode());
        validateProduct(product);

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setProvider(PROVIDER_TOSS);
        payment.setProductCode(product.getCode());
        payment.setPaymentKey(null);
        payment.setAmount(product.getPrice());
        payment.setPlan(null);
        payment.setCreditAmount(product.getCreditAmount());
        payment.setStatus(STATUS_READY);
        insertPaymentWithUniqueOrderId(payment);

        return new TossPaymentReadyResponse(
                payment.getOrderId(),
                orderName(product),
                payment.getAmount(),
                product.getCreditAmount(),
                email,
                tossPaymentProperties.getSuccessUrl(),
                tossPaymentProperties.getFailUrl());
    }

    /** Toss 승인 결과를 검증한 뒤 PAID 전환과 사용자 크레딧 충전을 한 트랜잭션으로 처리한다. */
    @Override
    @Transactional
    public TossPaymentConfirmResponse confirm(Long userId, TossPaymentConfirmRequest request) {
        Payment payment = requireOwnedPayment(request.orderId(), userId);
        validateConfirmRequest(payment, request);

        ConfirmedPayment confirmed = tossPaymentClient.confirm(request.paymentKey(), request.orderId(), request.amount());
        validateTossConfirmedPayment(payment, request, confirmed);

        int paidRows = markPaidIfReady(payment, request.paymentKey());
        if (paidRows == 0) {
            Payment latest = paymentMapper.findByOrderId(payment.getOrderId());
            if (latest != null && STATUS_PAID.equals(latest.getStatus())) {
                int balance = requireUserCredit(payment.getUserId());
                return new TossPaymentConfirmResponse(
                        latest.getOrderId(),
                        latest.getPaymentKey(),
                        latest.getAmount(),
                        requireCreditAmount(latest),
                        latest.getStatus(),
                        balance);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "결제 상태가 변경되어 승인 처리할 수 없습니다.");
        }

        int creditRows = paymentMapper.increaseUserCredit(payment.getUserId(), requireCreditAmount(payment));
        if (creditRows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "크레딧을 충전할 사용자를 찾을 수 없습니다.");
        }

        int balance = requireUserCredit(payment.getUserId());
        return new TossPaymentConfirmResponse(
                payment.getOrderId(),
                request.paymentKey(),
                payment.getAmount(),
                requireCreditAmount(payment),
                STATUS_PAID,
                balance);
    }

    /** 결제 준비에 사용할 활성 크레딧 상품을 상품 코드로 조회한다. */
    private CreditProduct requireActiveProduct(String productCode) {
        CreditProduct product = creditProductMapper.findEnabledProductByCode(productCode);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "구매 가능한 크레딧 상품을 찾을 수 없습니다.");
        }
        return product;
    }

    /** 상품 가격과 지급 크레딧이 결제 가능한 값인지 확인한다. */
    private void validateProduct(CreditProduct product) {
        if (product.getPrice() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 결제 금액이 올바르지 않습니다.");
        }
        if (product.getCreditAmount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 지급 크레딧이 올바르지 않습니다.");
        }
    }

    /** 주문번호로 결제 건을 찾고 현재 로그인 사용자의 결제인지 확인한다. */
    private Payment requireOwnedPayment(String orderId, Long userId) {
        Payment payment = paymentMapper.findByOrderId(orderId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "결제 건을 찾을 수 없습니다.");
        }
        if (!userId.equals(payment.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 결제 건만 승인할 수 있습니다.");
        }
        return payment;
    }

    /** Toss 승인 API를 호출하기 전에 내부 결제 상태, 금액, 중복 키를 검증한다. */
    private void validateConfirmRequest(Payment payment, TossPaymentConfirmRequest request) {
        if (!PROVIDER_TOSS.equals(payment.getProvider())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Toss 결제 건이 아닙니다.");
        }
        if (STATUS_PAID.equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 승인된 결제입니다.");
        }
        if (!STATUS_READY.equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "승인할 수 없는 결제 상태입니다.");
        }
        if (payment.getAmount() != request.amount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결제 금액이 일치하지 않습니다.");
        }
        if (requireCreditAmount(payment) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지급 크레딧이 올바르지 않습니다.");
        }
        if (paymentMapper.existsByPaymentKey(request.paymentKey())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 결제 키입니다.");
        }
    }

    /** Toss 승인 응답이 요청과 내부 결제 스냅샷에 정확히 대응하는지 확인한다. */
    private void validateTossConfirmedPayment(Payment payment,
                                              TossPaymentConfirmRequest request,
                                              ConfirmedPayment confirmed) {
        if (!request.paymentKey().equals(confirmed.paymentKey())) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss 승인 응답의 결제 키가 일치하지 않습니다.");
        }
        if (!payment.getOrderId().equals(confirmed.orderId())) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss 승인 응답의 주문번호가 일치하지 않습니다.");
        }
        if (payment.getAmount() != confirmed.totalAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss 승인 응답의 결제 금액이 일치하지 않습니다.");
        }
        if (!"DONE".equals(confirmed.status())) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss 결제가 완료 상태가 아닙니다.");
        }
    }

    /** 결제 당시 스냅샷으로 저장된 지급 크레딧이 존재하는지 확인하고 반환한다. */
    private int requireCreditAmount(Payment payment) {
        if (payment.getCreditAmount() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지급 크레딧이 설정되지 않은 결제 건입니다.");
        }
        return payment.getCreditAmount();
    }

    /** READY 상태 결제만 PAID로 바꿔 중복 승인과 중복 충전을 막는다. */
    private int markPaidIfReady(Payment payment, String paymentKey) {
        try {
            return paymentMapper.markPaidIfReady(payment.getOrderId(), paymentKey);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 결제 키입니다.");
        }
    }

    /** 크레딧 충전 후 사용자 잔액을 조회하고 사용자가 없으면 예외로 중단한다. */
    private int requireUserCredit(Long userId) {
        Integer balance = paymentMapper.findUserCredit(userId);
        if (balance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return balance;
    }

    /** Toss 주문번호로 사용할 충돌 가능성이 낮은 서버 생성 문자열을 만든다. */
    private String generateUniqueOrderId(Long userId) {
        return "CT-%s-%d-%s".formatted(
                LocalDateTime.now().format(ORDER_ID_TIME_FORMAT),
                userId,
                randomToken());
    }

    /** 주문번호 유니크 충돌이 발생하면 제한 횟수 안에서 새 주문번호로 재저장한다. */
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
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "주문번호를 생성하지 못했습니다.");
    }

    /** 주문번호 뒤에 붙일 짧은 난수 토큰을 생성한다. */
    private String randomToken() {
        StringBuilder builder = new StringBuilder(ORDER_ID_RANDOM_LENGTH);
        for (int i = 0; i < ORDER_ID_RANDOM_LENGTH; i++) {
            builder.append(ORDER_ID_CHARS[secureRandom.nextInt(ORDER_ID_CHARS.length)]);
        }
        return builder.toString();
    }

    /** 결제창에 표시할 주문명을 상품명 우선으로 만들고 없으면 크레딧 수량으로 대체한다. */
    private String orderName(CreditProduct product) {
        return product.getName() == null || product.getName().isBlank()
                ? "크레딧 " + product.getCreditAmount() + "개"
                : product.getName();
    }
}

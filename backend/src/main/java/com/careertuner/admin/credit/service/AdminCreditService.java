package com.careertuner.admin.credit.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.credit.dto.AdminCreditAdjustRequest;
import com.careertuner.admin.credit.dto.AdminCreditAdjustResponse;
import com.careertuner.admin.credit.dto.AdminCreditPage;
import com.careertuner.admin.credit.dto.AdminCreditSearchCriteria;
import com.careertuner.admin.credit.dto.AdminCreditSummary;
import com.careertuner.admin.credit.dto.AdminCreditUserBalance;
import com.careertuner.admin.credit.mapper.AdminCreditMapper;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.credit.domain.CreditTransaction;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminCreditService {

    static final int MAX_ABSOLUTE_ADJUSTMENT = 1_000_000;
    private static final Set<String> TRANSACTION_TYPES = Set.of(
            "AI_USAGE", "CHARGE", "REFUND", "ADMIN_ADJUST");

    private final AdminCreditMapper mapper;
    private final AdminActionLogService actionLogService;

    @Transactional(readOnly = true)
    public AdminCreditPage transactions(
            AuthUser authUser,
            String keyword,
            Long userId,
            String type,
            int page,
            int size
    ) {
        AdminAccess.requireAdmin(authUser);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = size <= 0 ? 20 : Math.min(size, 100);
        AdminCreditSearchCriteria criteria = new AdminCreditSearchCriteria(
                blankToNull(keyword),
                normalizeOptionalUserId(userId),
                normalizeType(type),
                normalizedPage,
                normalizedSize,
                (long) (normalizedPage - 1) * normalizedSize);
        return new AdminCreditPage(
                mapper.findTransactions(criteria),
                mapper.countTransactions(criteria),
                normalizedPage,
                normalizedSize);
    }

    @Transactional(readOnly = true)
    public AdminCreditSummary summary(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findSummary();
    }

    @Transactional
    public AdminCreditAdjustResponse adjust(
            AuthUser authUser,
            AdminCreditAdjustRequest request
    ) {
        AdminAccess.requireAdmin(authUser);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "크레딧 조정 요청이 필요합니다.");
        }
        Long userId = normalizeUserId(request.userId());
        int amount = normalizeAmount(request.amount());
        String reason = requireReason(request.reason());
        String requestKey = normalizeRequestId(request.requestId());

        AdminCreditUserBalance user = mapper.findUserBalanceForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없거나 삭제된 회원입니다.");
        }

        CreditTransaction previous = requestKey == null
                ? null
                : mapper.findAdminAdjustmentByRequestKey(userId, requestKey);
        if (previous != null) {
            if (previous.getAmount() != amount || !reason.equals(previous.getReason())) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "동일 요청 ID가 다른 크레딧 조정 내용에 사용되었습니다.");
            }
            return new AdminCreditAdjustResponse(
                    previous.getId(),
                    userId,
                    previous.getAmount(),
                    previous.getBalanceAfter() - previous.getAmount(),
                    previous.getBalanceAfter());
        }

        int balanceBefore = user.getCredit();
        if (amount > 0) {
            if (mapper.addUserCredit(userId, amount) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "크레딧 잔액이 허용 범위를 초과합니다.");
            }
        } else {
            int deduction = -amount;
            if (mapper.deductUserCreditIfEnough(userId, deduction) != 1) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_CREDIT, "차감할 크레딧이 부족합니다.");
            }
        }

        int balanceAfter = balanceBefore + amount;

        CreditTransaction transaction = CreditTransaction.builder()
                .userId(userId)
                .type("ADMIN_ADJUST")
                .amount(amount)
                .balanceAfter(balanceAfter)
                .featureType("ADMIN_CREDIT_ADJUST")
                .reason(reason)
                .requestKey(requestKey)
                .build();
        mapper.insertAdminAdjustment(transaction);

        actionLogService.record(
                authUser,
                userId,
                "CREDIT_ADJUSTED",
                "CREDIT",
                Integer.toString(balanceBefore),
                Integer.toString(balanceAfter),
                reason);

        return new AdminCreditAdjustResponse(
                transaction.getId(), userId, amount, balanceBefore, balanceAfter);
    }

    private static Long normalizeUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회원 ID가 올바르지 않습니다.");
        }
        return userId;
    }

    private static Long normalizeOptionalUserId(Long userId) {
        return userId == null ? null : normalizeUserId(userId);
    }

    private static int normalizeAmount(Integer amount) {
        if (amount == null || amount == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조정 크레딧은 0일 수 없습니다.");
        }
        if (amount < -MAX_ABSOLUTE_ADJUSTMENT || amount > MAX_ABSOLUTE_ADJUSTMENT) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "한 번에 조정할 수 있는 크레딧은 절대값 " + MAX_ABSOLUTE_ADJUSTMENT + " 이하입니다.");
        }
        return amount;
    }

    private static String requireReason(String reason) {
        String normalized = blankToNull(reason);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조정 사유는 필수입니다.");
        }
        if (normalized.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조정 사유는 255자 이하여야 합니다.");
        }
        return normalized;
    }

    private static String normalizeRequestId(String requestId) {
        String normalized = blankToNull(requestId);
        if (normalized != null && normalized.length() > 120) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 ID는 120자 이하여야 합니다.");
        }
        return normalized;
    }

    private static String normalizeType(String type) {
        String normalized = blankToNull(type);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!TRANSACTION_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "type 값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

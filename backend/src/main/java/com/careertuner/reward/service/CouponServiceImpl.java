package com.careertuner.reward.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.service.CreditService;
import com.careertuner.reward.domain.Coupon;
import com.careertuner.reward.domain.UserCoupon;
import com.careertuner.reward.dto.CouponItem;
import com.careertuner.reward.dto.CouponRedeemResult;
import com.careertuner.reward.mapper.CouponMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponServiceImpl.class);

    private final CouponMapper couponMapper;
    private final CreditService creditService;

    @Override
    @Transactional
    public UserCoupon issue(Long userId, String code) {
        Coupon coupon = couponMapper.findCouponByCode(code);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
        }
        if (!coupon.isEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용할 수 없는 쿠폰입니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아직 사용할 수 없는 쿠폰입니다.");
        }
        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효기간이 지난 쿠폰입니다.");
        }
        // 발급 상한을 원자적으로 검사·증가한다.
        int issued = couponMapper.incrementIssued(coupon.getId());
        if (issued == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "쿠폰 발급 수량이 모두 소진되었습니다.");
        }
        UserCoupon userCoupon = UserCoupon.builder()
                .couponId(coupon.getId())
                .userId(userId)
                .code(coupon.getCode())
                .status("ISSUED")
                .build();
        couponMapper.insertUserCoupon(userCoupon);
        return userCoupon;
    }

    @Override
    @Transactional
    public boolean issueQuietly(Long userId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        try {
            issue(userId, code);
            return true;
        } catch (RuntimeException e) {
            log.warn("자동 쿠폰 발급 실패 userId={} code={} : {}", userId, code, e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponItem> myCoupons(Long userId) {
        return couponMapper.findUserCouponItems(userId);
    }

    @Override
    @Transactional
    public CouponRedeemResult redeem(Long userId, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "쿠폰 코드를 입력하세요.");
        }
        UserCoupon userCoupon = couponMapper.findActiveUserCoupon(userId, normalized);
        if (userCoupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용 가능한 쿠폰이 없습니다.");
        }
        Coupon coupon = couponMapper.findCouponByCode(normalized);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "쿠폰 정의를 찾을 수 없습니다.");
        }
        if (!"CREDIT".equals(coupon.getDiscountType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이 쿠폰은 결제 시 할인으로 적용됩니다. 크레딧 즉시 적립 쿠폰이 아닙니다.");
        }
        int used = couponMapper.markUserCouponUsed(userCoupon.getId(), null);
        if (used == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용된 쿠폰입니다.");
        }
        int balanceAfter = creditService.grantCredit(
                userId, coupon.getDiscountValue(), "COUPON", "COUPON_REDEEM", "쿠폰 사용: " + normalized);
        return new CouponRedeemResult(normalized, "CREDIT", coupon.getDiscountValue(), balanceAfter,
                "크레딧 " + coupon.getDiscountValue() + "이(가) 적립되었습니다.");
    }
}

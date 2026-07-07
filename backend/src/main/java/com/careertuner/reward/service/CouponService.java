package com.careertuner.reward.service;

import java.util.List;

import com.careertuner.reward.domain.UserCoupon;
import com.careertuner.reward.dto.CouponItem;
import com.careertuner.reward.dto.CouponRedeemResult;

/** 쿠폰 발급/사용 서비스. */
public interface CouponService {

    /** 쿠폰 코드로 사용자에게 쿠폰을 발급한다. 유효성/발급상한 위반 시 예외. */
    UserCoupon issue(Long userId, String code);

    /** 레벨업 등 자동 발급용 — 실패해도 예외를 던지지 않고 발급 성공 여부만 반환. */
    boolean issueQuietly(Long userId, String code);

    /** 내 쿠폰 목록. */
    List<CouponItem> myCoupons(Long userId);

    /** 보유한 CREDIT 쿠폰을 사용해 즉시 크레딧을 적립한다. */
    CouponRedeemResult redeem(Long userId, String code);
}

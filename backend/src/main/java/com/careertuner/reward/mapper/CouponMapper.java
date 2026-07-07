package com.careertuner.reward.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.reward.domain.Coupon;
import com.careertuner.reward.domain.UserCoupon;
import com.careertuner.reward.dto.CouponItem;

/** 쿠폰 정의/발급/사용 접근. */
@Mapper
public interface CouponMapper {

    Coupon findCouponByCode(@Param("code") String code);

    Coupon findCouponById(@Param("id") Long id);

    /** 발급 카운트를 1 증가시킨다(총 발급 상한 가드 포함). 성공 시 1. */
    int incrementIssued(@Param("couponId") Long couponId);

    void insertUserCoupon(UserCoupon userCoupon);

    /** 사용 가능한(ISSUED) 사용자 쿠폰을 코드로 조회한다. */
    UserCoupon findActiveUserCoupon(@Param("userId") Long userId, @Param("code") String code);

    /** 사용자 쿠폰을 사용 처리한다(ISSUED → USED). 성공 시 1. */
    int markUserCouponUsed(@Param("id") Long id, @Param("orderRef") Long orderRef);

    /** 사용자의 쿠폰 목록(발급 최신순). */
    List<UserCoupon> findUserCouponsByUser(@Param("userId") Long userId);

    /** 마이페이지용 — coupon 정의를 조인한 사용자 쿠폰 목록. */
    List<CouponItem> findUserCouponItems(@Param("userId") Long userId);
}

package com.careertuner.reward.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.reward.dto.CouponItem;
import com.careertuner.reward.dto.CouponRedeemRequest;
import com.careertuner.reward.dto.CouponRedeemResult;
import com.careertuner.reward.dto.MyRewardResponse;
import com.careertuner.reward.service.CouponService;
import com.careertuner.reward.service.RewardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 사용자 리워드/레벨·쿠폰 조회 및 쿠폰 사용. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;
    private final CouponService couponService;

    /** 내 리워드/레벨 요약 + 최근 이력. */
    @GetMapping("/rewards/me")
    public ApiResponse<MyRewardResponse> myReward(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(rewardService.myReward(authUser.id()));
    }

    /** 내 쿠폰 목록. */
    @GetMapping("/coupons/me")
    public ApiResponse<List<CouponItem>> myCoupons(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(couponService.myCoupons(authUser.id()));
    }

    /** 보유 CREDIT 쿠폰 즉시 사용(크레딧 적립). */
    @PostMapping("/coupons/redeem")
    public ApiResponse<CouponRedeemResult> redeem(@AuthenticationPrincipal AuthUser authUser,
                                                  @Valid @RequestBody CouponRedeemRequest request) {
        return ApiResponse.ok(couponService.redeem(authUser.id(), request.code()));
    }
}

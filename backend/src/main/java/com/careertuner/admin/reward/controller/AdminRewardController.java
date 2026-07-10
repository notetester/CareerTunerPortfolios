package com.careertuner.admin.reward.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.reward.dto.AdminCouponIssueRequest;
import com.careertuner.admin.reward.dto.AdminCouponPage;
import com.careertuner.admin.reward.dto.AdminCouponRequest;
import com.careertuner.admin.reward.dto.AdminLevelPolicyRequest;
import com.careertuner.admin.reward.dto.AdminRewardHistoryPage;
import com.careertuner.admin.reward.dto.AdminRewardRuleUpdateRequest;
import com.careertuner.admin.reward.service.AdminRewardService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.common.web.SitesFinancialMutation;
import com.careertuner.reward.domain.Coupon;
import com.careertuner.reward.domain.RewardRule;
import com.careertuner.reward.domain.UserCoupon;
import com.careertuner.reward.domain.UserLevelPolicy;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 리워드 콘솔: 적립 규칙 on/off·값, 레벨 정책, 쿠폰, 리워드 이력. */
@RestController
@RequestMapping("/api/admin/rewards")
@RequiredArgsConstructor
@Validated
public class AdminRewardController {

    private final AdminRewardService service;

    // ── 적립 규칙 ──
    @GetMapping("/rules")
    public ApiResponse<List<RewardRule>> rules(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.rules(authUser));
    }

    @SitesFinancialMutation
    @PutMapping("/rules/{id}")
    public ApiResponse<RewardRule> updateRule(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long id,
                                              @Valid @RequestBody AdminRewardRuleUpdateRequest req) {
        return ApiResponse.ok(service.updateRule(authUser, id, req));
    }

    @SitesFinancialMutation
    @PatchMapping("/rules/{id}/enabled")
    public ApiResponse<RewardRule> toggleRule(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long id,
                                              @RequestParam boolean enabled) {
        return ApiResponse.ok(service.toggleRule(authUser, id, enabled));
    }

    // ── 레벨 정책 ──
    @GetMapping("/levels")
    public ApiResponse<List<UserLevelPolicy>> levels(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.levels(authUser));
    }

    @SitesFinancialMutation
    @PostMapping("/levels")
    public ApiResponse<UserLevelPolicy> createLevel(@AuthenticationPrincipal AuthUser authUser,
                                                    @Valid @RequestBody AdminLevelPolicyRequest req) {
        return ApiResponse.ok(service.createLevel(authUser, req));
    }

    @SitesFinancialMutation
    @PutMapping("/levels/{id}")
    public ApiResponse<UserLevelPolicy> updateLevel(@AuthenticationPrincipal AuthUser authUser,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody AdminLevelPolicyRequest req) {
        return ApiResponse.ok(service.updateLevel(authUser, id, req));
    }

    @SitesFinancialMutation
    @DeleteMapping("/levels/{id}")
    public ApiResponse<Void> deleteLevel(@AuthenticationPrincipal AuthUser authUser,
                                         @PathVariable Long id) {
        service.deleteLevel(authUser, id);
        return ApiResponse.ok();
    }

    // ── 쿠폰 ──
    @GetMapping("/coupons")
    public ApiResponse<AdminCouponPage> coupons(@AuthenticationPrincipal AuthUser authUser,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.coupons(authUser, keyword, page, size));
    }

    @SitesFinancialMutation
    @PostMapping("/coupons")
    public ApiResponse<Coupon> createCoupon(@AuthenticationPrincipal AuthUser authUser,
                                            @Valid @RequestBody AdminCouponRequest req) {
        return ApiResponse.ok(service.createCoupon(authUser, req));
    }

    @SitesFinancialMutation
    @PutMapping("/coupons/{id}")
    public ApiResponse<Coupon> updateCoupon(@AuthenticationPrincipal AuthUser authUser,
                                            @PathVariable Long id,
                                            @Valid @RequestBody AdminCouponRequest req) {
        return ApiResponse.ok(service.updateCoupon(authUser, id, req));
    }

    @SitesFinancialMutation
    @PostMapping("/coupons/{id}/issue")
    public ApiResponse<UserCoupon> issueCoupon(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long id,
                                               @Valid @RequestBody AdminCouponIssueRequest req) {
        return ApiResponse.ok(service.issueCoupon(authUser, id, req.userId()));
    }

    // ── 리워드 이력 ──
    @GetMapping("/history")
    public ApiResponse<AdminRewardHistoryPage> history(@AuthenticationPrincipal AuthUser authUser,
                                                       @RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) String eventCode,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.history(authUser, userId, eventCode, keyword, page, size));
    }
}

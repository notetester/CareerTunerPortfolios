package com.careertuner.admin.reward.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.reward.dto.AdminCouponPage;
import com.careertuner.admin.reward.dto.AdminCouponRequest;
import com.careertuner.admin.reward.dto.AdminLevelPolicyRequest;
import com.careertuner.admin.reward.dto.AdminRewardHistoryPage;
import com.careertuner.admin.reward.dto.AdminRewardRuleUpdateRequest;
import com.careertuner.admin.reward.mapper.AdminRewardMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.reward.domain.Coupon;
import com.careertuner.reward.domain.RewardRule;
import com.careertuner.reward.domain.UserCoupon;
import com.careertuner.reward.domain.UserLevelPolicy;
import com.careertuner.reward.service.CouponService;

import lombok.RequiredArgsConstructor;

/** 관리자 리워드 콘솔 서비스: 적립 규칙/레벨 정책/쿠폰/리워드 이력. */
@Service
@RequiredArgsConstructor
public class AdminRewardService {

    private final AdminRewardMapper mapper;
    private final CouponService couponService;

    // ── 적립 규칙 ──
    @Transactional(readOnly = true)
    public List<RewardRule> rules(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findAllRules();
    }

    @Transactional
    public RewardRule updateRule(AuthUser authUser, Long id, AdminRewardRuleUpdateRequest req) {
        AdminAccess.requireAdmin(authUser);
        RewardRule rule = requireRule(id);
        rule.setName(req.name());
        rule.setPointAmount(req.pointAmount());
        rule.setCreditAmount(req.creditAmount());
        rule.setDailyCap(req.dailyCap());
        rule.setEnabled(Boolean.TRUE.equals(req.enabled()));
        rule.setDescription(req.description());
        rule.setSortOrder(req.sortOrder());
        rule.setUpdatedBy(authUser.id());
        mapper.updateRule(rule);
        return mapper.findRuleById(id);
    }

    @Transactional
    public RewardRule toggleRule(AuthUser authUser, Long id, boolean enabled) {
        AdminAccess.requireAdmin(authUser);
        requireRule(id);
        mapper.updateRuleEnabled(id, enabled, authUser.id());
        return mapper.findRuleById(id);
    }

    private RewardRule requireRule(Long id) {
        RewardRule rule = mapper.findRuleById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "적립 규칙을 찾을 수 없습니다.");
        }
        return rule;
    }

    // ── 레벨 정책 ──
    @Transactional(readOnly = true)
    public List<UserLevelPolicy> levels(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findAllLevels();
    }

    @Transactional
    public UserLevelPolicy createLevel(AuthUser authUser, AdminLevelPolicyRequest req) {
        AdminAccess.requireAdmin(authUser);
        UserLevelPolicy level = toLevel(new UserLevelPolicy(), req);
        mapper.insertLevel(level);
        return mapper.findLevelById(level.getId());
    }

    @Transactional
    public UserLevelPolicy updateLevel(AuthUser authUser, Long id, AdminLevelPolicyRequest req) {
        AdminAccess.requireAdmin(authUser);
        UserLevelPolicy existing = mapper.findLevelById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "레벨 정책을 찾을 수 없습니다.");
        }
        UserLevelPolicy level = toLevel(existing, req);
        level.setId(id);
        mapper.updateLevel(level);
        return mapper.findLevelById(id);
    }

    @Transactional
    public void deleteLevel(AuthUser authUser, Long id) {
        AdminAccess.requireAdmin(authUser);
        mapper.deleteLevel(id);
    }

    private UserLevelPolicy toLevel(UserLevelPolicy target, AdminLevelPolicyRequest req) {
        target.setLevel(req.level());
        target.setLevelName(req.levelName());
        target.setMinPoint(req.minPoint());
        target.setLevelupCredit(req.levelupCredit());
        target.setLevelupCouponCode(blankToNull(req.levelupCouponCode()));
        target.setBenefitNote(req.benefitNote());
        target.setActive(Boolean.TRUE.equals(req.active()));
        return target;
    }

    // ── 쿠폰 ──
    @Transactional(readOnly = true)
    public AdminCouponPage coupons(AuthUser authUser, String keyword, int page, int size) {
        AdminAccess.requireAdmin(authUser);
        int p = Math.max(page, 1);
        int s = size <= 0 ? 20 : Math.min(size, 100);
        String kw = blankToNull(keyword);
        List<Coupon> items = mapper.findCoupons(kw, s, (long) (p - 1) * s);
        long total = mapper.countCoupons(kw);
        return new AdminCouponPage(items, total, p, s);
    }

    @Transactional
    public Coupon createCoupon(AuthUser authUser, AdminCouponRequest req) {
        AdminAccess.requireAdmin(authUser);
        Coupon coupon = toCoupon(new Coupon(), req);
        coupon.setCode(req.code().trim().toUpperCase());
        mapper.insertCoupon(coupon);
        return mapper.findCouponById(coupon.getId());
    }

    @Transactional
    public Coupon updateCoupon(AuthUser authUser, Long id, AdminCouponRequest req) {
        AdminAccess.requireAdmin(authUser);
        Coupon existing = mapper.findCouponById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
        }
        Coupon coupon = toCoupon(existing, req);
        coupon.setId(id);
        mapper.updateCoupon(coupon);
        return mapper.findCouponById(id);
    }

    @Transactional
    public UserCoupon issueCoupon(AuthUser authUser, Long couponId, Long userId) {
        AdminAccess.requireAdmin(authUser);
        Coupon coupon = mapper.findCouponById(couponId);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
        }
        return couponService.issue(userId, coupon.getCode());
    }

    private Coupon toCoupon(Coupon target, AdminCouponRequest req) {
        target.setName(req.name());
        target.setDiscountType(req.discountType());
        target.setDiscountValue(req.discountValue());
        target.setMinPurchase(req.minPurchase());
        target.setValidFrom(req.validFrom());
        target.setValidUntil(req.validUntil());
        target.setMaxIssue(req.maxIssue());
        target.setEnabled(Boolean.TRUE.equals(req.enabled()));
        return target;
    }

    // ── 리워드 이력 ──
    @Transactional(readOnly = true)
    public AdminRewardHistoryPage history(AuthUser authUser, Long userId, String eventCode,
                                          String keyword, int page, int size) {
        AdminAccess.requireAdmin(authUser);
        int p = Math.max(page, 1);
        int s = size <= 0 ? 20 : Math.min(size, 100);
        String kw = blankToNull(keyword);
        String ev = blankToNull(eventCode);
        return new AdminRewardHistoryPage(
                mapper.findHistory(userId, ev, kw, s, (long) (p - 1) * s),
                mapper.countHistory(userId, ev, kw),
                p, s);
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}

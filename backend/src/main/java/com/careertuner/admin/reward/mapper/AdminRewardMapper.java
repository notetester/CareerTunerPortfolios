package com.careertuner.admin.reward.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.reward.dto.AdminRewardHistoryRow;
import com.careertuner.reward.domain.Coupon;
import com.careertuner.reward.domain.RewardRule;
import com.careertuner.reward.domain.UserLevelPolicy;

/** 관리자 리워드 콘솔: 규칙/레벨/쿠폰/이력 관리. */
@Mapper
public interface AdminRewardMapper {

    // ── 적립 규칙 ──
    List<RewardRule> findAllRules();

    RewardRule findRuleById(@Param("id") Long id);

    int updateRule(RewardRule rule);

    int updateRuleEnabled(@Param("id") Long id, @Param("enabled") boolean enabled,
                          @Param("updatedBy") Long updatedBy);

    // ── 레벨 정책 ──
    List<UserLevelPolicy> findAllLevels();

    UserLevelPolicy findLevelById(@Param("id") Long id);

    int countLevelByNumber(@Param("level") int level);

    int countUsersByLevel(@Param("level") int level);

    void insertLevel(UserLevelPolicy level);

    int updateLevel(UserLevelPolicy level);

    int deleteLevel(@Param("id") Long id);

    // ── 쿠폰 ──
    List<Coupon> findCoupons(@Param("keyword") String keyword,
                             @Param("size") int size, @Param("offset") long offset);

    long countCoupons(@Param("keyword") String keyword);

    Coupon findCouponById(@Param("id") Long id);

    void insertCoupon(Coupon coupon);

    int updateCoupon(Coupon coupon);

    // ── 리워드 이력 ──
    List<AdminRewardHistoryRow> findHistory(@Param("userId") Long userId,
                                            @Param("eventCode") String eventCode,
                                            @Param("keyword") String keyword,
                                            @Param("size") int size, @Param("offset") long offset);

    long countHistory(@Param("userId") Long userId,
                      @Param("eventCode") String eventCode,
                      @Param("keyword") String keyword);
}

package com.careertuner.reward.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.service.CreditService;
import com.careertuner.reward.domain.RewardRule;
import com.careertuner.reward.domain.UserLevelPolicy;
import com.careertuner.reward.domain.UserRewardAccount;
import com.careertuner.reward.domain.UserRewardHistory;
import com.careertuner.reward.dto.MyRewardResponse;
import com.careertuner.reward.dto.RewardGrantResult;
import com.careertuner.reward.dto.RewardHistoryItem;
import com.careertuner.reward.mapper.RewardMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RewardServiceImpl implements RewardService {

    private static final int RECENT_HISTORY_LIMIT = 20;

    private final RewardMapper rewardMapper;
    private final CreditService creditService;
    private final CouponService couponService;

    @Override
    @Transactional
    public RewardGrantResult grant(Long userId, String eventCode, String refType, Long refId) {
        RewardRule rule = rewardMapper.findEnabledRuleByEvent(eventCode);
        if (rule == null) {
            // 규칙이 없거나 관리자가 off 로 둔 상태 — 미적립(집행 경로 무변경).
            return RewardGrantResult.skipped(eventCode, "NO_RULE");
        }
        if (rule.getDailyCap() != null) {
            int today = rewardMapper.countTodayGrantsByEvent(userId, eventCode);
            if (today >= rule.getDailyCap()) {
                return RewardGrantResult.skipped(eventCode, "DAILY_CAP");
            }
        }
        int point = Math.max(0, rule.getPointAmount());
        int credit = Math.max(0, rule.getCreditAmount());
        if (point == 0 && credit == 0) {
            return RewardGrantResult.skipped(eventCode, "NOTHING_TO_GRANT");
        }

        UserRewardAccount account = rewardMapper.findAccount(userId);
        if (account == null) {
            return RewardGrantResult.skipped(eventCode, "NO_USER");
        }
        int levelBefore = account.getUserLevel();

        if (point > 0) {
            rewardMapper.addActivityPoint(userId, point);
        }
        if (credit > 0) {
            creditService.grantCredit(userId, credit, "REWARD", eventCode, rule.getName());
        }

        rewardMapper.insertHistory(UserRewardHistory.builder()
                .userId(userId)
                .eventCode(eventCode)
                .pointDelta(point)
                .creditDelta(credit)
                .levelBefore(levelBefore)
                .levelAfter(levelBefore)
                .refType(refType)
                .refId(refId)
                .reason(rule.getName())
                .build());

        int newPoint = account.getActivityPoint() + point;
        List<UserLevelPolicy> levels = rewardMapper.findActiveLevelsOrdered();
        int newLevel = resolveLevel(levels, newPoint, levelBefore);
        boolean leveledUp = newLevel > levelBefore;
        if (leveledUp) {
            applyLevelUps(userId, levels, levelBefore, newLevel);
        }
        return new RewardGrantResult(eventCode, true, point, credit, leveledUp, levelBefore, newLevel, null);
    }

    /** 누적 포인트에 해당하는 최고 레벨을 구한다. 절대 강등하지 않는다(>= 현재 레벨). */
    private int resolveLevel(List<UserLevelPolicy> levels, int point, int currentLevel) {
        int resolved = currentLevel;
        for (UserLevelPolicy p : levels) {
            if (point >= p.getMinPoint() && p.getLevel() > resolved) {
                resolved = p.getLevel();
            }
        }
        return resolved;
    }

    /** fromLevel(제외) ~ toLevel(포함) 사이 넘긴 레벨마다 레벨업 보상/쿠폰을 지급한다. */
    private void applyLevelUps(Long userId, List<UserLevelPolicy> levels, int fromLevel, int toLevel) {
        rewardMapper.updateUserLevel(userId, toLevel);
        for (UserLevelPolicy p : levels) {
            if (p.getLevel() <= fromLevel || p.getLevel() > toLevel) {
                continue;
            }
            int bonus = Math.max(0, p.getLevelupCredit());
            if (bonus > 0) {
                creditService.grantCredit(userId, bonus, "LEVELUP_BONUS", "LEVEL_UP",
                        "레벨 " + p.getLevel() + "(" + p.getLevelName() + ") 달성 보상");
            }
            if (p.getLevelupCouponCode() != null && !p.getLevelupCouponCode().isBlank()) {
                couponService.issueQuietly(userId, p.getLevelupCouponCode());
            }
            rewardMapper.insertHistory(UserRewardHistory.builder()
                    .userId(userId)
                    .eventCode("LEVEL_UP")
                    .pointDelta(0)
                    .creditDelta(bonus)
                    .levelBefore(p.getLevel() - 1)
                    .levelAfter(p.getLevel())
                    .refType("LEVEL")
                    .refId((long) p.getLevel())
                    .reason("레벨 " + p.getLevel() + "(" + p.getLevelName() + ") 달성")
                    .build());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MyRewardResponse myReward(Long userId) {
        UserRewardAccount account = rewardMapper.findAccount(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        List<UserLevelPolicy> levels = rewardMapper.findActiveLevelsOrdered();

        String levelName = "레벨 " + account.getUserLevel();
        UserLevelPolicy next = null;
        for (UserLevelPolicy p : levels) {
            if (p.getLevel() == account.getUserLevel()) {
                levelName = p.getLevelName();
            }
            if (p.getMinPoint() > account.getActivityPoint() && (next == null || p.getMinPoint() < next.getMinPoint())) {
                next = p;
            }
        }

        List<RewardHistoryItem> recent = new ArrayList<>();
        for (UserRewardHistory h : rewardMapper.findRecentHistoryByUser(userId, RECENT_HISTORY_LIMIT)) {
            recent.add(new RewardHistoryItem(h.getId(), h.getEventCode(), h.getPointDelta(),
                    h.getCreditDelta(), h.getLevelAfter(), h.getReason(), h.getCreatedAt()));
        }

        return new MyRewardResponse(
                account.getActivityPoint(),
                account.getUserLevel(),
                levelName,
                next == null ? null : next.getLevel(),
                next == null ? null : next.getLevelName(),
                next == null ? null : next.getMinPoint() - account.getActivityPoint(),
                account.getCredit(),
                recent);
    }
}

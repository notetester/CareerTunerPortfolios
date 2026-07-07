package com.careertuner.reward.dto;

import java.util.List;

/** 마이페이지 "내 리워드/레벨" 응답. */
public record MyRewardResponse(
        int activityPoint,
        int level,
        String levelName,
        Integer nextLevel,
        String nextLevelName,
        Integer pointToNextLevel,
        int credit,
        List<RewardHistoryItem> recentHistory
) {
}

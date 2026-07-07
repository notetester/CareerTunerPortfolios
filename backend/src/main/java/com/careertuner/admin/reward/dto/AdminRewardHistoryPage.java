package com.careertuner.admin.reward.dto;

import java.util.List;

public record AdminRewardHistoryPage(
        List<AdminRewardHistoryRow> items,
        long total,
        int page,
        int size
) {
}

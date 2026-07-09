package com.careertuner.reward.dto;

import java.time.LocalDateTime;

public record RewardHistoryItem(
        Long id,
        String eventCode,
        int pointDelta,
        int creditDelta,
        Integer levelAfter,
        String reason,
        LocalDateTime createdAt
) {
}

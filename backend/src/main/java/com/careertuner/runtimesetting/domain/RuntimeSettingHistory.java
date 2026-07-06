package com.careertuner.runtimesetting.domain;

import java.time.LocalDateTime;

/** 런타임 설정 변경 이력 1건. */
public record RuntimeSettingHistory(
        Long id,
        Long settingId,
        String settingKey,
        int versionNo,
        String changeType,
        Long actorUserId,
        String beforeValue,
        String afterValue,
        String beforeFallback,
        String afterFallback,
        LocalDateTime createdAt) {
}

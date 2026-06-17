package com.careertuner.notification.dto;

import java.util.Map;

/** 알림 설정 변경 요청. null 필드는 기존값 유지. */
public record NotificationPreferenceUpdateRequest(
        Boolean pushEnabled,
        Boolean emailEnabled,
        Map<String, Boolean> categories,
        String quietHoursStart,
        String quietHoursEnd
) {}

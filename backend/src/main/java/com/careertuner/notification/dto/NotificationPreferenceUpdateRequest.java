package com.careertuner.notification.dto;

import java.util.Map;

import jakarta.validation.constraints.Pattern;

/** 알림 설정 변경 요청. null 필드는 기존값 유지. */
public record NotificationPreferenceUpdateRequest(
        Boolean pushEnabled,
        Boolean emailEnabled,
        Map<String, Boolean> categories,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "HH:mm 형식이어야 합니다")
        String quietHoursStart,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "HH:mm 형식이어야 합니다")
        String quietHoursEnd
) {}

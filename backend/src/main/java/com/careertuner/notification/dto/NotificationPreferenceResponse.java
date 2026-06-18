package com.careertuner.notification.dto;

import java.util.Map;

/** 알림 설정 응답. categories 는 카테고리코드→수신여부. */
public record NotificationPreferenceResponse(
        boolean pushEnabled,
        boolean emailEnabled,
        Map<String, Boolean> categories,
        String quietHoursStart,
        String quietHoursEnd,
        boolean pushDeviceRegistered
) {}

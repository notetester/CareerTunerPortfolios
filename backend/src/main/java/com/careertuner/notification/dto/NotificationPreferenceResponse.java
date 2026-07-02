package com.careertuner.notification.dto;

import java.util.Map;

/** 알림 설정 응답. categories 는 카테고리코드→수신여부. */
public record NotificationPreferenceResponse(
        boolean pushEnabled,
        boolean emailEnabled,
        Map<String, Boolean> categories,
        Map<String, NotificationRulePreference> rules,
        String quietHoursStart,
        String quietHoursEnd,
        boolean pushDeviceRegistered
) {

    public boolean ruleEnabled(String type) {
        NotificationRulePreference rule = rules != null ? rules.get(type) : null;
        return rule == null || rule.isEnabled();
    }

    public boolean channelEnabled(String type, String channel) {
        NotificationRulePreference rule = rules != null ? rules.get(type) : null;
        return rule == null || rule.channelEnabled(channel);
    }
}

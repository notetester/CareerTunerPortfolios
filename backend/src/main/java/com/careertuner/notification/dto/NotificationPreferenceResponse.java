package com.careertuner.notification.dto;

import java.util.List;
import java.util.Map;

/** 알림 설정 응답. categories 는 카테고리코드→수신여부, keywords 는 언급 감지 키워드. */
public record NotificationPreferenceResponse(
        boolean pushEnabled,
        boolean emailEnabled,
        Map<String, Boolean> categories,
        Map<String, NotificationRulePreference> rules,
        List<String> keywords,
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

    public boolean senderEnabled(String type, String relation) {
        NotificationRulePreference rule = rules != null ? rules.get(type) : null;
        return rule == null || rule.senderEnabled(relation);
    }
}

package com.careertuner.notification.dto;

/** 알림 이벤트(type)별 활성 여부와 플랫폼별 전달 채널 설정. */
public record NotificationRulePreference(
        Boolean enabled,
        NotificationChannelPreference channels
) {

    public static NotificationRulePreference enabledAll() {
        return new NotificationRulePreference(true, NotificationChannelPreference.enabledAll());
    }

    public NotificationRulePreference merge(NotificationRulePreference update) {
        if (update == null) {
            return this;
        }
        NotificationChannelPreference baseChannels = channels != null
                ? channels
                : NotificationChannelPreference.enabledAll();
        return new NotificationRulePreference(
                update.enabled != null ? update.enabled : enabled,
                baseChannels.merge(update.channels));
    }

    public boolean isEnabled() {
        return !Boolean.FALSE.equals(enabled);
    }

    public boolean channelEnabled(String channel) {
        if (!isEnabled()) {
            return false;
        }
        return channels == null || channels.isEnabled(channel);
    }
}

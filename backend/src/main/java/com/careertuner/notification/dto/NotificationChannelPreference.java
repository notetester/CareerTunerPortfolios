package com.careertuner.notification.dto;

/** 알림 이벤트별 전달 채널 설정. null 은 기존/기본값 유지에 사용한다. */
public record NotificationChannelPreference(
        Boolean webToast,
        Boolean webPush,
        Boolean mobilePush,
        Boolean mobileSound,
        Boolean mobileVibration,
        Boolean desktopToast,
        Boolean desktopTaskbar
) {

    public static NotificationChannelPreference enabledAll() {
        return new NotificationChannelPreference(true, true, true, true, true, true, true);
    }

    public NotificationChannelPreference merge(NotificationChannelPreference update) {
        if (update == null) {
            return this;
        }
        return new NotificationChannelPreference(
                update.webToast != null ? update.webToast : webToast,
                update.webPush != null ? update.webPush : webPush,
                update.mobilePush != null ? update.mobilePush : mobilePush,
                update.mobileSound != null ? update.mobileSound : mobileSound,
                update.mobileVibration != null ? update.mobileVibration : mobileVibration,
                update.desktopToast != null ? update.desktopToast : desktopToast,
                update.desktopTaskbar != null ? update.desktopTaskbar : desktopTaskbar);
    }

    public boolean isEnabled(String channel) {
        return switch (channel) {
            case "webToast" -> !Boolean.FALSE.equals(webToast);
            case "webPush" -> !Boolean.FALSE.equals(webPush);
            case "mobilePush" -> !Boolean.FALSE.equals(mobilePush);
            case "mobileSound" -> !Boolean.FALSE.equals(mobileSound);
            case "mobileVibration" -> !Boolean.FALSE.equals(mobileVibration);
            case "desktopToast" -> !Boolean.FALSE.equals(desktopToast);
            case "desktopTaskbar" -> !Boolean.FALSE.equals(desktopTaskbar);
            default -> true;
        };
    }
}

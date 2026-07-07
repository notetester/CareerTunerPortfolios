package com.careertuner.notification.push;

/**
 * 발송기로 전달되는 푸시 1건의 내용.
 * androidChannelId 는 수신자의 소리/진동 설정(mobileSound/mobileVibration)을 Android 알림 채널로 매핑한 값 —
 * 앱(Capacitor)이 시작 시 같은 id 로 채널 4종을 만들어 두고, FCM 메시지가 채널을 골라 소리/진동을 제어한다.
 */
public record PushMessage(
        String title,
        String body,
        String link,
        String androidChannelId
) {

    /** 소리+진동(기본). */
    public static final String CHANNEL_DEFAULT = "ct_alerts";
    /** 소리만. */
    public static final String CHANNEL_SOUND_ONLY = "ct_alerts_sound";
    /** 진동만. */
    public static final String CHANNEL_VIBRATE_ONLY = "ct_alerts_vibrate";
    /** 무음·무진동. */
    public static final String CHANNEL_SILENT = "ct_alerts_silent";

    public static String channelFor(boolean sound, boolean vibration) {
        if (sound && vibration) {
            return CHANNEL_DEFAULT;
        }
        if (sound) {
            return CHANNEL_SOUND_ONLY;
        }
        if (vibration) {
            return CHANNEL_VIBRATE_ONLY;
        }
        return CHANNEL_SILENT;
    }

    public String safeLink() {
        return link == null || link.isBlank() ? "/" : link;
    }

    public String safeBody() {
        return body == null ? "" : body;
    }
}

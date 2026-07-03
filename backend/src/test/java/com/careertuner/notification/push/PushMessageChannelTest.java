package com.careertuner.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 모바일 소리/진동 설정 → Android 알림 채널 매핑 검증(앱의 채널 생성 id 와 계약). */
class PushMessageChannelTest {

    @Test
    void 소리진동_조합별_채널() {
        assertThat(PushMessage.channelFor(true, true)).isEqualTo(PushMessage.CHANNEL_DEFAULT);
        assertThat(PushMessage.channelFor(true, false)).isEqualTo(PushMessage.CHANNEL_SOUND_ONLY);
        assertThat(PushMessage.channelFor(false, true)).isEqualTo(PushMessage.CHANNEL_VIBRATE_ONLY);
        assertThat(PushMessage.channelFor(false, false)).isEqualTo(PushMessage.CHANNEL_SILENT);
    }

    @Test
    void 링크와_본문_안전값() {
        PushMessage empty = new PushMessage("t", null, null, null);
        assertThat(empty.safeBody()).isEmpty();
        assertThat(empty.safeLink()).isEqualTo("/");
        PushMessage filled = new PushMessage("t", "b", "/interview?session=3", PushMessage.CHANNEL_DEFAULT);
        assertThat(filled.safeLink()).isEqualTo("/interview?session=3");
    }
}

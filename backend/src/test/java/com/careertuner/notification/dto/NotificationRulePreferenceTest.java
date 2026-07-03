package com.careertuner.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 알림 규칙 설정의 발신자 관계(senders) 병합·판정 검증.
 *
 * <p>규칙: 저장되지 않은 관계 키는 켜짐(true), 관계 미상(null)은 필터하지 않고 통과,
 * 규칙 자체가 꺼져 있으면 관계와 무관하게 거부.
 */
class NotificationRulePreferenceTest {

    @Test
    void 기본값은_모든_관계_수신() {
        NotificationRulePreference rule = NotificationRulePreference.enabledAll();
        assertThat(rule.senderEnabled("stranger")).isTrue();
        assertThat(rule.senderEnabled("friend")).isTrue();
        assertThat(rule.senderEnabled("company")).isTrue();
        assertThat(rule.senderEnabled("operator")).isTrue();
    }

    @Test
    void 관계미상은_통과() {
        NotificationRulePreference rule = new NotificationRulePreference(
                true, null, Map.of("stranger", false));
        assertThat(rule.senderEnabled(null)).isTrue();
    }

    @Test
    void 꺼진_관계만_거부() {
        NotificationRulePreference rule = new NotificationRulePreference(
                true, null, Map.of("stranger", false, "friend", true));
        assertThat(rule.senderEnabled("stranger")).isFalse();
        assertThat(rule.senderEnabled("friend")).isTrue();
        // 저장 안 된 키는 켜짐으로 본다
        assertThat(rule.senderEnabled("operator")).isTrue();
    }

    @Test
    void 규칙이_꺼지면_관계와_무관하게_거부() {
        NotificationRulePreference rule = new NotificationRulePreference(
                false, null, Map.of("friend", true));
        assertThat(rule.senderEnabled("friend")).isFalse();
        assertThat(rule.senderEnabled(null)).isFalse();
    }

    @Test
    void 병합은_요청된_관계만_덮어쓴다() {
        NotificationRulePreference base = NotificationRulePreference.enabledAll();
        NotificationRulePreference update = new NotificationRulePreference(
                null, null, Map.of("stranger", false));
        NotificationRulePreference merged = base.merge(update);
        assertThat(merged.senderEnabled("stranger")).isFalse();
        assertThat(merged.senderEnabled("friend")).isTrue();
        assertThat(merged.isEnabled()).isTrue();
    }

    @Test
    void 알수없는_관계키는_병합에서_무시() {
        NotificationRulePreference base = NotificationRulePreference.enabledAll();
        NotificationRulePreference update = new NotificationRulePreference(
                null, null, Map.of("이상한키", false));
        NotificationRulePreference merged = base.merge(update);
        assertThat(merged.senders()).doesNotContainKey("이상한키");
    }
}

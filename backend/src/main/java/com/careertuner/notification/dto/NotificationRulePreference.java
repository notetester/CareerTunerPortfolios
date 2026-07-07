package com.careertuner.notification.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import com.careertuner.notification.push.SenderRelation;

/**
 * 알림 이벤트(type)별 활성 여부·플랫폼별 전달 채널·발신자 관계별 수신 설정.
 * senders 는 관계 기반 알림(댓글·답글·쪽지·채팅 등)에만 의미가 있으며,
 * 저장되지 않은 관계 키는 켜짐(true)으로 본다.
 */
public record NotificationRulePreference(
        Boolean enabled,
        NotificationChannelPreference channels,
        Map<String, Boolean> senders
) {

    public static NotificationRulePreference enabledAll() {
        return new NotificationRulePreference(true, NotificationChannelPreference.enabledAll(), allSendersEnabled());
    }

    public static Map<String, Boolean> allSendersEnabled() {
        Map<String, Boolean> all = new LinkedHashMap<>();
        for (String relation : SenderRelation.ALL) {
            all.put(relation, Boolean.TRUE);
        }
        return all;
    }

    public NotificationRulePreference merge(NotificationRulePreference update) {
        if (update == null) {
            return this;
        }
        NotificationChannelPreference baseChannels = channels != null
                ? channels
                : NotificationChannelPreference.enabledAll();
        Map<String, Boolean> mergedSenders = new LinkedHashMap<>(senders != null ? senders : allSendersEnabled());
        if (update.senders != null) {
            for (String relation : SenderRelation.ALL) {
                Boolean value = update.senders.get(relation);
                if (value != null) {
                    mergedSenders.put(relation, value);
                }
            }
        }
        return new NotificationRulePreference(
                update.enabled != null ? update.enabled : enabled,
                baseChannels.merge(update.channels),
                mergedSenders);
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

    /** 관계 미상(null)은 필터하지 않고 통과시킨다 — 안전하게 수신을 허용. */
    public boolean senderEnabled(String relation) {
        if (!isEnabled()) {
            return false;
        }
        if (relation == null || senders == null) {
            return true;
        }
        return !Boolean.FALSE.equals(senders.get(relation));
    }
}

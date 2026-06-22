package com.careertuner.notification.push;

import org.springframework.stereotype.Component;

import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.mapper.PushSubscriptionMapper;
import com.careertuner.notification.service.NotificationPreferenceService;

import lombok.RequiredArgsConstructor;

/**
 * 알림 1건을 사용자의 등록 기기로 푸시한다(best-effort).
 * 푸시 비활성/카테고리 off/기기 없음/발송 실패 시 조용히 건너뛰어 알림 생성 흐름을 끊지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private final PushSubscriptionMapper pushSubscriptionMapper;
    private final NotificationPreferenceService preferenceService;
    private final PushSender pushSender;

    public void dispatch(Notification notification) {
        if (notification == null || notification.getUserId() == null) {
            return;
        }
        try {
            var pref = preferenceService.get(notification.getUserId());
            if (!pref.pushEnabled()) {
                return;
            }
            String category = NotificationCategories.of(notification.getType());
            if (Boolean.FALSE.equals(pref.categories().get(category))) {
                return;
            }
            for (var subscription : pushSubscriptionMapper.findByUserId(notification.getUserId())) {
                pushSender.send(subscription, notification.getTitle(), notification.getMessage(), notification.getLink());
            }
        } catch (RuntimeException ex) {
            // 푸시는 보조 채널 — 실패해도 in-app 알림에는 영향 주지 않는다.
        }
    }
}

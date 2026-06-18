package com.careertuner.notification.push;

import com.careertuner.notification.domain.PushSubscription;

/**
 * 실제 푸시 전송 경계. 기본 구현(LoggingPushSender)은 키 미설정 시 로그만 남긴다.
 * Web Push(VAPID)·FCM·APNs 발송기를 추가하면 이 인터페이스 구현으로 교체/확장한다.
 */
public interface PushSender {

    void send(PushSubscription subscription, String title, String body, String link);
}

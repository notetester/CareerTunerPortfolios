package com.careertuner.notification.push;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.careertuner.notification.domain.PushSubscription;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 기본 푸시 발송기 — 구독 종류로 실제 채널을 라우팅한다.
 *  - WEB        → VapidWebPushClient(있으면) 로 web push, 없으면 로깅 폴백
 *  - FCM/APNS   → FcmPushClient(있으면) 로 네이티브 push, 없으면 로깅 폴백
 * 어떤 경우에도 예외를 삼켜 알림 생성 흐름을 끊지 않는다.
 */
@Primary
@Component
@Slf4j
public class DefaultPushSender implements PushSender {

    private final ObjectProvider<VapidWebPushClient> webPushClient;
    private final ObjectProvider<FcmPushClient> fcmPushClient;
    private final LoggingPushSender loggingPushSender;
    private final ObjectMapper objectMapper;

    public DefaultPushSender(ObjectProvider<VapidWebPushClient> webPushClient,
                             ObjectProvider<FcmPushClient> fcmPushClient,
                             LoggingPushSender loggingPushSender,
                             ObjectMapper objectMapper) {
        this.webPushClient = webPushClient;
        this.fcmPushClient = fcmPushClient;
        this.loggingPushSender = loggingPushSender;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(PushSubscription subscription, String title, String body, String link) {
        String kind = subscription.getKind() == null ? "" : subscription.getKind().toUpperCase();
        if ("WEB".equals(kind)) {
            VapidWebPushClient client = webPushClient.getIfAvailable();
            if (client != null) {
                try {
                    client.send(subscription, buildPayload(title, body, link));
                    return;
                } catch (Exception ex) {
                    log.debug("[push] web push 발송 실패(무시): {}", ex.getMessage());
                    return;
                }
            }
        } else if ("FCM".equals(kind) || "APNS".equals(kind)) {
            FcmPushClient client = fcmPushClient.getIfAvailable();
            if (client != null && client.isReady()) {
                try {
                    client.send(subscription, title, body, link);
                    return;
                } catch (Exception ex) {
                    log.debug("[push] FCM 발송 실패(무시): {}", ex.getMessage());
                    return;
                }
            }
        }
        // 발송기 미설정(키/서비스계정 없음) → 로깅 폴백(설정 시 자동 활성).
        loggingPushSender.send(subscription, title, body, link);
    }

    private String buildPayload(String title, String body, String link) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("body", body == null ? "" : body);
        payload.put("url", link == null || link.isBlank() ? "/" : link);
        return objectMapper.writeValueAsString(payload);
    }
}

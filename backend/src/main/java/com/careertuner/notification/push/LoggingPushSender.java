package com.careertuner.notification.push;

import org.springframework.stereotype.Component;

import com.careertuner.notification.domain.PushSubscription;

import lombok.extern.slf4j.Slf4j;

/**
 * 기본 푸시 발송기 — 키/발송 인프라가 없을 때 동작.
 * 실제 전송 대신 의도를 로그로 남겨 흐름이 끊기지 않게 한다(OpenAI/RAG 와 동일한 graceful 패턴).
 * 실제 발송기(Web Push/FCM)를 추가할 때는 그 빈에 @Primary 를 붙여 이 기본 빈을 대체한다.
 */
@Slf4j
@Component
public class LoggingPushSender implements PushSender {

    @Override
    public void send(PushSubscription subscription, String title, String body, String link) {
        String token = subscription.getToken();
        String masked = token == null ? "?" : token.substring(0, Math.min(12, token.length())) + "…";
        log.info("[push] 발송기 미설정 — {} 기기({})로 보낼 알림: '{}' / link={}",
                subscription.getKind(), masked, title, link);
    }
}

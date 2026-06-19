package com.careertuner.notification.push;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.careertuner.notification.domain.PushSubscription;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;

/**
 * Web Push(VAPID) 발송 클라이언트. VAPID 키가 설정됐을 때만 빈으로 생성된다.
 * 브라우저/PWA 가 만든 구독(endpoint + p256dh + auth)으로 암호화 페이로드를 전송한다.
 */
@Component
@ConditionalOnProperty(prefix = "careertuner.push.vapid", name = {"public-key", "private-key"})
public class VapidWebPushClient {

    private final PushService pushService;

    public VapidWebPushClient(
            @Value("${careertuner.push.vapid.public-key}") String publicKey,
            @Value("${careertuner.push.vapid.private-key}") String privateKey,
            @Value("${careertuner.push.vapid.subject:mailto:no-reply@careertuner.local}") String subject)
            throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.pushService = new PushService(publicKey, privateKey, subject);
    }

    /** 단일 웹 구독으로 payload(JSON)를 발송한다. 실패 시 예외를 던지며, 호출부가 best-effort 처리한다. */
    public void send(PushSubscription subscription, String payload) throws Exception {
        Subscription.Keys keys = new Subscription.Keys(subscription.getP256dh(), subscription.getAuth());
        Subscription webSubscription = new Subscription(subscription.getToken(), keys);
        pushService.send(new Notification(webSubscription, payload));
    }
}

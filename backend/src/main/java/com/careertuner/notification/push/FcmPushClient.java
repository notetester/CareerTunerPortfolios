package com.careertuner.notification.push;

import java.io.FileInputStream;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.careertuner.notification.domain.PushSubscription;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;

import lombok.extern.slf4j.Slf4j;

/**
 * 네이티브(FCM/APNs) 푸시 발송 클라이언트. Firebase 서비스계정(JSON 파일 경로)이 설정됐을 때만 실제 발송한다.
 * Capacitor 앱이 등록한 디바이스 토큰(PushSubscription.token, kind=FCM)으로 메시지를 전송한다.
 * APNs(iOS)도 FCM 을 경유하므로 같은 토큰 경로를 쓴다.
 *
 * 빈은 항상 생성되지만, 경로 미설정/로드 실패 시 {@link #isReady()}=false 로 degrade 하여
 * 발송 대신 로깅 폴백을 타게 한다(OpenAI/RAG 와 동일한 graceful 패턴). 잘못된 경로가 컨텍스트를 깨지 않는다.
 */
@Component
@Slf4j
public class FcmPushClient {

    private static final String APP_NAME = "careertuner-push";

    /** 서비스계정이 정상 로드됐을 때만 non-null. */
    private final FirebaseMessaging messaging;

    public FcmPushClient(@Value("${careertuner.push.fcm.service-account:}") String serviceAccountPath) {
        FirebaseMessaging ready = null;
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            try (InputStream credentials = new FileInputStream(serviceAccountPath)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentials))
                        .build();
                // 이름을 지정해 기본 FirebaseApp 과 충돌하지 않게 한다(중복 초기화 방지).
                FirebaseApp app = FirebaseApp.getApps().stream()
                        .filter(a -> APP_NAME.equals(a.getName()))
                        .findFirst()
                        .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
                ready = FirebaseMessaging.getInstance(app);
                log.info("[push] FCM 발송기 활성화(service-account 로드 완료)");
            } catch (Exception ex) {
                log.warn("[push] FCM 서비스계정 로드 실패 — 네이티브 푸시는 로깅 폴백: {}", ex.getMessage());
            }
        }
        this.messaging = ready;
    }

    /** 서비스계정이 설정·로드돼 실제 발송 가능한 상태인지. */
    public boolean isReady() {
        return messaging != null;
    }

    /** 단일 디바이스 토큰으로 알림을 발송한다. 실패 시 예외를 던지며 호출부가 best-effort 처리한다. */
    public void send(PushSubscription subscription, String title, String body, String link) throws Exception {
        String url = (link == null || link.isBlank()) ? "/" : link;
        Message message = Message.builder()
                .setToken(subscription.getToken())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body == null ? "" : body)
                        .build())
                .putData("url", url)
                // 클릭 시 이동 경로 — 네이티브/웹뷰 양쪽에서 활용.
                .setAndroidConfig(AndroidConfig.builder()
                        .putData("url", url)
                        .build())
                .setWebpushConfig(WebpushConfig.builder()
                        .setFcmOptions(WebpushFcmOptions.withLink(url))
                        .build())
                .build();
        messaging.send(message);
    }
}

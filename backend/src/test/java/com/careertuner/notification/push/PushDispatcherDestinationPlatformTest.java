package com.careertuner.notification.push;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.domain.NotificationDestinationPlatform;
import com.careertuner.notification.domain.PushSubscription;
import com.careertuner.notification.dto.NotificationPreferenceResponse;
import com.careertuner.notification.mapper.PushSubscriptionMapper;
import com.careertuner.notification.service.NotificationPreferenceService;

class PushDispatcherDestinationPlatformTest {

    private final PushSubscriptionMapper subscriptionMapper = mock(PushSubscriptionMapper.class);
    private final NotificationPreferenceService preferenceService = mock(NotificationPreferenceService.class);
    private final PushSender pushSender = mock(PushSender.class);
    private PushDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new PushDispatcher(subscriptionMapper, preferenceService, pushSender);
    }

    @Test
    void desktopOnlyNotificationNeverRebroadcastsToWebOrFcm() {
        dispatcher.dispatch(Notification.builder()
                .userId(1L)
                .type("INTERVIEW_DISPATCH")
                .destinationPlatform(NotificationDestinationPlatform.DESKTOP)
                .build());

        verifyNoInteractions(preferenceService, subscriptionMapper, pushSender);
    }

    @Test
    void legacyNullDestinationKeepsAllPlatformPushBehavior() {
        NotificationPreferenceResponse preference = new NotificationPreferenceResponse(
                true, false, Map.of(), Map.of(), List.of(), null, null, true);
        PushSubscription subscription = PushSubscription.builder()
                .userId(2L)
                .kind("WEB")
                .token("token")
                .build();
        when(preferenceService.get(2L)).thenReturn(preference);
        when(subscriptionMapper.findByUserId(2L)).thenReturn(List.of(subscription));

        dispatcher.dispatch(Notification.builder()
                .userId(2L)
                .type("NOTICE")
                .title("공지")
                .build());

        verify(pushSender).send(org.mockito.ArgumentMatchers.eq(subscription),
                org.mockito.ArgumentMatchers.any(PushMessage.class));
    }

    @Test
    void mobileOnlyNotificationSkipsWebPushAndUsesNativeSubscription() {
        NotificationPreferenceResponse preference = new NotificationPreferenceResponse(
                true, false, Map.of(), Map.of(), List.of(), null, null, true);
        PushSubscription web = PushSubscription.builder()
                .userId(3L).kind("WEB").token("web-token").build();
        PushSubscription fcm = PushSubscription.builder()
                .userId(3L).kind("FCM").token("fcm-token").build();
        when(preferenceService.get(3L)).thenReturn(preference);
        when(subscriptionMapper.findByUserId(3L)).thenReturn(List.of(web, fcm));

        dispatcher.dispatch(Notification.builder()
                .userId(3L)
                .type("INTERVIEW_DISPATCH")
                .destinationPlatform(NotificationDestinationPlatform.MOBILE)
                .title("폰으로 전송")
                .build());

        verify(pushSender).send(org.mockito.ArgumentMatchers.eq(fcm),
                org.mockito.ArgumentMatchers.any(PushMessage.class));
        verifyNoMoreInteractions(pushSender);
    }

    @Test
    void webOnlyNotificationSkipsNativePushAndUsesWebSubscription() {
        NotificationPreferenceResponse preference = new NotificationPreferenceResponse(
                true, false, Map.of(), Map.of(), List.of(), null, null, true);
        PushSubscription web = PushSubscription.builder()
                .userId(4L).kind("WEB").token("web-token").build();
        PushSubscription fcm = PushSubscription.builder()
                .userId(4L).kind("FCM").token("fcm-token").build();
        when(preferenceService.get(4L)).thenReturn(preference);
        when(subscriptionMapper.findByUserId(4L)).thenReturn(List.of(web, fcm));

        dispatcher.dispatch(Notification.builder()
                .userId(4L)
                .type("NOTICE")
                .destinationPlatform(NotificationDestinationPlatform.WEB)
                .title("웹 전용 공지")
                .build());

        verify(pushSender).send(org.mockito.ArgumentMatchers.eq(web),
                org.mockito.ArgumentMatchers.any(PushMessage.class));
        verifyNoMoreInteractions(pushSender);
    }
}

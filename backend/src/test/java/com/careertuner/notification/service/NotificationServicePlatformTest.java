package com.careertuner.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.domain.NotificationDestinationPlatform;
import com.careertuner.notification.mapper.NotificationMapper;
import com.careertuner.privacy.service.PrivacyPolicyService;

class NotificationServicePlatformTest {

    private final NotificationMapper mapper = mock(NotificationMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SenderRelationResolver senderRelationResolver = mock(SenderRelationResolver.class);
    private final PrivacyPolicyService privacyPolicyService = mock(PrivacyPolicyService.class);
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(
                mapper, eventPublisher, senderRelationResolver, privacyPolicyService);
    }

    @Test
    void desktopListAndUnreadCountPassPlatformFilterToMapper() {
        Notification legacy = Notification.builder().id(91L).userId(7L).type("NOTICE").build();
        when(mapper.findByUserId(7L, "DESKTOP", 20, 20)).thenReturn(List.of(legacy));
        when(mapper.countByUserId(7L, "DESKTOP")).thenReturn(21);
        when(mapper.countUnreadByUserId(7L, "DESKTOP")).thenReturn(3);

        var page = service.getNotifications(7L, 1, 20, NotificationDestinationPlatform.DESKTOP);
        int unread = service.getUnreadCount(7L, NotificationDestinationPlatform.DESKTOP);

        assertThat(page.total()).isEqualTo(21);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.notifications()).singleElement()
                .satisfies(response -> assertThat(response.destinationPlatform())
                        .isEqualTo(NotificationDestinationPlatform.ALL));
        assertThat(unread).isEqualTo(3);
    }

    @Test
    void unspecifiedAndAllKeepLegacyUnfilteredQueries() {
        when(mapper.findByUserId(8L, null, 0, 10)).thenReturn(List.of());
        when(mapper.countByUserId(8L, null)).thenReturn(0);
        when(mapper.countUnreadByUserId(8L, null)).thenReturn(0);

        service.getNotifications(8L, 0, 10, null);
        service.getUnreadCount(8L, NotificationDestinationPlatform.ALL);

        verify(mapper).findByUserId(8L, null, 0, 10);
        verify(mapper).countByUserId(8L, null);
        verify(mapper).countUnreadByUserId(8L, null);
    }

    @Test
    void notificationWithoutDestinationIsPersistedAndPublishedAsAll() {
        Notification notification = Notification.builder()
                .userId(9L)
                .type("NOTICE")
                .title("공지")
                .build();
        ArgumentCaptor<Notification> inserted = ArgumentCaptor.forClass(Notification.class);

        service.notify(notification);

        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getDestinationPlatform())
                .isEqualTo(NotificationDestinationPlatform.ALL);
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}

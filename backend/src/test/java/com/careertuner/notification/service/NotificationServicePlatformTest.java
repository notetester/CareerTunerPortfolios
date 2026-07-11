package com.careertuner.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void desktopBulkMutationsPassPlatformFilterToMapper() {
        service.markAllAsRead(7L, NotificationDestinationPlatform.DESKTOP);
        service.deleteAll(7L, NotificationDestinationPlatform.DESKTOP);

        verify(mapper).markAllAsRead(7L, "DESKTOP");
        verify(mapper).deleteAllByUser(7L, "DESKTOP");
    }

    @Test
    void webOperationsPassWebPlatformFilterToMapper() {
        when(mapper.findByUserId(10L, "WEB", 0, 20)).thenReturn(List.of());
        when(mapper.countByUserId(10L, "WEB")).thenReturn(0);

        service.getNotifications(10L, 0, 20, NotificationDestinationPlatform.WEB);
        service.getUnreadCount(10L, NotificationDestinationPlatform.WEB);
        service.markAllAsRead(10L, NotificationDestinationPlatform.WEB);
        service.deleteAll(10L, NotificationDestinationPlatform.WEB);

        verify(mapper).findByUserId(10L, "WEB", 0, 20);
        verify(mapper).countByUserId(10L, "WEB");
        verify(mapper).countUnreadByUserId(10L, "WEB");
        verify(mapper).markAllAsRead(10L, "WEB");
        verify(mapper).deleteAllByUser(10L, "WEB");
    }

    @Test
    void unspecifiedAndAllKeepLegacyUnfilteredQueries() {
        when(mapper.findByUserId(8L, null, 0, 10)).thenReturn(List.of());
        when(mapper.countByUserId(8L, null)).thenReturn(0);
        when(mapper.countUnreadByUserId(8L, null)).thenReturn(0);

        service.getNotifications(8L, 0, 10, null);
        service.getUnreadCount(8L, NotificationDestinationPlatform.ALL);
        service.markAllAsRead(8L, null);
        service.deleteAll(8L, NotificationDestinationPlatform.ALL);

        verify(mapper).findByUserId(8L, null, 0, 10);
        verify(mapper).countByUserId(8L, null);
        verify(mapper).countUnreadByUserId(8L, null);
        verify(mapper).markAllAsRead(8L, null);
        verify(mapper).deleteAllByUser(8L, null);
    }

    @Test
    void notificationWithoutDestinationIsPersistedAndPublishedAsAll() {
        Notification notification = Notification.builder()
                .userId(9L)
                .type("NOTICE")
                .title("공지")
                .build();
        ArgumentCaptor<Notification> inserted = ArgumentCaptor.forClass(Notification.class);
        when(mapper.insert(notification)).thenReturn(1);

        service.notify(notification);

        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getDestinationPlatform())
                .isEqualTo(NotificationDestinationPlatform.ALL);
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void deletedOrOtherwiseInactiveRecipientDoesNotPublishPushEvent() {
        Notification notification = Notification.builder()
                .userId(9L)
                .type("NOTICE")
                .title("공지")
                .build();
        when(mapper.insert(notification)).thenReturn(0);

        service.notify(notification);

        verify(mapper).insert(notification);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void existingNotificationFromDeletedActorUsesUnlinkableTombstone() {
        Notification notification = Notification.builder()
                .id(1L)
                .userId(9L)
                .actorId(44L)
                .actorName("탈퇴한 사용자")
                .actorAvatarUrl("https://old.example/avatar.png")
                .actorStatus("DELETED")
                .type("COMMENT")
                .build();
        when(mapper.findByUserId(9L, null, 0, 20)).thenReturn(List.of(notification));
        when(mapper.countByUserId(9L, null)).thenReturn(1);

        var actor = service.getNotifications(9L, 0, 20, null).notifications().getFirst().actor();

        assertThat(actor.id()).isNull();
        assertThat(actor.name()).isEqualTo("탈퇴한 사용자");
        assertThat(actor.avatarUrl()).isNull();
    }
}

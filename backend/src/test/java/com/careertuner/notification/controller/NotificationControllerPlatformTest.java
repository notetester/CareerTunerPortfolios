package com.careertuner.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.common.security.AuthUser;
import com.careertuner.notification.domain.NotificationDestinationPlatform;
import com.careertuner.notification.dto.NotificationPageResponse;
import com.careertuner.notification.push.PushService;
import com.careertuner.notification.service.NotificationPreferenceService;
import com.careertuner.notification.service.NotificationService;

class NotificationControllerPlatformTest {

    private final NotificationService service = mock(NotificationService.class);
    private final NotificationController controller = new NotificationController(
            service, mock(NotificationPreferenceService.class), mock(PushService.class));
    private final AuthUser user = new AuthUser(17L, "desktop@example.com", "USER");

    @Test
    void desktopPlatformIsForwardedForListAndUnreadCount() {
        var page = new NotificationPageResponse(List.of(), 0, 0, 20, false);
        when(service.getNotifications(17L, 0, 20, NotificationDestinationPlatform.DESKTOP))
                .thenReturn(page);
        when(service.getUnreadCount(17L, NotificationDestinationPlatform.DESKTOP)).thenReturn(2);

        var listResponse = controller.getNotifications(
                0, 20, NotificationDestinationPlatform.DESKTOP, user);
        var countResponse = controller.getUnreadCount(NotificationDestinationPlatform.DESKTOP, user);

        assertThat(listResponse.data()).isSameAs(page);
        assertThat(countResponse.data()).isEqualTo(2);
    }

    @Test
    void desktopPlatformIsForwardedForBulkMutations() {
        controller.markAllAsRead(NotificationDestinationPlatform.DESKTOP, user);
        controller.deleteAll(NotificationDestinationPlatform.DESKTOP, user);

        verify(service).markAllAsRead(17L, NotificationDestinationPlatform.DESKTOP);
        verify(service).deleteAll(17L, NotificationDestinationPlatform.DESKTOP);
    }

    @Test
    void webPlatformIsForwardedForAllNotificationOperations() {
        controller.getNotifications(0, 20, NotificationDestinationPlatform.WEB, user);
        controller.getUnreadCount(NotificationDestinationPlatform.WEB, user);
        controller.markAllAsRead(NotificationDestinationPlatform.WEB, user);
        controller.deleteAll(NotificationDestinationPlatform.WEB, user);

        verify(service).getNotifications(17L, 0, 20, NotificationDestinationPlatform.WEB);
        verify(service).getUnreadCount(17L, NotificationDestinationPlatform.WEB);
        verify(service).markAllAsRead(17L, NotificationDestinationPlatform.WEB);
        verify(service).deleteAll(17L, NotificationDestinationPlatform.WEB);
    }

    @Test
    void omittedPlatformKeepsUnfilteredWebBehavior() {
        controller.getNotifications(1, 10, null, user);
        controller.getUnreadCount(null, user);
        controller.markAllAsRead(null, user);
        controller.deleteAll(null, user);

        verify(service).getNotifications(17L, 1, 10, null);
        verify(service).getUnreadCount(17L, null);
        verify(service).markAllAsRead(17L, null);
        verify(service).deleteAll(17L, null);
    }
}

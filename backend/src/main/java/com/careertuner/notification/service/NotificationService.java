package com.careertuner.notification.service;

import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.domain.NotificationDestinationPlatform;
import com.careertuner.notification.dto.NotificationPageResponse;

public interface NotificationService {

    /** 알림을 저장하고 사용자의 등록 기기로 푸시(best-effort)한다. 모든 알림 생성의 단일 진입점. */
    void notify(Notification notification);

    NotificationPageResponse getNotifications(
            Long userId, int page, int size, NotificationDestinationPlatform platform);

    default NotificationPageResponse getNotifications(Long userId, int page, int size) {
        return getNotifications(userId, page, size, null);
    }

    int getUnreadCount(Long userId, NotificationDestinationPlatform platform);

    default int getUnreadCount(Long userId) {
        return getUnreadCount(userId, null);
    }

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId, NotificationDestinationPlatform platform);

    default void markAllAsRead(Long userId) {
        markAllAsRead(userId, null);
    }

    void delete(Long notificationId, Long userId);

    void deleteAll(Long userId, NotificationDestinationPlatform platform);

    default void deleteAll(Long userId) {
        deleteAll(userId, null);
    }
}

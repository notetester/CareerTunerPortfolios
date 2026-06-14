package com.careertuner.notification.service;

import com.careertuner.notification.dto.NotificationPageResponse;

public interface NotificationService {

    NotificationPageResponse getNotifications(Long userId, int page, int size);

    int getUnreadCount(Long userId);

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId);
}

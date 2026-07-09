package com.careertuner.admin.notification.service;

import java.util.List;

import com.careertuner.admin.notification.dto.AdminNotificationResponse;
import com.careertuner.admin.notification.dto.AdminNotificationStatsResponse;
import com.careertuner.common.security.AuthUser;

public interface AdminNotificationService {

    List<AdminNotificationResponse> getNotifications(AuthUser authUser, int size);

    AdminNotificationStatsResponse getStats(AuthUser authUser);
}

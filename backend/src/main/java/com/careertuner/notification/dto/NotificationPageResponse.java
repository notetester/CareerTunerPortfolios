package com.careertuner.notification.dto;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> notifications,
        int total,
        int page,
        int size,
        boolean hasNext
) {}

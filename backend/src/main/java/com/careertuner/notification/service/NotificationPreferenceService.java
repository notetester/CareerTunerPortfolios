package com.careertuner.notification.service;

import com.careertuner.notification.dto.NotificationPreferenceResponse;
import com.careertuner.notification.dto.NotificationPreferenceUpdateRequest;

public interface NotificationPreferenceService {

    /** 사용자 알림 설정(미설정 시 기본값: 전부 수신·푸시 on). */
    NotificationPreferenceResponse get(Long userId);

    NotificationPreferenceResponse update(Long userId, NotificationPreferenceUpdateRequest request);
}

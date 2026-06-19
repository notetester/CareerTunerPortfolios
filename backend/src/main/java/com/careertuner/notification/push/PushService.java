package com.careertuner.notification.push;

import com.careertuner.notification.dto.PushSubscribeRequest;

public interface PushService {

    void subscribe(Long userId, PushSubscribeRequest request, String userAgent);

    void unsubscribe(Long userId, String token);
}

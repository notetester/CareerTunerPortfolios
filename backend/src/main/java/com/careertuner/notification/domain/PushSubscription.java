package com.careertuner.notification.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 푸시 구독(기기별). kind=WEB 은 web push, FCM/APNS 는 네이티브 디바이스 토큰. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscription {

    private Long id;
    private Long userId;
    private String kind;
    private String token;
    private String p256dh;
    private String auth;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}

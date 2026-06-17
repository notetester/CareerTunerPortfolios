package com.careertuner.notification.dto;

import jakarta.validation.constraints.NotBlank;

/** 푸시 구독 등록 요청. kind=WEB|FCM|APNS. WEB 은 endpoint(token)+p256dh+auth. */
public record PushSubscribeRequest(
        @NotBlank String kind,
        @NotBlank String token,
        String p256dh,
        String auth
) {}

package com.careertuner.auth.dto;

import java.time.LocalDateTime;

public record MfaChallengeResponse(
        String challengeToken,
        String status,
        String ipAddress,
        String userAgent,
        String deviceName,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}

package com.careertuner.auth.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaChallenge {
    private Long id;
    private Long userId;
    private String challengeToken;
    private String challengeType;
    private String deliveryType;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime approvedAt;
    private LocalDateTime verifiedAt;
    private String ipAddress;
    private String userAgent;
    private String deviceName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

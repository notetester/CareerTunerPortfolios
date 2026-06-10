package com.careertuner.auth.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** user_login_history table row for authentication audit logs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginHistory {

    private Long id;
    private Long userId;
    private String eventType;
    private String authProvider;
    private String loginMethod;
    private String loginIdentifier;
    private boolean success;
    private String failReason;
    private String ipAddress;
    private String userAgent;
    private String requestUri;
    private LocalDateTime createdAt;
}

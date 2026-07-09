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
public class UserMfaSetting {
    private Long id;
    private Long userId;
    private boolean enabled;
    private boolean verified;
    private String mfaType;
    private String secretKeyEncrypted;
    private String deviceName;
    private boolean pushEnabled;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

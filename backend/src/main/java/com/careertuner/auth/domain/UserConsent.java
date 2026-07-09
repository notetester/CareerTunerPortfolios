package com.careertuner.auth.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** user_consent table row for signup/settings consent history. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsent {

    private Long id;
    private Long userId;
    private String consentType;
    private boolean agreed;
    private LocalDateTime agreedAt;
    private LocalDateTime revokedAt;
    private String source;
    private LocalDateTime createdAt;
}

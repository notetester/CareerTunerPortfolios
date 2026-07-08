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
public class MfaBackupCode {
    private Long id;
    private Long userId;
    private String codeHash;
    private boolean used;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}

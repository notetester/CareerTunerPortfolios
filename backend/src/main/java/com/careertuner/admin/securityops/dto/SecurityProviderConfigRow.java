package com.careertuner.admin.securityops.dto;

import java.time.LocalDateTime;

public record SecurityProviderConfigRow(
        Long id,
        String providerCode,
        String displayName,
        String providerType,
        String mode,
        boolean enabled,
        String endpointUrl,
        String configJson,
        String healthStatus,
        LocalDateTime lastCheckedAt,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}

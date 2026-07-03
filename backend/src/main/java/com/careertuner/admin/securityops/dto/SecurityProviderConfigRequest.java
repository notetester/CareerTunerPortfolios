package com.careertuner.admin.securityops.dto;

import jakarta.validation.constraints.NotBlank;

public record SecurityProviderConfigRequest(
        @NotBlank String displayName,
        @NotBlank String providerType,
        String mode,
        Boolean enabled,
        String endpointUrl,
        String configJson) {
}

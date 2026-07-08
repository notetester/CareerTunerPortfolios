package com.careertuner.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaSetupVerifyRequest(
        @NotBlank String code
) {
}

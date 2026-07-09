package com.careertuner.auth.dto;

import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @Size(max = 255) String email,
        @Size(max = 255) String identifier) {

    public String loginIdentifier() {
        return identifier != null && !identifier.isBlank() ? identifier : email;
    }
}

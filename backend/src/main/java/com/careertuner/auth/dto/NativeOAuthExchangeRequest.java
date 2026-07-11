package com.careertuner.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NativeOAuthExchangeRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "유효한 handoffCode가 필요합니다.")
        String handoffCode,
        @NotBlank
        @Size(min = 43, max = 128, message = "handoffVerifier는 43~128자여야 합니다.")
        @Pattern(regexp = "^[A-Za-z0-9._~-]+$", message = "handoffVerifier 형식이 올바르지 않습니다.")
        String handoffVerifier) {
}

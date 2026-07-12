package com.careertuner.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** SHA-256(code_verifier)를 패딩 없이 base64url 인코딩한 PKCE challenge. */
public record NativeOAuthStartRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "유효한 PKCE handoffChallenge가 필요합니다.")
        String handoffChallenge) {
}

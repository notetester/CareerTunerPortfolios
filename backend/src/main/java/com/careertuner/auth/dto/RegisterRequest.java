package com.careertuner.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 4, max = 50)
        @Pattern(regexp = "^[a-z0-9_]+$", message = "아이디는 영문 소문자, 숫자, 밑줄만 사용할 수 있습니다.")
        String loginId,
        @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 100) String name,
        Boolean termsAgreed,
        Boolean privacyAgreed,
        Boolean aiDataAgreed,
        Boolean marketingAgreed) {
}

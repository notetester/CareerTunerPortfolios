package com.careertuner.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^[a-z0-9_]{4,50}$",
                message = "아이디는 영문 소문자, 숫자, 밑줄 4~50자로 입력해 주세요.") String loginId,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 100) String name,
        Boolean termsAgreed,
        Boolean privacyAgreed,
        Boolean aiDataAgreed,
        Boolean marketingAgreed) {
}

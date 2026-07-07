package com.careertuner.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 소셜/임시 이메일 계정의 실제 로그인 이메일 등록 요청. */
public record EmailRegistrationRequest(
        @NotBlank
        @Email
        String email) {
}

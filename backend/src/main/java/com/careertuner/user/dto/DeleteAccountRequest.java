package com.careertuner.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 본인 계정 소프트 탈퇴 재확인. 로컬 로그인 계정은 현재 비밀번호도 검증한다. */
public record DeleteAccountRequest(
        String password,
        @NotBlank String confirmation) {
}

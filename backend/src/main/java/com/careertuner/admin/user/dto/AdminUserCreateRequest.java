package com.careertuner.admin.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 콘솔의 일반 회원 생성 요청. 관리자 역할 승격은 권한 거버넌스 API에서만 처리한다. */
public record AdminUserCreateRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 100) String name) {
}

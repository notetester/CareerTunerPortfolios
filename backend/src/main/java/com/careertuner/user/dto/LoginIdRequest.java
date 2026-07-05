package com.careertuner.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 로그인 아이디 설정 요청(설정 후 변경 불가 정책). */
public record LoginIdRequest(
        @NotBlank
        @Size(min = 4, max = 50)
        @Pattern(regexp = "^[a-z0-9_]+$", message = "아이디는 영문 소문자, 숫자, 밑줄만 사용할 수 있습니다.")
        String loginId) {
}

package com.careertuner.admin.credit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreditAdjustRequest(
        @NotNull(message = "회원 ID는 필수입니다.")
        Long userId,

        @NotNull(message = "조정 크레딧은 필수입니다.")
        Integer amount,

        @NotBlank(message = "조정 사유는 필수입니다.")
        @Size(max = 255, message = "조정 사유는 255자 이하여야 합니다.")
        String reason
) {
}

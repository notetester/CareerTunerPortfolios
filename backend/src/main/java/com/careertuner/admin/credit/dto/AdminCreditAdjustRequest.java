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
        String reason,

        @Size(max = 120, message = "요청 ID는 120자 이하여야 합니다.")
        String requestId
) {
    /** 기존 호출부와의 호환을 유지하되, 새 클라이언트는 requestId를 전달한다. */
    public AdminCreditAdjustRequest(Long userId, Integer amount, String reason) {
        this(userId, amount, reason, null);
    }
}

package com.careertuner.admin.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCareerRunMemoRequest(
        @NotBlank(message = "메모 유형은 필수입니다.")
        @Size(max = 30, message = "메모 유형은 30자 이하여야 합니다.")
        String memoType,

        @NotBlank(message = "메모 내용은 필수입니다.")
        @Size(max = 5000, message = "메모 내용은 5000자 이하여야 합니다.")
        String content
) {
}

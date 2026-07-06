package com.careertuner.admin.correction.dto;

import jakarta.validation.constraints.Size;

public record AdminCorrectionMemoRequest(
        @Size(max = 2000, message = "운영 메모는 2000자 이하여야 합니다.")
        String memo
) {
}

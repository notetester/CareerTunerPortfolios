package com.careertuner.admin.common.dto;

import jakarta.validation.constraints.Size;

public record AdminMemoRequest(
        @Size(max = 2000) String adminMemo
) {
}

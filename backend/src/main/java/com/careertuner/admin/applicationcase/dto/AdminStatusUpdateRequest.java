package com.careertuner.admin.applicationcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminStatusUpdateRequest(
        @NotBlank @Size(max = 20) String status,
        @Size(max = 1000) String memo
) {
}

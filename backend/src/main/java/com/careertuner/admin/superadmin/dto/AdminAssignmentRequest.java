package com.careertuner.admin.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAssignmentRequest(
        @NotBlank @Size(max = 80) String code,
        @Size(max = 500) String reason) {
}

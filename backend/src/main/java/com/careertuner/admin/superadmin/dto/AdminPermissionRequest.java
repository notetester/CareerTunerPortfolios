package com.careertuner.admin.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPermissionRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 500) String description) {
}

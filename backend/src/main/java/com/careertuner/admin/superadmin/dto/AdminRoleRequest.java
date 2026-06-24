package com.careertuner.admin.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRoleRequest(
        @NotBlank String role,
        @Size(max = 500) String reason) {
}

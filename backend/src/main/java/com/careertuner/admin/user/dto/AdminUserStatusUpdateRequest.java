package com.careertuner.admin.user.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserStatusUpdateRequest(
        @NotBlank String status,
        @Size(max = 255) String reason,
        @Size(max = 2000) String memo,
        LocalDateTime blockedUntil) {
}

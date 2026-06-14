package com.careertuner.community.dto;

import com.careertuner.community.domain.TargetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull TargetType targetType,
        @NotNull Long targetId,
        @NotBlank String reason,
        @Size(max = 500) String detail
) {}

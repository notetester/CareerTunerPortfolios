package com.careertuner.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank String token) {
}

package com.careertuner.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record FindIdRequest(@NotBlank @Email String email) {
}

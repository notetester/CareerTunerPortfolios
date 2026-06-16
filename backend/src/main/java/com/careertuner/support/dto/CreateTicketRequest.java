package com.careertuner.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank String category,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String content
) {}

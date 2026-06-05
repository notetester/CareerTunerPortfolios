package com.careertuner.applicationcase.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApplicationCaseRequest(
        @NotBlank @Size(max = 255) String companyName,
        @NotBlank @Size(max = 255) String jobTitle,
        LocalDate postingDate,
        @Size(max = 20) String sourceType,
        @Size(max = 20) String status,
        Boolean favorite
) {
}

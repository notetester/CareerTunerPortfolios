package com.careertuner.applicationcase.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

public record UpdateApplicationCaseRequest(
        @Size(max = 255) String companyName,
        @Size(max = 255) String jobTitle,
        LocalDate postingDate,
        @Size(max = 20) String sourceType,
        @Size(max = 20) String status,
        Boolean favorite
) {
}

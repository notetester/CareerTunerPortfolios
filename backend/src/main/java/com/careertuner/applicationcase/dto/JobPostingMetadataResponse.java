package com.careertuner.applicationcase.dto;

import java.time.LocalDate;

public record JobPostingMetadataResponse(
        String companyName,
        String jobTitle,
        LocalDate postingDate,
        LocalDate deadlineDate
) {
}

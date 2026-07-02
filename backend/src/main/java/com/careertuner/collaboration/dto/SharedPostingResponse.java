package com.careertuner.collaboration.dto;

import java.time.LocalDate;

public record SharedPostingResponse(
        Long applicationCaseId,
        String companyName,
        String jobTitle,
        LocalDate deadlineDate,
        String sourceType
) {
}

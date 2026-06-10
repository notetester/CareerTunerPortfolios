package com.careertuner.jobanalysis.dto;

import jakarta.validation.constraints.Size;

public record JobAnalysisReviewRequest(
        @Size(max = 50) String employmentType,
        @Size(max = 50) String experienceLevel,
        String requiredSkills,
        String preferredSkills,
        String duties,
        String qualifications,
        @Size(max = 20) String difficulty,
        String summary,
        String evidence,
        String ambiguousConditions,
        Boolean confirmed
) {
}

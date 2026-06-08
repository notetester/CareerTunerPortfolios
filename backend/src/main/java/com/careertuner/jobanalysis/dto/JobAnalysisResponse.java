package com.careertuner.jobanalysis.dto;

import java.time.LocalDateTime;

import com.careertuner.jobanalysis.domain.JobAnalysis;

public record JobAnalysisResponse(
        Long id,
        Long applicationCaseId,
        String employmentType,
        String experienceLevel,
        String requiredSkills,
        String preferredSkills,
        String duties,
        String qualifications,
        String difficulty,
        String summary,
        LocalDateTime createdAt
) {
    public static JobAnalysisResponse from(JobAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return new JobAnalysisResponse(
                analysis.getId(),
                analysis.getApplicationCaseId(),
                analysis.getEmploymentType(),
                analysis.getExperienceLevel(),
                analysis.getRequiredSkills(),
                analysis.getPreferredSkills(),
                analysis.getDuties(),
                analysis.getQualifications(),
                analysis.getDifficulty(),
                analysis.getSummary(),
                analysis.getCreatedAt());
    }
}

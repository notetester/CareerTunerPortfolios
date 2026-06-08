package com.careertuner.admin.fitanalysis.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;

public record AdminFitAnalysisListItemResponse(
        Long id,
        Long applicationCaseId,
        String userName,
        String userEmail,
        String companyName,
        String jobTitle,
        String applicationStatus,
        boolean favorite,
        Integer fitScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        LocalDateTime createdAt
) {

    public static AdminFitAnalysisListItemResponse of(AdminFitAnalysisResult result,
                                                      List<String> matchedSkills,
                                                      List<String> missingSkills) {
        return new AdminFitAnalysisListItemResponse(
                result.getId(),
                result.getApplicationCaseId(),
                result.getUserName(),
                result.getUserEmail(),
                result.getCompanyName(),
                result.getJobTitle(),
                result.getApplicationStatus(),
                result.isFavorite(),
                result.getFitScore(),
                matchedSkills,
                missingSkills,
                result.getCreatedAt());
    }
}

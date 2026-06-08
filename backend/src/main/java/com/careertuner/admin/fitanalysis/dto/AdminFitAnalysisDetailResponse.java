package com.careertuner.admin.fitanalysis.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;

public record AdminFitAnalysisDetailResponse(
        Long id,
        Long applicationCaseId,
        Long userId,
        String userName,
        String userEmail,
        String companyName,
        String jobTitle,
        String applicationStatus,
        boolean favorite,
        Integer fitScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> recommendedStudy,
        List<String> recommendedCertificates,
        String strategy,
        LocalDateTime createdAt,
        List<AdminFitAnalysisMemoResponse> memos
) {

    public static AdminFitAnalysisDetailResponse of(AdminFitAnalysisResult result,
                                                    List<String> matchedSkills,
                                                    List<String> missingSkills,
                                                    List<String> recommendedStudy,
                                                    List<String> recommendedCertificates,
                                                    List<AdminFitAnalysisMemoResponse> memos) {
        return new AdminFitAnalysisDetailResponse(
                result.getId(),
                result.getApplicationCaseId(),
                result.getUserId(),
                result.getUserName(),
                result.getUserEmail(),
                result.getCompanyName(),
                result.getJobTitle(),
                result.getApplicationStatus(),
                result.isFavorite(),
                result.getFitScore(),
                matchedSkills,
                missingSkills,
                recommendedStudy,
                recommendedCertificates,
                result.getStrategy(),
                result.getCreatedAt(),
                memos);
    }
}

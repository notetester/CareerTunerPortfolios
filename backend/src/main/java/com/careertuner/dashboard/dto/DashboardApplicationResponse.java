package com.careertuner.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.dashboard.domain.DashboardApplicationSource;

public record DashboardApplicationResponse(
        Long id,
        String companyName,
        String jobTitle,
        LocalDate postingDate,
        String status,
        boolean favorite,
        Integer fitScore,
        int interviewCount,
        Integer latestInterviewScore,
        List<String> tags,
        LocalDateTime updatedAt,
        LocalDateTime analyzedAt
) {

    public static DashboardApplicationResponse of(DashboardApplicationSource source, List<String> tags) {
        return new DashboardApplicationResponse(
                source.getApplicationCaseId(),
                source.getCompanyName(),
                source.getJobTitle(),
                source.getPostingDate(),
                source.getStatus(),
                source.isFavorite(),
                source.getFitScore(),
                source.getInterviewCount(),
                source.getLatestInterviewScore(),
                tags,
                source.getUpdatedAt(),
                source.getAnalyzedAt());
    }
}

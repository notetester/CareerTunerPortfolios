package com.careertuner.applicationcase.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.careertuner.applicationcase.domain.ApplicationCase;

public record ApplicationCaseResponse(
        Long id,
        String companyName,
        String jobTitle,
        LocalDate postingDate,
        LocalDate deadlineDate,
        String sourceType,
        String status,
        boolean favorite,
        boolean archived,
        LocalDateTime archivedAt,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ApplicationCaseResponse from(ApplicationCase applicationCase) {
        return new ApplicationCaseResponse(
                applicationCase.getId(),
                applicationCase.getCompanyName(),
                applicationCase.getJobTitle(),
                applicationCase.getPostingDate(),
                applicationCase.getDeadlineDate(),
                applicationCase.getSourceType(),
                applicationCase.getStatus(),
                applicationCase.isFavorite(),
                applicationCase.getArchivedAt() != null,
                applicationCase.getArchivedAt(),
                applicationCase.getDeletedAt(),
                applicationCase.getCreatedAt(),
                applicationCase.getUpdatedAt());
    }
}

package com.careertuner.companyjobposting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.companyjobposting.domain.CompanyJobPosting;

/** 기업 공고 응답(기업 관리/공개 상세 공용). 기존 jobposting.dto.JobPostingResponse 와 별개 도메인. */
public record CompanyJobPostingResponse(
        Long id,
        Long companyUserId,
        String companyName,
        String trustGrade,
        String title,
        String jobRole,
        String employmentType,
        String careerLevel,
        Integer careerYearsMin,
        Integer careerYearsMax,
        String educationLevel,
        String salaryText,
        Boolean salaryNegotiable,
        String workLocation,
        String workHours,
        LocalDate deadlineDate,
        Boolean alwaysOpen,
        String mainTasks,
        String requirements,
        String preferred,
        String benefits,
        String hiringProcess,
        String headcount,
        List<String> tags,
        String status,
        String rejectReason,
        Integer viewCount,
        Boolean hasPendingRevision,
        LocalDateTime publishedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CompanyJobPostingResponse from(CompanyJobPosting posting, List<String> tags) {
        return new CompanyJobPostingResponse(
                posting.getId(),
                posting.getCompanyUserId(),
                posting.getCompanyName(),
                posting.getTrustGrade(),
                posting.getTitle(),
                posting.getJobRole(),
                posting.getEmploymentType(),
                posting.getCareerLevel(),
                posting.getCareerYearsMin(),
                posting.getCareerYearsMax(),
                posting.getEducationLevel(),
                posting.getSalaryText(),
                posting.getSalaryNegotiable(),
                posting.getWorkLocation(),
                posting.getWorkHours(),
                posting.getDeadlineDate(),
                posting.getAlwaysOpen(),
                posting.getMainTasks(),
                posting.getRequirements(),
                posting.getPreferred(),
                posting.getBenefits(),
                posting.getHiringProcess(),
                posting.getHeadcount(),
                tags,
                posting.getStatus(),
                posting.getRejectReason(),
                posting.getViewCount(),
                posting.getHasPendingRevision(),
                posting.getPublishedAt(),
                posting.getClosedAt(),
                posting.getCreatedAt(),
                posting.getUpdatedAt());
    }
}

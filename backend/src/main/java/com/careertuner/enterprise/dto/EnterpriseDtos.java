package com.careertuner.enterprise.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class EnterpriseDtos {

    private EnterpriseDtos() {
    }

    public record ApplicationRequest(
            String companyName,
            String businessNumber,
            String representativeName,
            String contactName,
            String contactEmail,
            String contactPhone,
            String websiteUrl,
            String industry,
            String employeeCount,
            String evidenceFileUrl,
            Boolean createRequiresReview,
            Boolean editRequiresReview) {
    }

    public record ApplicationReviewRequest(
            String status,
            String reviewMemo,
            Boolean trusted,
            Boolean createRequiresReview,
            Boolean editRequiresReview,
            Integer maxActivePosts) {
    }

    public record ApplicationResponse(
            Long id,
            Long userId,
            String userEmail,
            String userName,
            String companyName,
            String businessNumber,
            String representativeName,
            String contactName,
            String contactEmail,
            String contactPhone,
            String websiteUrl,
            String industry,
            String employeeCount,
            String evidenceFileUrl,
            String status,
            String reviewMemo,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record JobPolicyResponse(
            boolean trusted,
            boolean createRequiresReview,
            boolean editRequiresReview,
            int maxActivePosts) {
    }

    public record StatusResponse(
            boolean employer,
            ApplicationResponse latestApplication,
            JobPolicyResponse policy) {
    }

    public record JobRequest(
            String companyName,
            String title,
            String positionTitle,
            String jobCategory,
            List<String> specialties,
            String duties,
            String qualifications,
            String preferred,
            String benefits,
            String employmentType,
            String experienceLevel,
            String educationLevel,
            String salaryType,
            Integer salaryMin,
            Integer salaryMax,
            String salaryText,
            String workLocation,
            String workSchedule,
            String headcount,
            LocalDateTime applicationStartAt,
            LocalDateTime applicationEndAt,
            String applyUrl,
            String contactEmail,
            String contactPhone,
            String visibility) {
    }

    public record JobReviewRequest(
            String action,
            String reviewMemo) {
    }

    public record JobResponse(
            Long id,
            Long companyUserId,
            String ownerEmail,
            String ownerName,
            String companyName,
            String title,
            String positionTitle,
            String jobCategory,
            List<String> specialties,
            String duties,
            String qualifications,
            String preferred,
            String benefits,
            String employmentType,
            String experienceLevel,
            String educationLevel,
            String salaryType,
            Integer salaryMin,
            Integer salaryMax,
            String salaryText,
            String workLocation,
            String workSchedule,
            String headcount,
            LocalDateTime applicationStartAt,
            LocalDateTime applicationEndAt,
            String applyUrl,
            String contactEmail,
            String contactPhone,
            String visibility,
            String status,
            String reviewStatus,
            String reviewMemo,
            boolean pendingRevision,
            Long communityPostId,
            LocalDateTime approvedAt,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}

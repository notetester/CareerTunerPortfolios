package com.careertuner.company.dto;

import java.time.LocalDateTime;

import com.careertuner.company.domain.CompanyApplication;

/** 기업 신청 응답(내 신청 상태 카드 + 관리자 목록 공용). */
public record CompanyApplicationResponse(
        Long id,
        Long userId,
        String companyName,
        String businessNumber,
        String contact,
        String description,
        String status,
        String rejectReason,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        String applicantEmail,
        String applicantName
) {

    public static CompanyApplicationResponse from(CompanyApplication application) {
        return new CompanyApplicationResponse(
                application.getId(),
                application.getUserId(),
                application.getCompanyName(),
                application.getBusinessNumber(),
                application.getContact(),
                application.getDescription(),
                application.getStatus(),
                application.getRejectReason(),
                application.getReviewedAt(),
                application.getCreatedAt(),
                application.getApplicantEmail(),
                application.getApplicantName());
    }
}

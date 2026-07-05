package com.careertuner.company.dto;

import com.careertuner.company.domain.CompanyProfile;

/** 기업 프로필 응답. */
public record CompanyProfileResponse(
        Long userId,
        String companyName,
        String businessNumber,
        String trustGrade
) {

    public static CompanyProfileResponse from(CompanyProfile profile) {
        return new CompanyProfileResponse(
                profile.getUserId(),
                profile.getCompanyName(),
                profile.getBusinessNumber(),
                profile.getTrustGrade());
    }
}

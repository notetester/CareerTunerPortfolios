package com.careertuner.profile.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        Long userId,
        String loginId,
        String phoneNumber,
        boolean phoneVerified,
        String desiredJob,
        String desiredIndustry,
        Object education,
        Object career,
        Object projects,
        Object skills,
        Object certificates,
        Object languages,
        Object portfolioLinks,
        Object jobPreferences,
        Object personalInfo,
        Object activities,
        Object accountLinks,
        Object chatProfiles,
        String resumeText,
        String selfIntro,
        Object preferences,
        LocalDateTime updatedAt
) {
}

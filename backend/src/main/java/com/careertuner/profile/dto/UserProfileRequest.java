package com.careertuner.profile.dto;

public record UserProfileRequest(
        String loginId,
        String phoneNumber,
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
        Object preferences
) {
}

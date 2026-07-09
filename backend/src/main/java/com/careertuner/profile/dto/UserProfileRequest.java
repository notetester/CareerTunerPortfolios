package com.careertuner.profile.dto;

public record UserProfileRequest(
        String desiredJob,
        String desiredIndustry,
        Object education,
        Object career,
        Object projects,
        Object skills,
        Object certificates,
        Object languages,
        Object portfolioLinks,
        String resumeText,
        String selfIntro,
        Object preferences
) {
}

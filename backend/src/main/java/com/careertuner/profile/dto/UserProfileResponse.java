package com.careertuner.profile.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        Long userId,
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
        Object preferences,
        Integer versionNo,
        LocalDateTime updatedAt
) {
}

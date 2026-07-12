package com.careertuner.profile.dto;

import java.time.LocalDateTime;

/** 사용자가 직접 확인할 수 있는 프로필 입력 스냅샷. */
public record UserProfileVersionResponse(
        Long id,
        Long userId,
        Integer versionNo,
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
        String source,
        LocalDateTime createdAt) {
}

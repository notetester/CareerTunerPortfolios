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
        Object preferences,
        /**
         * 클라이언트가 편집을 시작할 때 읽은 user_profile.version_no.
         * 기존 프로필 저장에는 필수이며, 아직 입력이 하나도 없는 초기 프로필만 null을 허용한다.
         */
        Integer baseVersionNo
) {
}

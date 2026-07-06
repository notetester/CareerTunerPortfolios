package com.careertuner.user.dto;

import java.time.LocalDateTime;

/** 이력서 상세 스펙 응답(JSON 필드는 파싱된 객체로 반환). */
public record UserResumeDetailResponse(
        Long userId,
        Object education,
        Object career,
        Object certificates,
        Object languages,
        Object awards,
        Object activities,
        Object skills,
        Object portfolios,
        Object desiredCondition,
        LocalDateTime updatedAt) {
}

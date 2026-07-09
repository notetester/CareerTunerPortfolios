package com.careertuner.community.dto;

import java.time.LocalDateTime;

/**
 * 게시글 반응자 목록 항목.
 * 익명 리액션은 타인 시점 목록에서 제외되고(쿼리 단계), 본인 것만 anonymous=true 로 내려온다.
 */
public record PostReactorResponse(
        String reactionType,
        Long userId,
        String name,
        boolean anonymous,
        boolean mine,
        LocalDateTime createdAt
) {}

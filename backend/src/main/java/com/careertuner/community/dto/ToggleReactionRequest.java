package com.careertuner.community.dto;

import com.careertuner.community.domain.ReactionType;
import com.careertuner.community.domain.TargetType;

import jakarta.validation.constraints.NotNull;

public record ToggleReactionRequest(
        @NotNull TargetType targetType,
        @NotNull Long targetId,
        @NotNull ReactionType reactionType,
        /** 익명 리액션 여부(기본 false). 익명이면 알림도 _ANON 타입으로 발행된다. */
        Boolean anonymous
) {
    public boolean isAnonymous() {
        return Boolean.TRUE.equals(anonymous);
    }
}

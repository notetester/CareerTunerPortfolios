package com.careertuner.community.dto;

import com.careertuner.community.domain.ReactionType;
import com.careertuner.community.domain.TargetType;

import jakarta.validation.constraints.NotNull;

public record ToggleReactionRequest(
        @NotNull TargetType targetType,
        @NotNull Long targetId,
        @NotNull ReactionType reactionType
) {}

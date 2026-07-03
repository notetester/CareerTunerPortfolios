package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationMemberResponse(
        Long userId,
        String name,
        String email,
        String role,
        boolean muted,
        String displayName,
        String avatarUrl,
        boolean anonymous,
        List<String> permissions,
        LocalDateTime joinedAt) {
}

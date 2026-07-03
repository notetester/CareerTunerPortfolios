package com.careertuner.collaboration.dto;

import java.util.Map;

import jakarta.validation.constraints.Size;

public record ConversationSettingsRequest(
        @Size(max = 120) String title,
        @Size(max = 500) String description,
        @Size(max = 512) String profileImageUrl,
        @Size(max = 120) String password,
        Boolean clearPassword,
        Integer maxMembers,
        @Size(max = 30) String joinPolicy,
        @Size(max = 30) String invitePolicy,
        Boolean anonymousAllowed,
        Boolean anonymousOnly,
        Boolean roomProfileRequired,
        Map<String, Object> settings) {
}

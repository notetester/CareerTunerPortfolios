package com.careertuner.collaboration.dto;

import java.util.Map;

public record ConversationSettingsResponse(
        Long id,
        String type,
        String title,
        String description,
        String profileImageUrl,
        boolean locked,
        int maxMembers,
        String joinPolicy,
        String invitePolicy,
        boolean anonymousAllowed,
        boolean anonymousOnly,
        boolean roomProfileRequired,
        Map<String, Object> settings) {
}

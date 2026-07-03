package com.careertuner.collaboration.dto;

import java.util.List;

public record ConversationMemberUpdateRequest(
        String role,
        List<String> permissions,
        String displayName,
        String avatarUrl,
        Boolean anonymous) {
}

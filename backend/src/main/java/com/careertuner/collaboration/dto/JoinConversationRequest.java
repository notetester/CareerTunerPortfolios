package com.careertuner.collaboration.dto;

import jakarta.validation.constraints.Size;

public record JoinConversationRequest(
        @Size(max = 120) String password,
        Boolean anonymous,
        Long chatProfileId,
        @Size(max = 80) String displayName,
        @Size(max = 512) String avatarUrl
) {
}

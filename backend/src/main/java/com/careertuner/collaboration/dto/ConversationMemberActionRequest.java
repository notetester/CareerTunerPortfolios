package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

public record ConversationMemberActionRequest(
        String reason,
        Boolean ban,
        LocalDateTime bannedUntil) {
}

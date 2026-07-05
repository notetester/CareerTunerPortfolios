package com.careertuner.collaboration.dto;

import jakarta.validation.constraints.Size;

public record JoinConversationRequest(
        @Size(max = 120) String password
) {
}

package com.careertuner.collaboration.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 20) String type,
        @Size(max = 120) String title,
        @Size(max = 500) String description,
        @Size(max = 120) String password,
        List<Long> memberUserIds
) {
}

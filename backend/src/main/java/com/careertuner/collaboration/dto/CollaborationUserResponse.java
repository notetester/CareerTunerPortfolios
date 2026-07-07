package com.careertuner.collaboration.dto;

public record CollaborationUserResponse(
        Long id,
        String name,
        String email,
        String relationStatus
) {
}

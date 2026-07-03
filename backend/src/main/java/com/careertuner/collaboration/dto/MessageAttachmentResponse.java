package com.careertuner.collaboration.dto;

public record MessageAttachmentResponse(
        Long fileId,
        String originalName,
        String contentType,
        Long sizeBytes,
        String shareMode,
        String availability,
        java.time.LocalDateTime expiresAt,
        String downloadUrl
) {
}

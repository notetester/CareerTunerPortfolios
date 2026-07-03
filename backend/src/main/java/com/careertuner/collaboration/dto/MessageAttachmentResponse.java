package com.careertuner.collaboration.dto;

public record MessageAttachmentResponse(
        Long fileId,
        String originalName,
        String contentType,
        Long sizeBytes,
        String shareMode,
        String availability,
        java.time.LocalDateTime expiresAt,
        String downloadUrl,
        /** LOCAL 공유일 때만 세팅 — 파일 소유자의 데스크톱이 온라인(heartbeat 90초 이내)인지. */
        Boolean ownerDesktopOnline
) {
}

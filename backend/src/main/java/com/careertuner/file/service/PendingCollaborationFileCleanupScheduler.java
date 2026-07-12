package com.careertuner.file.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 24시간 넘게 메시지에 연결되지 않은 메신저 첨부를 시간당 최대 100건 회수한다. */
@Component
@RequiredArgsConstructor
public class PendingCollaborationFileCleanupScheduler {

    static final int TTL_HOURS = 24;
    static final int BATCH_LIMIT = 100;

    private final FileService fileService;

    @Scheduled(
            initialDelayString = "${careertuner.file.pending-collaboration-cleanup.initial-delay-ms:300000}",
            fixedDelayString = "${careertuner.file.pending-collaboration-cleanup.fixed-delay-ms:3600000}")
    public void cleanup() {
        fileService.cleanupStalePendingCollaborationAttachments(TTL_HOURS, BATCH_LIMIT);
    }
}

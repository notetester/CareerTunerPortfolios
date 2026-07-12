package com.careertuner.file.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 24시간 넘게 소비되지 않은 AutoPrep 입력 첨부를 시간당 최대 100건 회수한다. */
@Component
@RequiredArgsConstructor
public class PendingAutoPrepFileCleanupScheduler {

    static final int TTL_HOURS = 24;
    static final int BATCH_LIMIT = 100;

    private final FileService fileService;

    @Scheduled(
            initialDelayString = "${careertuner.file.pending-auto-prep-cleanup.initial-delay-ms:330000}",
            fixedDelayString = "${careertuner.file.pending-auto-prep-cleanup.fixed-delay-ms:3600000}")
    public void cleanup() {
        fileService.cleanupStalePendingAutoPrepAttachments(TTL_HOURS, BATCH_LIMIT);
    }
}

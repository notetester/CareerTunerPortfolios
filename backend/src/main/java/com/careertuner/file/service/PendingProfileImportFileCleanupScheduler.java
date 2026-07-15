package com.careertuner.file.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 24시간 넘게 즉시 삭제되지 않은 프로필 import 원본을 시간당 최대 100건 회수한다. */
@Component
@RequiredArgsConstructor
public class PendingProfileImportFileCleanupScheduler {

    static final int TTL_HOURS = 24;
    static final int BATCH_LIMIT = 100;

    private final FileService fileService;

    @Scheduled(
            initialDelayString = "${careertuner.file.pending-profile-import-cleanup.initial-delay-ms:390000}",
            fixedDelayString = "${careertuner.file.pending-profile-import-cleanup.fixed-delay-ms:3600000}")
    public void cleanup() {
        fileService.cleanupStalePendingProfileImports(TTL_HOURS, BATCH_LIMIT);
    }
}

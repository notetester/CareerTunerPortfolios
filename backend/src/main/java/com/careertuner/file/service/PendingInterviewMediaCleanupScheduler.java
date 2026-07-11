package com.careertuner.file.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 24시간 넘은 미연결 원본과 삭제된 답변을 가리키는 고아 원본을 시간당 각각 최대 100건 회수한다. */
@Component
@RequiredArgsConstructor
public class PendingInterviewMediaCleanupScheduler {

    static final int TTL_HOURS = 24;
    static final int BATCH_LIMIT = 100;

    private final FileService fileService;

    @Scheduled(
            initialDelayString = "${careertuner.file.pending-interview-cleanup.initial-delay-ms:360000}",
            fixedDelayString = "${careertuner.file.pending-interview-cleanup.fixed-delay-ms:3600000}")
    public void cleanup() {
        fileService.cleanupStalePendingInterviewMedia(TTL_HOURS, BATCH_LIMIT);
        fileService.cleanupStaleOrphanedInterviewMedia(TTL_HOURS, BATCH_LIMIT);
    }
}

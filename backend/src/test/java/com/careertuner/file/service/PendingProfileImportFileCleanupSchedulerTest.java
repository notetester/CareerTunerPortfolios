package com.careertuner.file.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class PendingProfileImportFileCleanupSchedulerTest {

    @Test
    void schedulerUsesTwentyFourHourTtlAndBoundedBatch() {
        FileService fileService = mock(FileService.class);
        PendingProfileImportFileCleanupScheduler scheduler =
                new PendingProfileImportFileCleanupScheduler(fileService);

        scheduler.cleanup();

        verify(fileService).cleanupStalePendingProfileImports(24, 100);
    }
}
